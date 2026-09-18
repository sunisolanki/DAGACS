-- DAGACS Database Migration: Ensure optional columns are nullable
-- to match entity annotations (@Column(nullable = true)).

ALTER TABLE students MODIFY COLUMN gender VARCHAR(10) NULL;
ALTER TABLE students MODIFY COLUMN father_name VARCHAR(100) NULL;
ALTER TABLE students MODIFY COLUMN mother_name VARCHAR(100) NULL;
ALTER TABLE students MODIFY COLUMN photo_url VARCHAR(500) NULL;
ALTER TABLE students MODIFY COLUMN enrollment_number VARCHAR(50) NULL;
ALTER TABLE students MODIFY COLUMN age INT NULL;
ALTER TABLE students MODIFY COLUMN admission_date VARCHAR(20) NULL;
ALTER TABLE students MODIFY COLUMN status VARCHAR(20) NULL;
ALTER TABLE students MODIFY COLUMN batch_id BIGINT NULL;
ALTER TABLE students MODIFY COLUMN section_id BIGINT NULL;
