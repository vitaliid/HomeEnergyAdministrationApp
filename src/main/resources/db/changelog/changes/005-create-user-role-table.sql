--liquibase formatted sql

--changeset vitalii:005-create-user-role-table
--comment: Roles of a user, each on exactly one scope (home administrative area or outside public area)
CREATE TABLE user_role
(
    id                          UUID                     NOT NULL DEFAULT gen_random_uuid(),
    user_id                     UUID                     NOT NULL,
    role                        VARCHAR(32)              NOT NULL,
    home_administrative_area_id UUID,
    outside_public_area_id      UUID,

    created_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_user_role PRIMARY KEY (id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_user_role_home_administrative_area
        FOREIGN KEY (home_administrative_area_id) REFERENCES home_administrative_area (id),
    CONSTRAINT fk_user_role_outside_public_area
        FOREIGN KEY (outside_public_area_id) REFERENCES outside_public_area (id),

    CONSTRAINT ck_user_role_role
        CHECK (role IN ('ESR_EDITOR', 'ESR_APPROVER', 'ESR_VIEWER',
                        'GIR_EDITOR', 'GIR_APPROVER', 'GIR_VIEWER',
                        'ADMIN')),
    CONSTRAINT ck_user_role_single_scope_required
        CHECK (num_nonnulls(home_administrative_area_id, outside_public_area_id) = 1)
);

CREATE UNIQUE INDEX uq_user_role_home_administrative_area
    ON user_role (user_id, role, home_administrative_area_id) WHERE home_administrative_area_id IS NOT NULL;

CREATE UNIQUE INDEX uq_user_role_outside_public_area
    ON user_role (user_id, role, outside_public_area_id) WHERE outside_public_area_id IS NOT NULL;

--rollback DROP TABLE user_role CASCADE;


--changeset vitalii:005-insert-initial-admin-roles
--comment: ADMIN role for each initial admin, scoped to their home administrative area.
INSERT INTO user_role (user_id, role, home_administrative_area_id)
VALUES ('a0000000-0000-4000-8000-000000000001', 'ADMIN',
        (SELECT id FROM home_administrative_area WHERE code = 'HOME001')),
       ('a0000000-0000-4000-8000-000000000002', 'ADMIN',
        (SELECT id FROM home_administrative_area WHERE code = 'HOME002')),
       ('a0000000-0000-4000-8000-000000000003', 'ADMIN',
        (SELECT id FROM home_administrative_area WHERE code = 'HOME003')),
       ('a0000000-0000-4000-8000-000000000004', 'ADMIN',
        (SELECT id FROM home_administrative_area WHERE code = 'HOME004')),
       ('a0000000-0000-4000-8000-000000000005', 'ADMIN',
        (SELECT id FROM home_administrative_area WHERE code = 'HOME005')) ON CONFLICT (user_id, role, home_administrative_area_id)
WHERE home_administrative_area_id IS NOT NULL DO NOTHING;

--rollback DELETE FROM user_role WHERE role = 'ADMIN' AND user_id IN ('a0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000002', 'a0000000-0000-4000-8000-000000000003', 'a0000000-0000-4000-8000-000000000004', 'a0000000-0000-4000-8000-000000000005');