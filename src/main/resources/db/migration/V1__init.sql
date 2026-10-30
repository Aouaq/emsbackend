-- ElectricWave access backend v1 schema.
-- The Emoncms read key is never stored in plaintext: key_hash is used for
-- claim matching and emoncms_api_key_enc holds the AES-256-GCM payload.

CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(255),
    full_name      VARCHAR(255),
    role           VARCHAR(32)  NOT NULL DEFAULT 'CLIENT',
    auth_provider  VARCHAR(32)  NOT NULL DEFAULT 'LOCAL',
    is_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    disabled_at    TIMESTAMPTZ,
    last_login_at  TIMESTAMPTZ,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'CLIENT')),
    CONSTRAINT chk_users_auth_provider CHECK (auth_provider IN ('LOCAL', 'GOOGLE'))
);

CREATE TABLE access_keys (
    id                  BIGSERIAL PRIMARY KEY,
    resource            VARCHAR(100) NOT NULL,
    description         VARCHAR(255),
    key_hash            CHAR(64)     NOT NULL,
    key_hint            VARCHAR(16)  NOT NULL,
    emoncms_api_key_enc BYTEA        NOT NULL,
    is_active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_access_keys_resource UNIQUE (resource),
    CONSTRAINT uq_access_keys_key_hash UNIQUE (key_hash)
);

CREATE TABLE user_access_keys (
    user_id       BIGINT      NOT NULL REFERENCES users (id)       ON DELETE CASCADE,
    access_key_id BIGINT      NOT NULL REFERENCES access_keys (id) ON DELETE CASCADE,
    linked_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, access_key_id)
);

CREATE INDEX idx_user_access_keys_access_key_id ON user_access_keys (access_key_id);

CREATE OR REPLACE FUNCTION enforce_client_access_key_link()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM users WHERE id = NEW.user_id AND role = 'CLIENT'
    ) THEN
        RAISE EXCEPTION 'Only CLIENT accounts can be linked to access keys';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_user_access_keys_client_only
    BEFORE INSERT OR UPDATE ON user_access_keys
    FOR EACH ROW
    EXECUTE FUNCTION enforce_client_access_key_link();

CREATE TABLE audit_log (
    id             BIGSERIAL PRIMARY KEY,
    event_type     VARCHAR(64) NOT NULL,
    actor_user_id  BIGINT,
    actor_email    VARCHAR(255),
    target_user_id BIGINT,
    target_email   VARCHAR(255),
    resource       VARCHAR(100),
    ip_address     VARCHAR(64),
    details        JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_created_at ON audit_log (created_at DESC);
CREATE INDEX idx_audit_log_actor_user_id ON audit_log (actor_user_id);
CREATE INDEX idx_audit_log_event_type ON audit_log (event_type);
