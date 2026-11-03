--liquibase formatted sql

--changeset vitalii:007-create-user-role-audit-table
--comment: Envers audit table for user_role: one row per change, revtype 0 = ADD, 2 = DEL (roles are immutable, no MOD).
--comment: Business columns are nullable as Envers requires.
CREATE TABLE user_role_aud
(
    id                     UUID     NOT NULL,
    rev                    INTEGER  NOT NULL,
    revtype                SMALLINT NOT NULL,
    user_id                UUID,
    role                   VARCHAR(32),
    home_administrative_area_id UUID,
    yard_public_area_id       UUID,
    created_at             TIMESTAMP WITH TIME ZONE,
    updated_at             TIMESTAMP WITH TIME ZONE,

    CONSTRAINT pk_user_role_aud PRIMARY KEY (id, rev),
    CONSTRAINT fk_user_role_aud_revinfo FOREIGN KEY (rev) REFERENCES revinfo (id)
);

CREATE INDEX idx_user_role_aud_user_id ON user_role_aud (user_id);

--rollback DROP TABLE user_role_aud;
