CREATE TABLE users (
    id             BIGSERIAL PRIMARY KEY,
    email          VARCHAR(255) NOT NULL UNIQUE,
    password_hash  VARCHAR(255),
    full_name      VARCHAR(255),
    role           VARCHAR(32)  NOT NULL DEFAULT 'CLIENT',
    auth_provider  VARCHAR(32)  NOT NULL DEFAULT 'LOCAL',
    is_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'CLIENT')),
    CONSTRAINT chk_users_auth_provider CHECK (auth_provider IN ('LOCAL', 'GOOGLE'))
);

CREATE TABLE access_keys (
    id           BIGSERIAL PRIMARY KEY,
    key_value    VARCHAR(255) NOT NULL UNIQUE,
    resource     VARCHAR(128) NOT NULL,
    description  VARCHAR(512),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE user_access_keys (
    user_id       BIGINT      NOT NULL REFERENCES users (id)      ON DELETE CASCADE,
    access_key_id BIGINT      NOT NULL REFERENCES access_keys (id) ON DELETE CASCADE,
    granted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, access_key_id)
);

CREATE INDEX idx_user_access_keys_access_key_id ON user_access_keys (access_key_id);
CREATE INDEX idx_access_keys_resource ON access_keys (resource);

INSERT INTO access_keys (key_value, resource, description) VALUES
    ('ew_metering_01', 'metering',    'Smart meter readings and consumption data'),
    ('ew_billing_01',  'billing',     'Invoices, payments and billing operations'),
    ('ew_grid_01',     'grid',        'Grid topology, outages and load data')
ON CONFLICT (key_value) DO NOTHING;
