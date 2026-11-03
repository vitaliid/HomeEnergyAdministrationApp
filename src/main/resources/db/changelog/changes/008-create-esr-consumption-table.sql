--liquibase formatted sql

--changeset vitalii:008-create-esr-consumption-table
--comment: ESR energy consumption entries of a yard public area (single table: BuildingConsumption | HeatBuildingConsumption)
CREATE TABLE esr_consumption
(
    id                     UUID                     NOT NULL DEFAULT gen_random_uuid(),
    yard_public_area_id       UUID                     NOT NULL,
    discriminator          VARCHAR(32)              NOT NULL,
    category_id            UUID,
    energy_carrier_unit_id UUID,
    quantity               NUMERIC(22, 10)          NOT NULL,
    start_date             DATE                     NOT NULL,
    end_date               DATE                     NOT NULL,
    is_weather_adjusted    BOOLEAN,

    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             UUID                     NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by             UUID                     NOT NULL,

    CONSTRAINT pk_esr_consumption PRIMARY KEY (id),
    CONSTRAINT fk_esr_consumption_yard_public_area
        FOREIGN KEY (yard_public_area_id) REFERENCES yard_public_area (id),
    CONSTRAINT fk_esr_consumption_created_by FOREIGN KEY (created_by) REFERENCES app_user (id),
    CONSTRAINT fk_esr_consumption_updated_by FOREIGN KEY (updated_by) REFERENCES app_user (id),

    CONSTRAINT ck_esr_consumption_discriminator
        CHECK (discriminator IN ('BuildingConsumption', 'HeatBuildingConsumption')),
    CONSTRAINT ck_esr_consumption_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_esr_consumption_period CHECK (end_date >= start_date),
    -- the climate-factor answer exists exactly for heat-only consumptions
    CONSTRAINT ck_esr_consumption_weather_adjusted_heat_only
        CHECK ((discriminator = 'HeatBuildingConsumption') = (is_weather_adjusted IS NOT NULL))
);

CREATE INDEX idx_esr_consumption_yard_public_area_id ON esr_consumption (yard_public_area_id);

--rollback DROP TABLE esr_consumption;

--changeset vitalii:008-create-esr-consumption-audit-table
--comment: Envers audit table for esr_consumption (revtype 0 = ADD, 1 = MOD, 2 = DEL). Business columns nullable as Envers requires.
CREATE TABLE esr_consumption_aud
(
    id                     UUID     NOT NULL,
    rev                    INTEGER  NOT NULL,
    revtype                SMALLINT NOT NULL,
    yard_public_area_id       UUID,
    discriminator          VARCHAR(32),
    category_id            UUID,
    energy_carrier_unit_id UUID,
    quantity               NUMERIC(22, 10),
    start_date             DATE,
    end_date               DATE,
    is_weather_adjusted    BOOLEAN,
    created_at             TIMESTAMP WITH TIME ZONE,
    created_by             UUID,
    updated_at             TIMESTAMP WITH TIME ZONE,
    updated_by             UUID,

    CONSTRAINT pk_esr_consumption_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_esr_consumption_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (id)
);

CREATE INDEX idx_esr_consumption_aud_yard_public_area_id ON esr_consumption_aud (yard_public_area_id);

--rollback DROP TABLE esr_consumption_aud;
