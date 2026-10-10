package org.example.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.Role;
import org.example.entity.UserEntity;
import org.example.entity.UserRoleEntity;
import org.example.exception.BusinessException;
import org.example.exception.ErrorCode;
import org.example.validation.Din91379TextValidation;
import org.example.validation.Din91379Type;
import org.example.mapper.UserMapper;
import org.example.mapper.UserRoleMapper;
import org.example.model.User;
import org.example.model.UserCreate;
import org.example.model.UserUpdate;
import org.example.repository.UserRepository;
import org.example.repository.UserRoleRepository;
import org.example.service.AccessScopeService.CallingAdmin;
import org.example.service.UserAssignmentService.ScopedUnit;
import org.example.service.UserRoleService.RoleGrant;
import org.example.shared.comparator.UserComparator;
import org.jspecify.annotations.Nullable;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final KeycloakUserService keycloakUserService;
    private final AccessScopeService accessScopeService;
    private final UserAssignmentService assignmentService;
    private final UserRoleService userRoleService;
    private final UserMapper mapper;
    private final Din91379TextValidation textValidation;
    private final UserRoleMapper roleMapper;

    @Transactional
    public User create(UserCreate dto) {
        CallingAdmin admin = accessScopeService.requireCallingAdmin();

        String firstName = textValidation.normalizeAndValidate(trimToNull(dto.getFirstName()), Din91379Type.DATATYPE_C);
        String lastName = textValidation.normalizeAndValidate(trimToNull(dto.getLastName()), Din91379Type.DATATYPE_C);
        String email = lowerCase(trimToNull(dto.getEmail()));

        if (firstName == null || lastName == null || email == null) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_FORMAT);
        }

        // Unlike patch, a new user must be assigned somewhere.
        Assignment assignment = Assignment.from(dto.getHomeAdministrativeAreaId(), dto.getOutsidePublicAreaId())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ASSIGNMENT));

        // Everything that can reject the request runs before the Keycloak account exists.
        ScopedUnit scopedUnit = assignmentService.loadScopedUnit(admin, assignment);
        List<RoleGrant> roleGrants = userRoleService.prepareGrants(admin, dto.getRoles());

        // Keycloak owns the identifier; the register row is keyed by it.
        UUID id = keycloakUserService.create(firstName, lastName, email);

        UserEntity entity = new UserEntity(id);
        scopedUnit.applyTo(entity);

        UserEntity savedEntity;
        List<UserRoleEntity> roles;
        try {
            savedEntity = userRepository.saveAndFlush(entity);
            roles = userRoleService.grant(savedEntity, roleGrants);
        } catch (RuntimeException ex) {
            log.error("Register row for user {} could not be written; removing the Keycloak account", id, ex);
            keycloakUserService.delete(id);
            throw ex;
        }

        return mapper.toApiModel(savedEntity, firstName, lastName, email, true, roleMapper.toApiModels(roles));
    }

    @Transactional
    public User deactivate(UUID id, boolean confirmed) {
        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        CallingAdmin admin = accessScopeService.requireCallingAdmin();

        accessScopeService.requireUserInScope(admin, entity);
        accessScopeService.requireNotSelf(admin, entity, ErrorCode.SELF_DEACTIVATION_NOT_ALLOWED);
        // Deactivation revokes every role, so the Admin must be entitled to all of them, not just some.
        List<UserRoleEntity> roles = accessScopeService.requireAllRolesInScope(admin, entity);

        UserRepresentation keycloakUser = requireKeycloakAccount(id);

        // already deactivated
        if (Boolean.FALSE.equals(keycloakUser.isEnabled())) {
            return mapper.toApiModel(entity, keycloakUser, roleMapper.toApiModels(roles));
        }

        if (!confirmed && isLastActiveAdmin(id)) {
            throw new BusinessException(ErrorCode.LAST_ADMIN_CONFIRMATION_REQUIRED);
        }

        // Roles first: if Keycloak fails, the transaction rolls the revocation back.
        userRoleService.revoke(roles);
        keycloakUserService.setEnabled(id, false);

        return mapper.toApiModel(
                entity,
                keycloakUser.getFirstName(),
                keycloakUser.getLastName(),
                keycloakUser.getEmail(),
                false,
                List.of());
    }

    private boolean isLastActiveAdmin(UUID id) {
        // One Keycloak lookup per colleague, even when they administer several of the units.
        Map<UUID, Boolean> activeById = new HashMap<>();

        for (UserRoleEntity ownRole : userRoleRepository.findAllByUserIdAndRole(id, Role.ADMIN)) {
            boolean noOtherActiveAdmin = otherAdminsOn(ownRole, id).stream()
                    .map(role -> role.getUser().getId())
                    .noneMatch(otherId -> activeById.computeIfAbsent(otherId, this::isActiveInKeycloak));

            if (noOtherActiveAdmin) {
                return true;
            }
        }

        return false;
    }

    private List<UserRoleEntity> otherAdminsOn(UserRoleEntity ownRole, UUID id) {
        if (ownRole.getHomeAdministrativeArea() != null) {
            return userRoleRepository.findAllByRoleAndHomeAdministrativeAreaIdAndUserIdNot(
                    Role.ADMIN, ownRole.getHomeAdministrativeArea().getId(), id);
        }

        return userRoleRepository.findAllByRoleAndOutsidePublicAreaIdAndUserIdNot(
                Role.ADMIN, ownRole.getOutsidePublicArea().getId(), id);
    }

    private boolean isActiveInKeycloak(UUID id) {
        return keycloakUserService.findById(id)
                .map(user -> Boolean.TRUE.equals(user.isEnabled()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        CallingAdmin admin = accessScopeService.requireCallingAdmin();
        accessScopeService.requireUserInScope(admin, entity);

        UserRepresentation keycloakUser = requireKeycloakAccount(id);

        return mapper.toApiModel(
                entity,
                keycloakUser,
                roleMapper.toApiModels(userRoleService.findVisible(admin, id)));
    }

    private UserRepresentation requireKeycloakAccount(UUID id) {
        return keycloakUserService.findById(id)
                .orElseThrow(() -> {
                    log.error("User {} exists in the database but not in Keycloak", id);
                    return new BusinessException(ErrorCode.INTERNAL_DATA_ERROR);
                });
    }

    @Transactional
    public User patch(UUID id, UserUpdate dto) {
        UserEntity entity = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND));

        CallingAdmin admin = accessScopeService.requireCallingAdmin();
        accessScopeService.requireUserInScope(admin, entity);

        if (dto.getFirstName() == null && dto.getLastName() == null && dto.getEmail() == null
                && dto.getHomeAdministrativeAreaId() == null && dto.getOutsidePublicAreaId() == null
                && dto.getRoles() == null) {
            throw new BusinessException(ErrorCode.MISSING_REQUIRED_FIELD);
        }

        String firstName = textValidation.normalizeAndValidate(optionalName(dto.getFirstName()), Din91379Type.DATATYPE_C);
        String lastName = textValidation.normalizeAndValidate(optionalName(dto.getLastName()), Din91379Type.DATATYPE_C);
        String email = lowerCase(optionalName(dto.getEmail()));
        // Absent means "keep the current assignment"; a target equal to the current one is a no-op too.
        Optional<ScopedUnit> reassignment = Assignment
                .from(dto.getHomeAdministrativeAreaId(), dto.getOutsidePublicAreaId())
                .flatMap(target -> assignmentService.loadScopedUnitIfChanged(admin, entity, target));

        if (dto.getRoles() != null) {
            accessScopeService.requireNotSelf(admin, entity, ErrorCode.SELF_ROLE_CHANGE_NOT_ALLOWED);
        }

        // Absent means "keep the roles as they are"; an empty list revokes every role in scope.
        // Resolved and scope-checked up front, so a rejected role never reaches the database.
        Optional<List<RoleGrant>> roleGrants = Optional.ofNullable(dto.getRoles())
                .map(roles -> userRoleService.prepareGrants(admin, roles));

        UserRepresentation keycloakUser = requireKeycloakAccount(id);

        if (reassignment.isPresent()) {
            reassignment.get().applyTo(entity);
            userRepository.saveAndFlush(entity);
        }

        roleGrants.ifPresent(grants -> userRoleService.replaceWithinScope(admin, entity, grants));

        if (firstName != null || lastName != null || email != null) {
            keycloakUserService.updateProfile(id, firstName, lastName, email);
        }

        return mapper.toApiModel(
                entity,
                firstName != null ? firstName : keycloakUser.getFirstName(),
                lastName != null ? lastName : keycloakUser.getLastName(),
                email != null ? email : keycloakUser.getEmail(),
                Boolean.TRUE.equals(keycloakUser.isEnabled()),
                roleMapper.toApiModels(userRoleService.findVisible(admin, id)));
    }

    @Transactional(readOnly = true)
    public List<User> list() {
        CallingAdmin admin = accessScopeService.requireCallingAdmin();

        List<UserEntity> entities = userRepository.findAllWithRoleOrAssignmentOn(
                admin.homeAdministrativeAreaIds(), admin.outsidePublicAreaIds());

        if (entities.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = entities.stream().map(UserEntity::getId).toList();

        Map<UUID, UserRepresentation> keycloakUsers = keycloakUserService.findByIds(ids)
                .collect(Collectors.toMap(user -> UUID.fromString(user.getId()), Function.identity()));

        Map<UUID, List<UserRoleEntity>> visibleRoles = userRoleService.findVisibleByUserIds(admin, ids);

        return entities.stream()
                .map(entity -> toApiModelOrSkip(
                        entity,
                        keycloakUsers.get(entity.getId()),
                        visibleRoles.getOrDefault(entity.getId(), List.of())))
                .flatMap(Optional::stream)
                .sorted(UserComparator.byLastNameThenFirstName())
                .toList();
    }

    private Optional<User> toApiModelOrSkip(UserEntity entity,
                                            @Nullable UserRepresentation keycloakUser,
                                            List<UserRoleEntity> roles) {
        if (keycloakUser == null) {
            log.error("User {} exists in the database but not in Keycloak - omitted from the list",
                    entity.getId());
            return Optional.empty();
        }

        return Optional.of(mapper.toApiModel(entity, keycloakUser, roleMapper.toApiModels(roles)));
    }

    // ---- small string helpers (avoid a commons-lang dependency) ----

    private static @Nullable String optionalName(@Nullable String value) {
        if (value == null) {
            return null;
        }

        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException(ErrorCode.INVALID_FIELD_FORMAT);
        }
        return trimmed;
    }

    private static @Nullable String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static @Nullable String lowerCase(@Nullable String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
