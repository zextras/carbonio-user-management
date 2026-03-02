CREATE TABLE user_info_cache (
    user_id    VARCHAR(64)  PRIMARY KEY,
    email      VARCHAR(320),
    full_name  VARCHAR(512) NOT NULL DEFAULT '',
    domain     VARCHAR(255),
    status     VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    type       VARCHAR(32)  NOT NULL DEFAULT 'INTERNAL',
    expires_at BIGINT       NOT NULL
);
CREATE INDEX idx_user_info_email ON user_info_cache (email);

CREATE TABLE user_details_cache (
    user_id              VARCHAR(64)  PRIMARY KEY,
    token_hash           VARCHAR(64),
    locale               VARCHAR(32)  NOT NULL DEFAULT 'en',
    feature_list         JSONB        NOT NULL DEFAULT '{}'::JSONB,
    expires_at           BIGINT       NOT NULL
);
CREATE INDEX idx_user_details_token_hash ON user_details_cache (token_hash);
