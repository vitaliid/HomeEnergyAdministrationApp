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


--changeset vitalii:002-insert-initial-home-administrative-areas
--comment: Initial top-level home administrative areas, one per building zone.
INSERT INTO home_administrative_area (name, code, level_type, zone)
VALUES ('Basement', 'HOME001', 1, 'HOME001'),
       ('Ground floor', 'HOME002', 1, 'HOME002'),
       ('First floor', 'HOME003', 1, 'HOME003'),
       ('Second floor', 'HOME004', 1, 'HOME004'),
       ('Attic', 'HOME005', 1, 'HOME005') ON CONFLICT (code) DO NOTHING;

--rollback DELETE FROM home_administrative_area WHERE code IN ('HOME001', 'HOME002', 'HOME003', 'HOME004', 'HOME005');