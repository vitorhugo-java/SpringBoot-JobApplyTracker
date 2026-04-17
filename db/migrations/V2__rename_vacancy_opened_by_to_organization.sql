-- Rename column `vacancy_opened_by` to `organization` in MariaDB without data loss
-- Adjust the table/column types if different in your schema.
-- This keeps the existing values and makes the column nullable (optional).

ALTER TABLE job_applications
  CHANGE COLUMN vacancy_opened_by organization VARCHAR(255) NULL;
