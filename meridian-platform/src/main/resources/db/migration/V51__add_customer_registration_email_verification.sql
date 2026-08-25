ALTER TABLE users
    ADD COLUMN email_verified_at TIMESTAMP;

UPDATE users
SET email_verified_at = CURRENT_TIMESTAMP
WHERE email_verified_at IS NULL;

CREATE TABLE email_verification_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token_digest CHAR(64) NOT NULL,
    issued_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    consumed_at TIMESTAMP,
    revoked_at TIMESTAMP,

    CONSTRAINT fk_email_verification_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES users (id),
    CONSTRAINT uq_email_verification_tokens_token_digest
        UNIQUE (token_digest),
    CONSTRAINT chk_email_verification_tokens_digest_sha256_hex
        CHECK (token_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_email_verification_tokens_expiry
        CHECK (expires_at > issued_at),
    CONSTRAINT chk_email_verification_tokens_consumed_time
        CHECK (consumed_at IS NULL OR consumed_at >= issued_at),
    CONSTRAINT chk_email_verification_tokens_revoked_time
        CHECK (revoked_at IS NULL OR revoked_at >= issued_at),
    CONSTRAINT chk_email_verification_tokens_single_terminal_state
        CHECK (consumed_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX idx_email_verification_tokens_user_id
    ON email_verification_tokens (user_id);

CREATE UNIQUE INDEX uq_email_verification_tokens_active_user
    ON email_verification_tokens (user_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;
