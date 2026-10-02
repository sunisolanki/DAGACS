-- DAGACS Database Migration: Add personal_email to students table.
--
-- This is a separate profile/contact field independent from the
-- authentication email (User.email / Student.email). Students can
-- update their personal email from the My Profile screen without
-- affecting login credentials.
--
-- The column is nullable so existing students without a personal
-- email are not affected.

ALTER TABLE students ADD COLUMN personal_email VARCHAR(255) NULL;
