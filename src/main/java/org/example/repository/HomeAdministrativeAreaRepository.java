package org.example.repository;

import org.example.entity.HomeAdministrativeAreaEntity;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HomeAdministrativeAreaRepository extends JpaRepository<HomeAdministrativeAreaEntity, UUID> {

    @Override
    @NonNull
    @EntityGraph(attributePaths = {"parent"})
    Optional<HomeAdministrativeAreaEntity> findById(@NonNull UUID id);

    @NonNull
    @EntityGraph(attributePaths = {"parent"})
    List<HomeAdministrativeAreaEntity> findByParentId(@NonNull UUID parentId);

    Optional<HomeAdministrativeAreaEntity> findByCode(String code);

    /**
     * Every unit the user holds any role on, plus everything below each of them, at any depth.
     */
    @Query(value = """
            WITH RECURSIVE scope(id) AS (
                SELECT ur.home_administrative_area_id
                FROM user_role ur
                WHERE ur.user_id = :userId
                  AND ur.home_administrative_area_id IS NOT NULL

                UNION

                SELECT child.id
                FROM home_administrative_area child
                         JOIN scope ON child.parent_id = scope.id
            )
            SELECT au.*
            FROM home_administrative_area au
                     JOIN scope ON scope.id = au.id
            ORDER BY au.name ASC, au.code ASC
            """, nativeQuery = true)
    List<HomeAdministrativeAreaEntity> findAllVisibleToUser(@Param("userId") UUID userId);

    /**
     * The given units plus everything below each of them, at any depth.
     */
    @Query(value = """
            WITH RECURSIVE scope(id) AS (
                SELECT root.id
                FROM home_administrative_area root
                WHERE root.id IN (:rootIds)

                UNION

                SELECT child.id
                FROM home_administrative_area child
                         JOIN scope ON child.parent_id = scope.id
            )
            SELECT id
            FROM scope
            """, nativeQuery = true)
    List<UUID> findSelfAndDescendantIdsOfAny(@Param("rootIds") Collection<UUID> rootIds);

    /**
     * Whether the user holds any role on the given unit or on one of its ancestors.
     */
    @Query(value = """
            WITH RECURSIVE ancestors(id, parent_id) AS (
                SELECT au.id, au.parent_id
                FROM home_administrative_area au
                WHERE au.id = :homeAdministrativeAreaId

                UNION

                SELECT parent.id, parent.parent_id
                FROM home_administrative_area parent
                         JOIN ancestors ON parent.id = ancestors.parent_id
            )
            SELECT EXISTS (
                SELECT 1
                FROM ancestors
                         JOIN user_role ur ON ur.home_administrative_area_id = ancestors.id
                WHERE ur.user_id = :userId
            )
            """, nativeQuery = true)
    boolean isVisibleToUser(@Param("homeAdministrativeAreaId") UUID homeAdministrativeAreaId,
                            @Param("userId") UUID userId);
}
