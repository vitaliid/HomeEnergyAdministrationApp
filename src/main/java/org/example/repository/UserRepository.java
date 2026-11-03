package org.example.repository;

import org.example.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {

    /**
     * Users attached to any of the given units - through their assignment or through a role.
     * <p>
     * Matching is by identity: the ids must already contain every unit of every subtree the
     * caller administers, expanded once per request by {@code AccessScopeService}. This query
     * walks no hierarchy.
     */
    @Query("""
            SELECT u
            FROM UserEntity u
                     LEFT JOIN FETCH u.homeAdministrativeArea
                     LEFT JOIN FETCH u.yardPublicArea pe
                     LEFT JOIN FETCH pe.homeAdministrativeArea
            WHERE u.homeAdministrativeArea.id IN :homeAdministrativeAreaIds
               OR pe.id IN :yardPublicAreaIds
               OR EXISTS (SELECT r
                          FROM UserRoleEntity r
                          WHERE r.user = u
                            AND (r.homeAdministrativeArea.id IN :homeAdministrativeAreaIds
                                 OR r.yardPublicArea.id IN :yardPublicAreaIds))
            """)
    List<UserEntity> findAllWithRoleOrAssignmentOn(
            @Param("homeAdministrativeAreaIds") Collection<UUID> homeAdministrativeAreaIds,
            @Param("yardPublicAreaIds") Collection<UUID> yardPublicAreaIds);
}
