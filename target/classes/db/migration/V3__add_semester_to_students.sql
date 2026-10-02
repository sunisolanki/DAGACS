-- DAGACS Database Migration: Add semester_id to students table.
--
-- The student's current semester is stored, not derived (the existing frozen
-- export/analytics stance treats semester as NOT derivable). The FK reuses the
-- existing master-data `semesters` table - the same pattern already used by
-- subject_offerings.semester_id - so no duplicate academic relationship is
-- introduced. The column is nullable so existing/simple-imported students keep
-- a NULL semester (still shown under "All" filters).

ALTER TABLE students ADD COLUMN semester_id BIGINT NULL;

ALTER TABLE students ADD CONSTRAINT fk_students_semester
    FOREIGN KEY (semester_id) REFERENCES semesters(id);