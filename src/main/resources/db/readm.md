CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE tenants {
id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
slug            VARCHAR(63) NOT NULL UNIQUE,          -- Subdomain e.g. "acme"
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
}

CREATE UNIQUE INDEX idx_tenants_slug ON tenants(slug);
CREATE INDEX idx_tenants_email ON tenants(email);

-- 3. Users Table (Tenant-scoped)
CREATE TABLE users (
id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
tenant_id       UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
email           VARCHAR(255) NOT NULL,
password_hash   VARCHAR(255) NOT NULL,
full_name       VARCHAR(255) NOT NULL,
phone           VARCHAR(50),
avatar_url      TEXT,
role            VARCHAR(50) NOT NULL DEFAULT 'owner'
CHECK (role IN ('owner', 'admin', 'staff')),
permissions     JSONB NOT NULL DEFAULT '[]',
is_active       BOOLEAN NOT NULL DEFAULT true,
last_login_at   TIMESTAMPTZ,
created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
UNIQUE(tenant_id, email)
);

{
"businessName": "Acme Fashion",
"slug": "acme-fashion",
"fullName": "Jane Doe",
"email": "jane@acme.com",
"password": "Password123!"
}

