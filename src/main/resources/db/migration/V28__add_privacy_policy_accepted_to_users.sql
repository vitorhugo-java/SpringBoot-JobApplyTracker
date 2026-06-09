ALTER TABLE users
  ADD COLUMN privacy_policy_accepted TINYINT(1) NOT NULL DEFAULT 0,
  ADD COLUMN privacy_policy_accepted_at DATETIME NULL;
