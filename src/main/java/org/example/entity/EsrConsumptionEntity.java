package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.DiscriminatorType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.AuditOverride;
import org.hibernate.envers.Audited;
import org.hibernate.envers.RelationTargetAuditMode;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Energy consumption entry of a yard public area in the Energy Supply Register (ESR).
 * Single table; the discriminator tells plain building consumption from heat-only consumption.
 */
@Getter
@Setter
@Entity
@Audited
@AuditOverride(forClass = BaseAuditEntity.class, isAudited = true)
@AuditOverride(forClass = BaseUserAuditEntity.class, isAudited = true)
@Table(name = "esr_consumption")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "discriminator", discriminatorType = DiscriminatorType.STRING, length = 32)
public abstract class EsrConsumptionEntity extends BaseUserAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @Audited(targetAuditMode = RelationTargetAuditMode.NOT_AUDITED)
    @JoinColumn(name = "yard_public_area_id", nullable = false, updatable = false)
    private YardPublicAreaEntity yardPublicArea;

    // TODO master data: becomes a FK to the BWZK category table; ignored until then
    @Column(name = "category_id")
    private @Nullable UUID categoryId;

    // TODO master data: becomes a FK to the energy carrier unit table; ignored until then
    @Column(name = "energy_carrier_unit_id")
    private @Nullable UUID energyCarrierUnitId;

    @Column(name = "quantity", nullable = false, precision = 22, scale = 10)
    private BigDecimal quantity;

    @Column(name = "start_date", nullable = false)
    private LocalDate start;

    @Column(name = "end_date", nullable = false)
    private LocalDate end;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EsrConsumptionEntity that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "%s{id=%s, quantity=%s, start=%s, end=%s}"
                .formatted(getClass().getSimpleName(), id, quantity, start, end);
    }
}
