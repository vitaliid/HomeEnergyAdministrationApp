package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

import java.util.Objects;
import java.util.UUID;

/**
 * Register row of a user. Identity data (name, e-mail, enabled) lives in Keycloak; the row is
 * keyed by the Keycloak user id and carries only the organisational assignment.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "app_user")
public class UserEntity extends BaseAuditEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "home_administrative_area_id")
    private @Nullable HomeAdministrativeAreaReferenceEntity homeAdministrativeArea;

    @ManyToOne
    @JoinColumn(name = "yard_public_area_id")
    private @Nullable YardPublicAreaEntity yardPublicArea;

    /**
     * Not persisted; only tells Spring Data to {@code persist()} rather than {@code merge()}.
     * False by default, so entities materialised by Hibernate - which bypasses constructors
     * and leaves this at its default - are correctly treated as existing rows.
     */
    @Transient
    private boolean fresh;

    /**
     * Constructor for a brand-new user, and the only place {@link #fresh} is set.
     *
     * @param id the Keycloak user id
     */
    public UserEntity(UUID id) {
        this.id = id;
        this.fresh = true;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Id only. Touching the lazy associations here would trigger a load - or a
     * {@code LazyInitializationException} - from whatever logger happened to call this.
     */
    @Override
    public String toString() {
        return "UserEntity{id=%s}".formatted(id);
    }
}
