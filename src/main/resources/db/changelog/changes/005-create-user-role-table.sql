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