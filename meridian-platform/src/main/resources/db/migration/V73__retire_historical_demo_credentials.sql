-- Keep historical actors and their references; retire only the five V13 credentials.
-- Generate a separate 256-bit secret per row and discard it after BCrypt hashing.
DO $migration$
DECLARE
    crypto_schema TEXT;
BEGIN
    -- pgcrypto may live outside the application schema in an existing database.
    SELECT n.nspname INTO STRICT crypto_schema
    FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
    WHERE e.extname = 'pgcrypto';

    EXECUTE format($retire$
UPDATE users
SET status = 'DISABLED',
    password_hash = %1$I.crypt(encode(%1$I.gen_random_bytes(32), 'hex'), %1$I.gen_salt('bf', 10)),
    authorization_version = authorization_version + 1,
    failed_login_attempts = 0,
    locked_until = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE id IN (
    '00000000-0000-0000-0000-000000000301',
    '00000000-0000-0000-0000-000000000302',
    '00000000-0000-0000-0000-000000000303',
    '00000000-0000-0000-0000-000000000304',
    '00000000-0000-0000-0000-000000000305'
)
$retire$, crypto_schema);
END;
$migration$;

-- Revoke every outstanding refresh family, including consumed predecessors.
UPDATE refresh_token_sessions
SET revoked_at = GREATEST(CURRENT_TIMESTAMP, issued_at)
WHERE revoked_at IS NULL AND user_id IN (
    '00000000-0000-0000-0000-000000000301',
    '00000000-0000-0000-0000-000000000302',
    '00000000-0000-0000-0000-000000000303',
    '00000000-0000-0000-0000-000000000304',
    '00000000-0000-0000-0000-000000000305'
);

-- Password setup shares password-reset state. Preserve terminal token history.
UPDATE password_reset_tokens
SET revoked_at = GREATEST(CURRENT_TIMESTAMP, issued_at)
WHERE consumed_at IS NULL AND revoked_at IS NULL AND user_id IN (
    '00000000-0000-0000-0000-000000000301',
    '00000000-0000-0000-0000-000000000302',
    '00000000-0000-0000-0000-000000000303',
    '00000000-0000-0000-0000-000000000304',
    '00000000-0000-0000-0000-000000000305'
);

UPDATE email_verification_tokens
SET revoked_at = GREATEST(CURRENT_TIMESTAMP, issued_at)
WHERE consumed_at IS NULL AND revoked_at IS NULL AND user_id IN (
    '00000000-0000-0000-0000-000000000301',
    '00000000-0000-0000-0000-000000000302',
    '00000000-0000-0000-0000-000000000303',
    '00000000-0000-0000-0000-000000000304',
    '00000000-0000-0000-0000-000000000305'
);
