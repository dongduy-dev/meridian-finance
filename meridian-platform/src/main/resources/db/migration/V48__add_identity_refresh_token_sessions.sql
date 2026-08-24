CREATE TABLE refresh_token_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    family_id UUID NOT NULL,
    token_digest CHAR(64) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    revoked_at TIMESTAMP,

    CONSTRAINT fk_refresh_token_sessions_user
        FOREIGN KEY (user_id)
        REFERENCES users (id),
    CONSTRAINT uq_refresh_token_sessions_token_digest
        UNIQUE (token_digest),
    CONSTRAINT chk_refresh_token_sessions_digest_sha256_hex
        CHECK (token_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_refresh_token_sessions_expiry
        CHECK (expires_at > issued_at),
    CONSTRAINT chk_refresh_token_sessions_consumed_time
        CHECK (consumed_at IS NULL OR consumed_at >= issued_at),
    CONSTRAINT chk_refresh_token_sessions_revoked_time
        CHECK (revoked_at IS NULL OR revoked_at >= issued_at)
);

CREATE INDEX idx_refresh_token_sessions_user_id
    ON refresh_token_sessions (user_id);

CREATE INDEX idx_refresh_token_sessions_family_id
    ON refresh_token_sessions (family_id);

CREATE UNIQUE INDEX uq_refresh_token_sessions_active_family
    ON refresh_token_sessions (family_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
