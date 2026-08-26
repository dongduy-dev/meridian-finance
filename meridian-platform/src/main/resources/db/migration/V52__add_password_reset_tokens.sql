CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_digest CHAR(64) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    revoked_at TIMESTAMP,

    CONSTRAINT fk_password_reset_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users (id),
    CONSTRAINT uq_password_reset_tokens_token_digest
        UNIQUE (token_digest),
    CONSTRAINT chk_password_reset_tokens_digest_sha256_hex
        CHECK (token_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_password_reset_tokens_expiry
        CHECK (expires_at > issued_at),
    CONSTRAINT chk_password_reset_tokens_consumed_time
        CHECK (consumed_at IS NULL OR consumed_at >= issued_at),
    CONSTRAINT chk_password_reset_tokens_revoked_time
        CHECK (revoked_at IS NULL OR revoked_at >= issued_at),
    CONSTRAINT chk_password_reset_tokens_single_terminal_state
        CHECK (consumed_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX idx_password_reset_tokens_user_id
    ON password_reset_tokens (user_id);

CREATE UNIQUE INDEX uq_password_reset_tokens_active_user
    ON password_reset_tokens (user_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
