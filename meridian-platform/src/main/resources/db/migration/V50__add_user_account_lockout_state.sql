ALTER TABLE users
    ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN locked_until TIMESTAMP;

ALTER TABLE users
    ADD CONSTRAINT chk_users_failed_login_attempts_non_negative
        CHECK (failed_login_attempts >= 0);
