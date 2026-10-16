--liquibase formatted sql

--changeset vitalii:002-create-home-administrative-area-table
--comment: Home administrative area hierarchy (code e.g. HOME001, HOME002; level_type: 1 = top level, increasing downwards)
CREATE TABLE home_administrative_area
(
    id               UUID                     NOT NULL DEFAULT gen_random_uuid(),
    name             TEXT                     NOT NULL,
    code             VARCHAR(16),
    population_count INTEGER,
    level_type       INTEGER,
    zone             VARCHAR(16)              NOT NULL,
    parent_id        UUID,

    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_home_administrative_area PRIMARY KEY (id),
    CONSTRAINT uq_home_administrative_area_code UNIQUE (code),
    CONSTRAINT fk_home_administrative_area_parent FOREIGN KEY (parent_id) REFERENCES home_administrative_area (id),
    CONSTRAINT fk_home_administrative_area_building_zone FOREIGN KEY (zone) REFERENCES building_zone (id)
);

CREATE INDEX idx_home_administrative_area_parent_id ON home_administrative_area (parent_id);

--rollback DROP TABLE home_administrative_area CASCADE;
