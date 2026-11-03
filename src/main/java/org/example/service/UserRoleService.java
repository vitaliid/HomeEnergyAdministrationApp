package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.entity.HomeAdministrativeAreaReferenceEntity;
import org.example.entity.YardPublicAreaEntity;
import org.example.entity.Role;
import org.example.entity.UserEntity;
import org.example.entity.UserRoleEntity;
import org.example.exception.BusinessException;
import org.example.exception.ErrorCode;
import org.example.mapper.UserRoleMapper;
import org.example.model.UserRoleCreate;
import org.example.repository.UserRoleRepository;
import org.example.service.AccessScopeService.CallingAdmin;
import org.example.service.UserAssignmentService.ScopedUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Grants and revokes the roles of a user.
 */
@Service
@RequiredArgsConstructor
public class UserRoleService {

    private final UserAssignmentService assignmentService;
    private final UserRoleRepository repository;
    private final UserRoleMapper mapper;

    /**
     * A role whose unit is loaded and scope-checked.
     */
    public record RoleGrant(Role role, ScopedUnit unit) {

        UserRoleEntity toEntity(UserEntity user) {
            UserRoleEntity entity = new UserRoleEntity();
            entity.setUser(user);
            entity.setRole(role);
            switch (unit) {
                case ScopedUnit.ToHomeAdministrativeArea(HomeAdministrativeAreaReferenceEntity homeAdministrativeArea) ->
                        entity.setHomeAdministrativeArea(homeAdministrativeArea);
                case ScopedUnit.ToYardPublicArea(YardPublicAreaEntity yardPublicArea) ->
                        entity.setYardPublicArea(yardPublicArea);
            }
            return entity;
        }
    }

    public List<RoleGrant> prepareGrants(CallingAdmin admin, @Nullable List<UserRoleCreate> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }

        // Keyed by the ids from the request, so a duplicate never resolves its unit a second time.
        record Key(org.example.model.Role role, Assignment assignment) {
        }

        Map<Key, RoleGrant> grants = new LinkedHashMap<>();
        for (UserRoleCreate role : roles) {
            Assignment assignment = Assignment.from(role.getHomeAdministrativeAreaId(), role.getYardPublicAreaId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ASSIGNMENT));

            grants.computeIfAbsent(new Key(role.getRole(), assignment), key ->
                    new RoleGrant(mapper.toEntity(role.getRole()), assignmentService.loadScopedUnit(admin, assignment)));
        }

        return List.copyOf(grants.values());
    }

    /**
     * Flushes so that a constraint violation surfaces here.
     */
    public List<UserRoleEntity> grant(UserEntity user, List<RoleGrant> grants) {
        if (grants.isEmpty()) {
            return List.of();
        }

        return repository.saveAllAndFlush(grants.stream().map(grant -> grant.toEntity(user)).toList());
    }

    /**
     * Makes the user's roles within the admin's scope equal to the role grants: adds the missing
     * ones, removes the ones not listed, keeps the ones that match. Roles outside the admin's
     * scope are not touched.
     */
    public void replaceWithinScope(CallingAdmin admin, UserEntity user, List<RoleGrant> grants) {
        Map<RoleKey, UserRoleEntity> current = new LinkedHashMap<>();
        for (UserRoleEntity role : findVisible(admin, user.getId())) {
            current.put(keyOf(role), role);
        }

        Map<RoleKey, RoleGrant> desired = new LinkedHashMap<>();
        for (RoleGrant grant : grants) {
            desired.put(keyOf(grant), grant);
        }

        List<UserRoleEntity> revoked = current.entrySet().stream()
                .filter(entry -> !desired.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        List<UserRoleEntity> granted = desired.entrySet().stream()
                .filter(entry -> !current.containsKey(entry.getKey()))
                .map(entry -> entry.getValue().toEntity(user))
                .toList();

        revoke(revoked);

        if (!granted.isEmpty()) {
            repository.saveAllAndFlush(granted);
        }
    }

    public void revoke(List<UserRoleEntity> roles) {
        if (roles.isEmpty()) {
            return;
        }

        repository.deleteAll(roles);
        repository.flush();
    }

    /**
     * Identity of a role for diffing. Two roles with the same key are the same role.
     */
    private record RoleKey(Role role, Assignment assignment) {
    }

    private static RoleKey keyOf(UserRoleEntity entity) {
        if (entity.getHomeAdministrativeArea() != null) {
            return new RoleKey(entity.getRole(),
                    new Assignment.OfHomeAdministrativeArea(entity.getHomeAdministrativeArea().getId()));
        }
        if (entity.getYardPublicArea() != null) {
            return new RoleKey(entity.getRole(),
                    new Assignment.OfYardPublicArea(entity.getYardPublicArea().getId()));
        }
        // ck_user_role_single_scope_required makes this unreachable for persisted rows.
        throw new IllegalStateException("UserRole without scope: " + entity);
    }

    private static RoleKey keyOf(RoleGrant grant) {
        return switch (grant.unit()) {
            case ScopedUnit.ToHomeAdministrativeArea(HomeAdministrativeAreaReferenceEntity unit) ->
                    new RoleKey(grant.role(), new Assignment.OfHomeAdministrativeArea(unit.getId()));
            case ScopedUnit.ToYardPublicArea(YardPublicAreaEntity yardPublicArea) ->
                    new RoleKey(grant.role(), new Assignment.OfYardPublicArea(yardPublicArea.getId()));
        };
    }

    public List<UserRoleEntity> findVisible(CallingAdmin admin, UUID userId) {
        return repository.findAllByUserId(userId).stream()
                .filter(role -> admin.covers(role.getHomeAdministrativeArea(), role.getYardPublicArea()))
                .toList();
    }

    public Map<UUID, List<UserRoleEntity>> findVisibleByUserIds(CallingAdmin admin, Collection<UUID> userIds) {
        return repository.findAllByUserIdIn(userIds).stream()
                .filter(role -> admin.covers(role.getHomeAdministrativeArea(), role.getYardPublicArea()))
                .collect(Collectors.groupingBy(role -> role.getUser().getId()));
    }
}
