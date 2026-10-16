--liquibase formatted sql

--changeset vitalii:004-create-app-user-table
--comment: Register row of a user (Keycloak user id + assignment to exactly one home administrative area or outside public area)
CREATE TABLE app_user
(
    id                          UUID                     NOT NULL,
    home_administrative_area_id UUID,
    outside_public_area_id      UUID,

    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_app_user PRIMARY KEY (id),
    CONSTRAINT fk_app_user_home_administrative_area
        FOREIGN KEY (home_administrative_area_id) REFERENCES home_administrative_area (id),
    CONSTRAINT fk_app_user_outside_public_area
        FOREIGN KEY (outside_public_area_id) REFERENCES outside_public_area (id),
    CONSTRAINT ck_app_user_single_assignment_required
        CHECK (num_nonnulls(home_administrative_area_id, outside_public_area_id) = 1)
);

CREATE INDEX idx_app_user_home_administrative_area_id ON app_user (home_administrative_area_id);
CREATE INDEX idx_app_user_outside_public_area_id ON app_user (outside_public_area_id);

--rollback DROP TABLE app_user CASCADE;


--changeset vitalii:004-insert-initial-admin-users
--comment: Initial admin users, one per top-level home administrative area. Ids MUST match the Keycloak user ids.
INSERT INTO app_user (id, home_administrative_area_id)
VALUES ('a0000000-0000-4000-8000-000000000001', (SELECT id FROM home_administrative_area WHERE code = 'HOME001')),
       ('a0000000-0000-4000-8000-000000000002', (SELECT id FROM home_administrative_area WHERE code = 'HOME002')),
       ('a0000000-0000-4000-8000-000000000003', (SELECT id FROM home_administrative_area WHERE code = 'HOME003')),
       ('a0000000-0000-4000-8000-000000000004', (SELECT id FROM home_administrative_area WHERE code = 'HOME004')),
       ('a0000000-0000-4000-8000-000000000005',
        (SELECT id FROM home_administrative_area WHERE code = 'HOME005')) ON CONFLICT (id) DO NOTHING;

--rollback DELETE FROM app_user WHERE id IN ('a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000005');
