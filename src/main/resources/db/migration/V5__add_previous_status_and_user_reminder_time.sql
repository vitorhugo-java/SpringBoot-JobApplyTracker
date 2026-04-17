ALTER TABLE job_applications
    ADD COLUMN previous_status VARCHAR(100) NULL AFTER status;

ALTER TABLE users
    ADD COLUMN reminder_time TIME NOT NULL DEFAULT '19:00:00' AFTER password_hash;
