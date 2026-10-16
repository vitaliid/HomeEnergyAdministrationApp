--liquibase formatted sql

--changeset vitalii:001-create-building-zone-table
--comment: Building zones (floors), reference data every home administrative area and outside public area is tagged with.
--comment: Ids follow the same HOMEnnn code format as home_administrative_area.code.
--comment: Structure only; the rows are reference data, see db/changelog/reference-data/building-zone.csv.
CREATE TABLE building_zone
(
    id   VARCHAR(16) NOT NULL,
    name VARCHAR(32) NOT NULL,

    CONSTRAINT pk_building_zone PRIMARY KEY (id)
);

--rollback DROP TABLE building_zone;
