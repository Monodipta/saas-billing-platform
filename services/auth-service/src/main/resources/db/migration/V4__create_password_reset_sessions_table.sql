CREATE TABLE password_reset_sessions (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID         NOT NULL REFERENCES users(id),
    session_token_hash VARCHAR(255) NOT NULL,
    is_used            BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at         TIMESTAMP    NOT NULL,  -- 15 minutes
    created_at         TIMESTAMP    NOT NULL DEFAULT NOW()
);