package org.example.mapper;

import lombok.RequiredArgsConstructor;
import org.example.entity.Role;
import org.example.entity.UserRoleEntity;
import org.example.model.AssignmentReference;
import org.example.model.UserRole;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hand-written: the role enum exists twice (persistence vs. generated API model) and the scope
 * is one of two reference types, which reads clearer than a MapStruct configuration.
 */
@Component
@RequiredArgsConstructor
public class UserRoleMapper {

    private final HomeAdministrativeAreaMapper homeAdministrativeAreaMapper;
    private final YardPublicAreaMapper yardPublicAreaMapper;

    public Role toEntity(org.example.model.Role role) {
        return Role.valueOf(role.name());
    }

    public org.example.model.Role toApiModel(Role role) {
        return org.example.model.Role.valueOf(role.name());
    }

    public UserRole toApiModel(UserRoleEntity entity) {
        UserRole role = new UserRole();
        role.setId(entity.getId());
        role.setRole(toApiModel(entity.getRole()));
        role.setAssignment(toAssignment(entity));
        return role;
    }

    public List<UserRole> toApiModels(List<UserRoleEntity> entities) {
        return entities.stream().map(this::toApiModel).toList();
    }

    public AssignmentReference toAssignment(UserRoleEntity entity) {
        if (entity.getHomeAdministrativeArea() != null) {
            return homeAdministrativeAreaMapper.toReference(entity.getHomeAdministrativeArea());
        }
        if (entity.getYardPublicArea() != null) {
            return yardPublicAreaMapper.toReference(entity.getYardPublicArea());
        }
        // ck_user_role_single_scope_required makes this unreachable for persisted rows.
        throw new IllegalStateException("UserRole without scope: " + entity);
    }
}
