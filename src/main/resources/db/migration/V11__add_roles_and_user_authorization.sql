CREATE TABLE IF NOT EXISTS roles (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(20) NOT NULL,
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id BINARY(16) NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO roles (name) VALUES ('USER');
INSERT IGNORE INTO roles (name) VALUES ('BETA');
INSERT IGNORE INTO roles (name) VALUES ('ADMIN');

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
         JOIN roles r ON r.name = 'USER'
WHERE NOT EXISTS (
    SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id
);

INSERT INTO users (id, name, email, password_hash, reminder_time, created_at, updated_at)
SELECT UNHEX(REPLACE('00000000-0000-0000-0000-000000000001', '-', '')),
       'Admin User',
       'admin@jobtracker.local',
       '$2b$12$QToBups4coFRvP0ykMiYQOIjfHCBuUTgAi4czRXM5e0HVW5L6/F1y',
       '19:00:00',
       NOW(),
       NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE email = 'admin@jobtracker.local'
);

INSERT INTO users (id, name, email, password_hash, reminder_time, created_at, updated_at)
SELECT UNHEX(REPLACE('00000000-0000-0000-0000-000000000002', '-', '')),
       'Sample User',
       'user@jobtracker.local',
       '$2b$12$2fi1Eof0SmvlyAGu2B9g/uTRqaXHI8EXq4ilMasRnYLoA/DrU.Y1e',
       '19:00:00',
       NOW(),
       NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM users WHERE email = 'user@jobtracker.local'
);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
         JOIN roles r ON r.name IN ('USER', 'ADMIN')
WHERE u.email = 'admin@jobtracker.local'
  AND NOT EXISTS (
    SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id
);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u
         JOIN roles r ON r.name = 'USER'
WHERE u.email = 'user@jobtracker.local'
  AND NOT EXISTS (
    SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id
);
