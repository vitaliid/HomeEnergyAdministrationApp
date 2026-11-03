package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.entity.HomeAdministrativeAreaReferenceEntity;
import org.example.entity.YardPublicAreaEntity;
import org.example.entity.UserEntity;
import org.example.exception.BusinessException;
import org.example.exception.ErrorCode;
import org.example.repository.HomeAdministrativeAreaReferenceRepository;
import org.example.repository.YardPublicAreaRepository;
import org.example.service.AccessScopeService.CallingAdmin;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Assigns a user to an organisational unit - on creation and when moving them later.
 */
@Service
@RequiredArgsConstructor
public class UserAssignmentService {

    private final HomeAdministrativeAreaReferenceRepository homeAdministrativeAreaReferenceRepository;
    private final YardPublicAreaRepository yardPublicAreaRepository;
    private final AccessScopeService accessScopeService;

    /**
     * A unit loaded and checked to lie within the admin's scope. Exactly one kind, so applying it
     * always changes both columns together and {@code ck_app_user_single_assignment_required}
     * holds at every point.
     */
    public sealed interface ScopedUnit {

        record ToHomeAdministrativeArea(HomeAdministrativeAreaReferenceEntity unit) implements ScopedUnit {
        }

        record ToYardPublicArea(YardPublicAreaEntity yardPublicArea) implements ScopedUnit {
        }

        default void applyTo(UserEntity user) {
            switch (this) {
                case ToHomeAdministrativeArea(HomeAdministrativeAreaReferenceEntity unit) -> {
                    user.setHomeAdministrativeArea(unit);
                    user.setYardPublicArea(null);
                }
                case ToYardPublicArea(YardPublicAreaEntity yardPublicArea) -> {
                    user.setHomeAdministrativeArea(null);
                    user.setYardPublicArea(yardPublicArea);
                }
            }
        }
    }

    public ScopedUnit loadScopedUnit(CallingAdmin admin, Assignment target) {
        return switch (target) {
            case Assignment.OfHomeAdministrativeArea(UUID id) -> {
                HomeAdministrativeAreaReferenceEntity unit = homeAdministrativeAreaReferenceRepository.findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARENT));

                accessScopeService.requireHomeAdministrativeAreaInScope(admin, unit.getId());
                yield new ScopedUnit.ToHomeAdministrativeArea(unit);
            }
            case Assignment.OfYardPublicArea(UUID id) -> {
                YardPublicAreaEntity yardPublicArea = yardPublicAreaRepository.findById(id)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PARENT));

                accessScopeService.requireYardPublicAreaInScope(admin, yardPublicArea);
                yield new ScopedUnit.ToYardPublicArea(yardPublicArea);
            }
        };
    }

    /**
     * For patch: empty when the target is the unit the user already holds.
     */
    public Optional<ScopedUnit> loadScopedUnitIfChanged(CallingAdmin admin, UserEntity user, Assignment target) {
        if (isCurrentAssignment(user, target)) {
            return Optional.empty();
        }

        accessScopeService.requireNotSelf(admin, user, ErrorCode.SELF_REASSIGNMENT_NOT_ALLOWED);
        return Optional.of(loadScopedUnit(admin, target));
    }

    private static boolean isCurrentAssignment(UserEntity user, Assignment target) {
        return switch (target) {
            case Assignment.OfHomeAdministrativeArea(UUID id) ->
                    user.getHomeAdministrativeArea() != null && id.equals(user.getHomeAdministrativeArea().getId());
            case Assignment.OfYardPublicArea(UUID id) ->
                    user.getYardPublicArea() != null && id.equals(user.getYardPublicArea().getId());
        };
    }
}
