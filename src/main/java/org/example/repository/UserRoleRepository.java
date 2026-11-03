package org.example.repository;

import org.example.entity.Role;
import org.example.entity.UserRoleEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRoleEntity, UUID> {

    List<UserRoleEntity> findAllByUserIdAndRole(UUID userId, Role role);

    @EntityGraph(attributePaths = {"homeAdministrativeArea", "yardPublicArea"})
    List<UserRoleEntity> findAllByUserId(UUID userId);

    @EntityGraph(attributePaths = {"user", "homeAdministrativeArea", "yardPublicArea"})
    List<UserRoleEntity> findAllByUserIdIn(Collection<UUID> userIds);

    List<UserRoleEntity> findAllByRoleAndHomeAdministrativeAreaIdAndUserIdNot(
            Role role, UUID homeAdministrativeAreaId, UUID userId);

    List<UserRoleEntity> findAllByRoleAndYardPublicAreaIdAndUserIdNot(
            Role role, UUID yardPublicAreaId, UUID userId);
}
