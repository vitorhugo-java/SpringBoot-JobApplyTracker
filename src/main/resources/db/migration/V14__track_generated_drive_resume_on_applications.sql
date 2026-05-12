ALTER TABLE job_applications
    ADD COLUMN drive_resume_file_id VARCHAR(255) NULL AFTER drive_vacancy_folder_id,
    ADD COLUMN drive_resume_file_name VARCHAR(255) NULL AFTER drive_resume_file_id,
    ADD COLUMN drive_resume_document_url VARCHAR(2048) NULL AFTER drive_resume_file_name,
    ADD COLUMN drive_resume_generated_at DATETIME NULL AFTER drive_resume_document_url;
