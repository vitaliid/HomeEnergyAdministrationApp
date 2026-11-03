package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * A role of a user on exactly one scope: a home administrative area or a yard public area.
 */
@Getter
@Setter
@Entity
@Audited
@AuditOverride(forClass = BaseAuditEntity.class, isAudited = true)
@Table(name = "user_role")
public class UserRoleEntity extends BaseAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private UserEntity user;

    @Convert(converter = RoleConverter.class)
    @Column(name = "role", nullable = false, length = 32, updatable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @JoinColumn(name = "home_administrative_area_id", updatable = false)
    private @Nullable HomeAdministrativeAreaReferenceEntity homeAdministrativeArea;

    @ManyToOne(fetch = FetchType.LAZY)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @JoinColumn(name = "yard_public_area_id", updatable = false)
    private @Nullable YardPublicAreaEntity yardPublicArea;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserRoleEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "UserRoleEntity{id=%s, role=%s}".formatted(id, role);
    }
}
