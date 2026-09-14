package com.dagacs.repository;

import com.dagacs.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only, self-scoped report queries for the M7.1 teacher subject-wise report.
 * Self-scope is always derived from the authenticated teacher (service layer);
 * data is restricted to that teacher's own recorded sessions (session.teacherEntity)
 * AND to their own teaching assignments (TeacherSubjectSectionAssignment), so a
 * teacher can never see another teacher's classes even with a known assignment.
 */
@Repository
public interface TeacherReportRepository extends JpaRepository<AttendanceRecord, Long> {

    @Query("SELECT sub.id AS subjectId, sub.code AS subjectCode, sub.name AS subjectName, "
            + "sec.id AS sectionId, sec.sectionCode AS sectionCode, sec.name AS sectionName, "
            + "COUNT(ar) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM AttendanceRecord ar "
            + "JOIN ar.session s JOIN s.teacherEntity t "
            + "JOIN ar.subject sub JOIN ar.section sec "
            + "WHERE t.id = :teacherId "
            + "AND EXISTS (SELECT 1 FROM TeacherSubjectSectionAssignment a "
            + "WHERE a.teacher.id = t.id AND a.subjectOffering.subject.id = sub.id AND a.section.id = sec.id) "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY sub.id, sub.code, sub.name, sec.id, sec.sectionCode, sec.name "
            + "ORDER BY sub.code ASC, sec.sectionCode ASC")
    List<TeacherSubjectAggregation> findSubjectReportByTeacherAndDateRange(
            @Param("teacherId") Long teacherId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * Phase-2 additive batch twin. Groups the same record-backed subject-wise
     * report by batch instead of section, restricted to the teacher's own
     * batch-mode assignments. Added only; the section-based query above is
     * frozen and untouched.
     */
    @Query("SELECT sub.id AS subjectId, sub.code AS subjectCode, sub.name AS subjectName, "
            + "bat.id AS batchId, bat.batchCode AS batchCode, "
            + "COUNT(ar) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM AttendanceRecord ar "
            + "JOIN ar.session s JOIN s.teacherEntity t "
            + "JOIN ar.subject sub JOIN ar.batch bat "
            + "WHERE t.id = :teacherId "
            + "AND EXISTS (SELECT 1 FROM TeacherSubjectSectionAssignment a "
            + "WHERE a.teacher.id = t.id AND a.subjectOffering.subject.id = sub.id AND a.batch.id = bat.id) "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY sub.id, sub.code, sub.name, bat.id, bat.batchCode "
            + "ORDER BY sub.code ASC, bat.batchCode ASC")
    List<TeacherBatchAggregation> findBatchSubjectReportByTeacherAndDateRange(
            @Param("teacherId") Long teacherId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    interface TeacherSubjectAggregation {
        Long getSubjectId();
        String getSubjectCode();
        String getSubjectName();
        Long getSectionId();
        String getSectionCode();
        String getSectionName();
        Long getPresentCount();
        Long getTotalRecorded();
    }

    interface TeacherBatchAggregation {
        Long getSubjectId();
        String getSubjectCode();
        String getSubjectName();
        Long getBatchId();
        String getBatchCode();
        Long getPresentCount();
        Long getTotalRecorded();
    }
}