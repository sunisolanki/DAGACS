-- DAGACS Database Migration: Add academic_session_id to students table
-- and make optional columns nullable for bulk import support.

-- 1. Add academic_session_id column (NOT NULL for backfill safety)
ALTER TABLE students ADD COLUMN academic_session_id BIGINT NOT NULL;

-- 2. Add foreign key constraint
ALTER TABLE students ADD CONSTRAINT fk_students_academic_session
    FOREIGN KEY (academic_session_id) REFERENCES academic_sessions(id);

-- 3. Backfill existing students: derive academic_session from their batch
UPDATE students s
JOIN batches b ON s.batch_id = b.id
SET s.academic_session_id = b.academic_session_id;

-- 4. Drop NOT NULL constraints on optional columns for bulk import support
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
