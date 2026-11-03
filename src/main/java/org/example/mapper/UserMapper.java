package org.example.mapper;

import lombok.RequiredArgsConstructor;
import org.example.entity.UserEntity;
import org.example.model.AssignmentReference;
import org.example.model.User;
import org.example.model.UserRole;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Assembles the API user from the register row (assignment, roles) and the Keycloak account
 * (name, e-mail, enabled). Hand-written: the two sources make MapStruct more noise than help here.
 */
@Component
@RequiredArgsConstructor
public class UserMapper {

    private final HomeAdministrativeAreaMapper homeAdministrativeAreaMapper;
    private final YardPublicAreaMapper yardPublicAreaMapper;

    public User toApiModel(UserEntity entity, String firstName, String lastName, String email, boolean active,
                           List<UserRole> roles) {
        User user = new User();
        user.setId(entity.getId());
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        user.setActive(active);
        user.setAssignment(toAssignment(entity));
        user.setRoles(roles);
        return user;
    }

    public User toApiModel(UserEntity entity, UserRepresentation keycloakUser, List<UserRole> roles) {
        return toApiModel(
                entity,
                keycloakUser.getFirstName(),
                keycloakUser.getLastName(),
                keycloakUser.getEmail(),
                Boolean.TRUE.equals(keycloakUser.isEnabled()),
                roles);
    }

    public AssignmentReference toAssignment(UserEntity entity) {
        if (entity.getHomeAdministrativeArea() != null) {
            return homeAdministrativeAreaMapper.toReference(entity.getHomeAdministrativeArea());
        }
        if (entity.getYardPublicArea() != null) {
            return yardPublicAreaMapper.toReference(entity.getYardPublicArea());
        }
        // ck_app_user_single_assignment_required makes this unreachable for persisted rows.
        throw new IllegalStateException("User without assignment: " + entity);
    }
}
