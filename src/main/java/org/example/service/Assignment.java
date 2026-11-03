package org.example.service;

import org.example.exception.BusinessException;
import org.example.exception.ErrorCode;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The organisational unit a caller wants a user (or a role) attached to - exactly one of the two kinds.
 * <p>
 * The API carries this as two optional ids; {@link #from} is the only place that turns them into
 * a value, so "both given" and "none given" are handled once and cannot reach the services.
 */
public sealed interface Assignment {

    record OfHomeAdministrativeArea(UUID id) implements Assignment {
    }

    record OfYardPublicArea(UUID id) implements Assignment {
    }

    /**
     * Empty when neither id is given - the caller decides whether that is allowed. Both given
     * mirrors {@code ck_app_user_single_assignment_required}.
     */
    static Optional<Assignment> from(@Nullable UUID homeAdministrativeAreaId, @Nullable UUID yardPublicAreaId) {
        if (homeAdministrativeAreaId != null && yardPublicAreaId != null) {
            throw new BusinessException(ErrorCode.INVALID_ASSIGNMENT);
        }
        if (homeAdministrativeAreaId != null) {
            return Optional.of(new OfHomeAdministrativeArea(homeAdministrativeAreaId));
        }
        if (yardPublicAreaId != null) {
            return Optional.of(new OfYardPublicArea(yardPublicAreaId));
        }
        return Optional.empty();
    }
}
