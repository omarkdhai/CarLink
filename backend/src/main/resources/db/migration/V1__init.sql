-- ============================================
-- CarLink schema — initial migration
-- ============================================

-- UUID generation (PostgreSQL 13+ has gen_random_uuid() built-in)
-- pgcrypto not required on Postgres 13+.

-- ---------- USERS ----------
CREATE TABLE users (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email             VARCHAR(255) NOT NULL,
    password_hash     VARCHAR(255) NOT NULL,
    phone             VARCHAR(20),
    first_name        VARCHAR(100),
    last_name         VARCHAR(100),
    role              VARCHAR(20)  NOT NULL DEFAULT 'USER',
    is_active         BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT chk_users_email_format CHECK (email ~* '^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}$')
);

CREATE INDEX idx_users_email ON users (email);

-- ---------- VEHICLES ----------
CREATE TABLE vehicles (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id       UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    nickname       VARCHAR(100),
    brand          VARCHAR(100),
    model          VARCHAR(100),
    color          VARCHAR(50),
    license_plate  VARCHAR(20) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_vehicles_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_vehicles_owner ON vehicles (owner_id);
CREATE INDEX idx_vehicles_status ON vehicles (status);

-- ---------- QR CODES ----------
CREATE TABLE qr_codes (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id     UUID NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    -- Hash of the public token (SHA-256, hex). The raw token is never persisted.
    token_hash     VARCHAR(128) NOT NULL,
    is_active      BOOLEAN NOT NULL DEFAULT TRUE,
    activated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deactivated_at TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_qr_active_deactivated CHECK (
        (is_active = TRUE AND deactivated_at IS NULL)
        OR
        (is_active = FALSE AND deactivated_at IS NOT NULL)
    )
);

CREATE INDEX idx_qr_codes_token_hash ON qr_codes (token_hash);
CREATE INDEX idx_qr_codes_vehicle ON qr_codes (vehicle_id);
CREATE INDEX idx_qr_codes_active ON qr_codes (is_active);

-- Partial unique index: only one ACTIVE QR per vehicle.
-- (The table-level unique simulates it but a partial index is exact.)
CREATE UNIQUE INDEX uq_qr_codes_one_active_per_vehicle
    ON qr_codes (vehicle_id)
    WHERE is_active = TRUE;

-- ---------- CONVERSATIONS ----------
CREATE TABLE conversations (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vehicle_id UUID NOT NULL REFERENCES vehicles (id) ON DELETE CASCADE,
    channel    VARCHAR(20) NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'SENT',
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_conv_channel CHECK (channel IN ('WHATSAPP', 'SMS')),
    CONSTRAINT chk_conv_status CHECK (status IN ('SENT', 'FAILED', 'EXPIRED'))
);

CREATE INDEX idx_conversations_vehicle ON conversations (vehicle_id);
CREATE INDEX idx_conversations_expires ON conversations (expires_at);
CREATE INDEX idx_conversations_status ON conversations (status);

-- ---------- MESSAGES ----------
CREATE TABLE messages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_messages_conversation ON messages (conversation_id);

-- ---------- REFRESH TOKENS ----------
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

-- ---------- PASSWORD RESET TOKENS ----------
CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_password_reset_user ON password_reset_tokens (user_id);
CREATE INDEX idx_password_reset_token ON password_reset_tokens (token_hash);

-- ---------- EMAIL VERIFICATION TOKENS ----------
CREATE TABLE email_verification_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(128) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used_at    TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_email_verification_user ON email_verification_tokens (user_id);
CREATE INDEX idx_email_verification_token ON email_verification_tokens (token_hash);

-- ---------- REPORTS ----------
CREATE TABLE reports (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_ip     VARCHAR(45),
    conversation_id UUID REFERENCES conversations (id) ON DELETE SET NULL,
    reason          VARCHAR(20) NOT NULL DEFAULT 'OTHER',
    details         TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_reports_reason CHECK (reason IN ('SPAM', 'ABUSE', 'OTHER')),
    CONSTRAINT chk_reports_status CHECK (status IN ('OPEN', 'REVIEWED', 'CLOSED'))
);

CREATE INDEX idx_reports_status ON reports (status);
CREATE INDEX idx_reports_created ON reports (created_at);

-- ---------- AUDIT LOGS ----------
CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   UUID,
    ip_address  VARCHAR(45),
    user_agent  TEXT,
    details     JSONB,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_user ON audit_logs (user_id);
CREATE INDEX idx_audit_logs_created ON audit_logs (created_at);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id);