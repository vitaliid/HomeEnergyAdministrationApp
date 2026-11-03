package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.HomeAdministrativeAreaReferenceEntity;
import org.example.entity.YardPublicAreaEntity;
import org.example.entity.Role;
import org.example.entity.UserEntity;
import org.example.entity.UserRoleEntity;
import org.example.exception.BusinessException;
import org.example.exception.ErrorCode;
import org.example.repository.HomeAdministrativeAreaRepository;
import org.example.repository.YardPublicAreaRepository;
import org.example.repository.UserRepository;
import org.example.repository.UserRoleRepository;
import org.example.security.CurrentUserProvider;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessScopeService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final HomeAdministrativeAreaRepository homeAdministrativeAreaRepository;
    private final YardPublicAreaRepository yardPublicAreaRepository;
    private final CurrentUserProvider currentUserProvider;

    /**
     * The authenticated subject together with the home administrative areas of all its subtrees,
     * and all yard public areas in them or administered directly.
     */
    public record CallingAdmin(UserEntity user,
                               Set<UUID> homeAdministrativeAreaIds,
                               Set<UUID> yardPublicAreaIds) {

        public UUID id() {
            return user.getId();
        }

        public boolean coversHomeAdministrativeArea(UUID homeAdministrativeAreaId) {
            return homeAdministrativeAreaIds.contains(homeAdministrativeAreaId);
        }

        public boolean coversYardPublicArea(UUID yardPublicAreaId) {
            return yardPublicAreaIds.contains(yardPublicAreaId);
        }

        /**
         * Whether the one unit that is set lies in the scope.
         */
        public boolean covers(@Nullable HomeAdministrativeAreaReferenceEntity homeAdministrativeArea,
                              @Nullable YardPublicAreaEntity yardPublicArea) {
            if (homeAdministrativeArea != null) {
                return coversHomeAdministrativeArea(homeAdministrativeArea.getId());
            }
            return yardPublicArea != null && coversYardPublicArea(yardPublicArea.getId());
        }
    }

    /**
     * The authenticated subject as an Admin. Authenticated in Keycloak without a user row, or
     * with a user row but no ADMIN role, means no authority at all.
     */
    public CallingAdmin requireCallingAdmin() {
        UUID id = currentUserProvider.requireCurrentUserId();

        UserEntity user = userRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("Authenticated subject {} has no user row", id);
                    return new BusinessException(ErrorCode.ACCESS_DENIED);
                });

        List<UserRoleEntity> adminRoles = userRoleRepository.findAllByUserIdAndRole(id, Role.ADMIN);
        if (adminRoles.isEmpty()) {
            log.error("User {} holds no ADMIN role", id);
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        // Ids only: reading the id of a lazy association does not load it.
        Set<UUID> roots = adminRoles.stream()
                .map(UserRoleEntity::getHomeAdministrativeArea)
                .filter(Objects::nonNull)
                .map(HomeAdministrativeAreaReferenceEntity::getId)
                .collect(Collectors.toSet());

        Set<UUID> homeAdministrativeAreaIds = roots.isEmpty()
                ? Set.of()
                : Set.copyOf(homeAdministrativeAreaRepository.findSelfAndDescendantIdsOfAny(roots));

        Set<UUID> yardPublicAreaIds = adminRoles.stream()
                .map(UserRoleEntity::getYardPublicArea)
                .filter(Objects::nonNull)
                .map(YardPublicAreaEntity::getId)
                .collect(Collectors.toCollection(HashSet::new));
        if (!homeAdministrativeAreaIds.isEmpty()) {
            yardPublicAreaIds.addAll(yardPublicAreaRepository.findAllIdsByHomeAdministrativeAreaIdIn(homeAdministrativeAreaIds));
        }

        return new CallingAdmin(user, homeAdministrativeAreaIds, Set.copyOf(yardPublicAreaIds));
    }

    /**
     * The authenticated subject as a user, whatever roles they hold. Authenticated in Keycloak
     * without a user row means no authority at all.
     */
    public UUID requireCurrentUserId() {
        UUID id = currentUserProvider.requireCurrentUserId();
        if (!userRepository.existsById(id)) {
            log.error("Authenticated subject {} has no user row", id);
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return id;
    }

    public void requireRoleOnYardPublicArea(Role role, UUID yardPublicAreaId) {
        requireAnyRoleOnYardPublicArea(Set.of(role), yardPublicAreaId);
    }

    public void requireAnyRoleOnYardPublicArea(Set<Role> roles, UUID yardPublicAreaId) {
        UUID userId = requireCurrentUserId();
        Set<String> dbValues = roles.stream().map(Role::getDbValue).collect(Collectors.toSet());
        if (!yardPublicAreaRepository.hasAnyRoleOnYardPublicArea(yardPublicAreaId, userId, dbValues)) {
            log.error("User {} holds none of {} covering yard public area {}", userId, roles, yardPublicAreaId);
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    public void requireNotSelf(CallingAdmin admin, UserEntity user, ErrorCode errorCode) {
        if (admin.id().equals(user.getId())) {
            log.error("User {} attempted an operation on their own record ({})", admin.id(), errorCode);
            throw new BusinessException(errorCode);
        }
    }

    /**
     * A user is within the Admin's scope when their assignment lies in it, or at least one of
     * their roles does. Both links count, independently of each other.
     */
    public void requireUserInScope(CallingAdmin admin, UserEntity user) {
        boolean inScope = admin.covers(user.getHomeAdministrativeArea(), user.getYardPublicArea())
                || userRoleRepository.findAllByUserId(user.getId()).stream()
                .anyMatch(role -> admin.covers(role.getHomeAdministrativeArea(), role.getYardPublicArea()));

        if (!inScope) {
            log.error("User {} is outside the scope of user {}", user.getId(), admin.id());
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    public List<UserRoleEntity> requireAllRolesInScope(CallingAdmin admin, UserEntity user) {
        List<UserRoleEntity> roles = userRoleRepository.findAllByUserId(user.getId());

        boolean allInScope = roles.stream()
                .allMatch(role -> admin.covers(role.getHomeAdministrativeArea(), role.getYardPublicArea()));

        if (!allInScope) {
            log.error("User {} holds roles outside the scope of user {}", user.getId(), admin.id());
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }

        return roles;
    }

    public void requireHomeAdministrativeAreaInScope(CallingAdmin admin, UUID homeAdministrativeAreaId) {
        if (!admin.coversHomeAdministrativeArea(homeAdministrativeAreaId)) {
            log.error("Home administrative area {} is outside the scope of user {}", homeAdministrativeAreaId, admin.id());
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    public void requireYardPublicAreaInScope(CallingAdmin admin, YardPublicAreaEntity yardPublicArea) {
        if (!admin.coversYardPublicArea(yardPublicArea.getId())) {
            log.error("Yard public area {} is outside the scope of user {}", yardPublicArea.getId(), admin.id());
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }
}
