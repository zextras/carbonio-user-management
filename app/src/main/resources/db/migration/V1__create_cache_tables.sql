CREATE TABLE user_info_cache (
    user_id    VARCHAR(64)  PRIMARY KEY,
    email      VARCHAR(320) UNIQUE,
    full_name  VARCHAR(512) NOT NULL DEFAULT '',
    domain     VARCHAR(255),
    status     VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    type       VARCHAR(32)  NOT NULL DEFAULT 'INTERNAL',
    expires_at BIGINT       NOT NULL
);
CREATE INDEX idx_user_info_expires ON user_info_cache (expires_at);

CREATE TABLE user_myself_cache (
    user_id      VARCHAR(64)  PRIMARY KEY,
    token_hash   VARCHAR(64)  UNIQUE,
    email        VARCHAR(320),
    full_name    VARCHAR(512) NOT NULL DEFAULT '',
    domain       VARCHAR(255),
    status       VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    type         VARCHAR(32)  NOT NULL DEFAULT 'INTERNAL',
    locale       VARCHAR(32)  NOT NULL DEFAULT 'en',
    feature_list JSONB        NOT NULL DEFAULT '{}'::JSONB,
    expires_at   BIGINT       NOT NULL
);
CREATE INDEX idx_user_myself_expires ON user_myself_cache (expires_at);
