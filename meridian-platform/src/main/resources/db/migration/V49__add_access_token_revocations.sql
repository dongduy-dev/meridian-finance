CREATE TABLE access_token_revocations (
    token_id UUID PRIMARY KEY,
    revoked_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,

    CONSTRAINT chk_access_token_revocations_expiry
        CHECK (expires_at > revoked_at)
);

CREATE INDEX idx_access_token_revocations_expires_at
    ON access_token_revocations (expires_at);
