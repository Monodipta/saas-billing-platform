CREATE TABLE sessions (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users(id),
    refresh_token_id UUID         NOT NULL REFERENCES refresh_tokens(id),
    user_agent       VARCHAR(500),
    ip_address       VARCHAR(50),
    last_active_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);