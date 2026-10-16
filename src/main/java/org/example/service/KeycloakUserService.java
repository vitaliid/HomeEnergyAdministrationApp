package org.example.service;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.KeycloakAdminConfig.KeycloakProperties;
import org.example.exception.BusinessException;
import org.example.exception.ErrorCode;
import org.jspecify.annotations.Nullable;
import org.keycloak.admin.client.CreatedResponseUtil;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Identity data of users lives in Keycloak; this service is the only place that talks to its Admin API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakUserService {

    private final RealmResource registryRealm;
    private final KeycloakProperties properties;

    /**
     * Keycloak assigns the user id itself - an id in the representation is ignored by
     * {@code POST /users} - so the caller must use the id returned here.
     */
    public UUID create(String firstName, String lastName, String email) {
        CredentialRepresentation credential = new CredentialRepresentation();
        credential.setType(CredentialRepresentation.PASSWORD);
        credential.setValue(properties.initialPassword());
        credential.setTemporary(true);

        UserRepresentation user = new UserRepresentation();
        user.setUsername(email);
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEnabled(true);
        user.setEmailVerified(false);
        user.setCredentials(List.of(credential));

        try (Response response = registryRealm.users().create(user)) {
            int status = response.getStatus();

            if (status == HttpStatus.CONFLICT.value()) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }

            if (status != HttpStatus.CREATED.value()) {
                log.error("Keycloak user creation returned unexpected status {} for email={}", status, email);
                throw new BusinessException(ErrorCode.KEYCLOAK_UNAVAILABLE);
            }

            // The created id only travels in the Location header: .../users/{id}
            String id = CreatedResponseUtil.getCreatedId(response);
            if (id == null) {
                log.error("Keycloak user creation returned no Location header for email={}", email);
                throw new BusinessException(ErrorCode.KEYCLOAK_UNAVAILABLE);
            }
            return UUID.fromString(id);
        } catch (ProcessingException | WebApplicationException ex) {
            log.error("Keycloak user creation failed for email={}", email, ex);
            throw new BusinessException(ErrorCode.KEYCLOAK_UNAVAILABLE);
        } catch (IllegalArgumentException ex) {
            log.error("Keycloak user creation returned a non-UUID id for email={}", email, ex);
            throw new BusinessException(ErrorCode.KEYCLOAK_UNAVAILABLE);
        }
    }

    public void delete(UUID id) {
        try (Response response = registryRealm.users().delete(id.toString())) {
            if (response.getStatus() != HttpStatus.NO_CONTENT.value()) {
                log.error("Keycloak user deletion returned status {} for id={}", response.getStatus(), id);
            }
        } catch (ProcessingException | WebApplicationException ex) {
            log.error("Keycloak user deletion failed for id={}; account is orphaned", id, ex);
        }
    }

    /**
     * The username is not sent: the realm runs with {@code registrationEmailAsUsername}, so
     * Keycloak keeps it equal to the e-mail by itself. A changed e-mail is marked unverified
     * again, exactly as a freshly created account is.
     */
    public void updateProfile(UUID id, @Nullable String firstName, @Nullable String lastName, @Nullable String email) {
        UserRepresentation update = new UserRepresentation();
        update.setFirstName(firstName);
        update.setLastName(lastName);
        update.setEmail(email);
        if (email != null) {
            update.setEmailVerified(false);
        }

        try {
            registryRealm.users().get(id.toString()).update(update);
        } catch (NotFoundException ex) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        } catch (WebApplicationException ex) {
            if (ex.getResponse() != null && ex.getResponse().getStatus() == HttpStatus.CONFLICT.value()) {
                throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            log.error("Keycloak profile update failed for id={}", id, ex);
            throw new BusinessException(ErrorCode.KEYCLOAK_UNAVAILABLE);
        } catch (ProcessingException ex) {
            log.error("Keycloak profile update failed for id={}", id, ex);
            throw new BusinessException(ErrorCode.KEYCLOAK_UNAVAILABLE);
        }
    }

    public void setEnabled(UUID id, boolean enabled) {
        UserRepresentation update = new UserRepresentation();
        update.setEnabled(enabled);

        try {
            registryRealm.users().get(id.toString()).update(update);
        } catch (NotFoundException ex) {
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND);
        } catch (ProcessingException | WebApplicationException ex) {
            log.error("Keycloak user update failed for id={} enabled={}", id, enabled, ex);
            throw new BusinessException(ErrorCode.KEYCLOAK_UNAVAILABLE);
        }
    }

    public Optional<UserRepresentation> findById(UUID id) {
        try {
            return Optional.of(registryRealm.users().get(id.toString()).toRepresentation());
        } catch (NotFoundException ex) {
            return Optional.empty();
        } catch (ProcessingException | WebApplicationException ex) {
            log.error("Keycloak user lookup failed for id={}", id, ex);
            throw new BusinessException(ErrorCode.KEYCLOAK_UNAVAILABLE);
        }
    }

    public Optional<UserRepresentation> findByEmail(String email) {
        try {
            return registryRealm.users().searchByEmail(email, true).stream().findFirst();
        } catch (ProcessingException | WebApplicationException ex) {
            log.error("Keycloak user lookup failed for email={}", email, ex);
            throw new BusinessException(ErrorCode.KEYCLOAK_UNAVAILABLE);
        }
    }

    public Stream<UserRepresentation> findByIds(Collection<UUID> ids) {
        return ids.stream()
                .map(this::findById)
                .flatMap(Optional::stream);
    }
}
