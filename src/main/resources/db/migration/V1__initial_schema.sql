-- V1: Initial schema

CREATE TABLE IF NOT EXISTS users (
    id            BINARY(16)   NOT NULL PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    DATETIME     NOT NULL,
    updated_at    DATETIME     NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    INDEX idx_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BINARY(16)   NOT NULL PRIMARY KEY,
    token       VARCHAR(512) NOT NULL,
    expiry_date DATETIME     NOT NULL,
    revoked     BOOLEAN      NOT NULL DEFAULT FALSE,
    user_id     BINARY(16)   NOT NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    CONSTRAINT uk_refresh_tokens_token UNIQUE (token),
    INDEX idx_refresh_token_value (token),
    INDEX idx_refresh_token_user_id (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id          BINARY(16)   NOT NULL PRIMARY KEY,
    token       VARCHAR(512) NOT NULL,
    expiry_date DATETIME     NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    user_id     BINARY(16)   NOT NULL,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    CONSTRAINT uk_prt_token UNIQUE (token),
    INDEX idx_prt_token (token),
    INDEX idx_prt_user_id (user_id),
    CONSTRAINT fk_prt_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS job_applications (
    id                           BINARY(16)    NOT NULL PRIMARY KEY,
    vacancy_name                 VARCHAR(255)  NOT NULL,
    recruiter_name               VARCHAR(255),
    vacancy_opened_by            VARCHAR(255)  NOT NULL,
    vacancy_link                 VARCHAR(2048),
    application_date             DATE,
    rh_accepted_connection       BOOLEAN       NOT NULL DEFAULT FALSE,
    interview_scheduled          BOOLEAN       NOT NULL DEFAULT FALSE,
    next_step_date_time          DATETIME,
    status                       VARCHAR(100)  NOT NULL,
    recruiter_dm_reminder_enabled BOOLEAN      NOT NULL DEFAULT FALSE,
    user_id                      BIGINT        NOT NULL,
    created_at                   DATETIME      NOT NULL,
    updated_at                   DATETIME      NOT NULL,
    INDEX idx_app_user_id (user_id),
    INDEX idx_app_status (status),
    INDEX idx_app_next_step (next_step_date_time),
    INDEX idx_app_application_date (application_date),
    CONSTRAINT fk_job_applications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
