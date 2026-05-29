CREATE TABLE IF NOT EXISTS gpt_oauth_authorization_codes (
    id                    BINARY(16)   NOT NULL PRIMARY KEY,
    user_id               BINARY(16)   NOT NULL,
    client_id             VARCHAR(255) NOT NULL,
    redirect_uri          VARCHAR(500) NOT NULL,
    scope_value           VARCHAR(1000) NOT NULL,
    code_hash             VARCHAR(128) NOT NULL,
    code_challenge        VARCHAR(255) NOT NULL,
    code_challenge_method VARCHAR(20)  NOT NULL,
    expires_at            DATETIME     NOT NULL,
    used_at               DATETIME     NULL,
    created_at            DATETIME     NOT NULL,
    CONSTRAINT uk_gpt_oauth_code_hash UNIQUE (code_hash),
    INDEX idx_gpt_oauth_code_user (user_id),
    INDEX idx_gpt_oauth_code_expires (expires_at),
    CONSTRAINT fk_gpt_oauth_code_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
