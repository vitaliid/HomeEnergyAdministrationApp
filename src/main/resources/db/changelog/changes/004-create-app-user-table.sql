--liquibase formatted sql

--changeset vitalii:004-create-app-user-table
--comment: Register row of a user (Keycloak user id + assignment to exactly one home administrative area or yard public area)
CREATE TABLE app_user
(
    id                          UUID                     NOT NULL,
    home_administrative_area_id UUID,
    yard_public_area_id      UUID,

    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT fk_app_user_home_administrative_area
        FOREIGN KEY (home_administrative_area_id) REFERENCES home_administrative_area (id),
    CONSTRAINT fk_app_user_yard_public_area
        FOREIGN KEY (yard_public_area_id) REFERENCES yard_public_area (id),
    CONSTRAINT ck_app_user_single_assignment_required
        CHECK (num_nonnulls(home_administrative_area_id, yard_public_area_id) = 1)
);

CREATE INDEX idx_app_user_home_administrative_area_id ON app_user (home_administrative_area_id);
CREATE INDEX idx_app_user_yard_public_area_id ON app_user (yard_public_area_id);

--rollback DROP TABLE app_user CASCADE;
