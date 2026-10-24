CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE tenants (
                         id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                         slug            VARCHAR(63) NOT NULL UNIQUE,
                         business_name   VARCHAR(255) NOT NULL,
                         email           VARCHAR(255) NOT NULL,
                         phone           VARCHAR(50),
                         logo_url        TEXT,
                         country         VARCHAR(2) NOT NULL DEFAULT 'NG',
                         currency        VARCHAR(3) NOT NULL DEFAULT 'NGN',
                         timezone        VARCHAR(50) NOT NULL DEFAULT 'Africa/Lagos',
                         settings        JSONB NOT NULL DEFAULT '{}',
                         is_active       BOOLEAN NOT NULL DEFAULT true,
                         created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                         updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_tenants_slug ON tenants(slug);
CREATE INDEX idx_tenants_email ON tenants(email);

CREATE TABLE users (
                       id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                       tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                       email           VARCHAR(255) NOT NULL,
                       password_hash   VARCHAR(255) NOT NULL,
                       full_name       VARCHAR(255) NOT NULL,
                       phone           VARCHAR(50),
                       avatar_url      TEXT,
                       role            VARCHAR(50) NOT NULL DEFAULT 'owner' CHECK (role IN ('owner', 'admin', 'staff')),
                       permissions     JSONB NOT NULL DEFAULT '[]',
                       is_active       BOOLEAN NOT NULL DEFAULT true,
                       last_login_at   TIMESTAMPTZ,
                       created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                       updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                       CONSTRAINT uq_users_tenant_email UNIQUE (tenant_id, email)
);

CREATE INDEX idx_users_tenant ON users(tenant_id);
CREATE INDEX idx_users_email ON users(email);

CREATE TABLE refresh_tokens (
                                id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                                user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                                tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
                                token_hash      VARCHAR(255) NOT NULL UNIQUE,
                                expires_at      TIMESTAMPTZ NOT NULL,
                                created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
                                revoked_at      TIMESTAMPTZ
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_tenant ON refresh_tokens(tenant_id);