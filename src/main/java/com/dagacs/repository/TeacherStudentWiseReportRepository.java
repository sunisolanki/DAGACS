package com.dagacs.repository;

import com.dagacs.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only, self-scoped additive data source for the student-wise attendance
 * matrix (enrollment-ordered register + per-session cells). Self-scope is
 * derived from the authenticated teacher (service layer); data is restricted to
 * that teacher's own recorded sessions (session.teacherEntity) AND to their own
 * teaching assignments (TeacherSubjectSectionAssignment), mirroring the frozen
 * M7.1 self-scoping rules. The frozen M7.1 aggregation queries are untouched.
 */
@Repository
public interface TeacherStudentWiseReportRepository extends JpaRepository<AttendanceRecord, Long> {

    @Query("SELECT stu.id AS studentId, stu.rollNumber AS rollNumber, "
            + "stu.enrollmentNumber AS enrollmentNumber, stu.name AS studentName, "
            + "s.id AS sessionId, s.lecturePeriod AS lecturePeriod, s.date AS date, "
            + "ar.status AS status, ar.isPresent AS isPresent, "
            + "sub.id AS subjectId, sub.name AS subjectName, "
            + "sec.id AS sectionId, sec.sectionCode AS sectionCode, sec.name AS sectionName, "
            + "bat.id AS batchId, bat.batchCode AS batchCode "
            + "FROM AttendanceRecord ar "
            + "JOIN ar.session s JOIN s.teacherEntity t "
            + "JOIN ar.student stu JOIN ar.subject sub "
            + "LEFT JOIN ar.section sec LEFT JOIN ar.batch bat "
            + "WHERE t.id = :teacherId "
            + "AND s.status = 'CONDUCTED' "
            + "AND (:subjectId IS NULL OR sub.id = :subjectId) "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "AND ((sec.id IS NOT NULL AND EXISTS (SELECT 1 FROM TeacherSubjectSectionAssignment a "
            + "WHERE a.teacher.id = t.id AND a.subjectOffering.subject.id = sub.id AND a.section.id = sec.id)) "
            + "OR (bat.id IS NOT NULL AND EXISTS (SELECT 1 FROM TeacherSubjectSectionAssignment a "
            + "WHERE a.teacher.id = t.id AND a.subjectOffering.subject.id = sub.id AND a.batch.id = bat.id))) "
            + "AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "AND (:batchId IS NULL OR bat.id = :batchId) "
            + "ORDER BY stu.enrollmentNumber ASC, s.date ASC, s.lecturePeriod ASC")
    List<StudentWiseRecordAggregation> findStudentWiseByTeacherAndDateRange(
            @Param("teacherId") Long teacherId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            @Param("subjectId") Long subjectId,
            @Param("sectionId") Long sectionId,
            @Param("batchId") Long batchId);

    /**
     * Authoritative CONDUCTED-session set for the selected teaching context and
     * date range. This - not the per-student attendance-record count - is the
     * source of truth for both the matrix date columns and the {@code Total
     * Classes} denominator, so a conducted class is counted even when a given
     * student has no record for it, and no student can have a smaller
     * denominator than a classmate.
     *
     * <p>Scope mirrors the record query exactly (own sessions, CONDUCTED only,
     * authorized subject/section, inclusive range) so the columns and the
     * denominator can never disagree.
     */
    @Query("SELECT s.id AS sessionId, s.date AS date, s.lecturePeriod AS lecturePeriod "
            + "FROM AttendanceSession s "
            + "JOIN s.teacherEntity t JOIN s.subjectEntity sub "
            + "LEFT JOIN s.sectionEntity sec LEFT JOIN s.batchEntity bat "
            + "WHERE t.id = :teacherId "
            + "AND s.status = 'CONDUCTED' "
            + "AND sub.id = :subjectId "
            + "AND (:startDate IS NULL OR s.date >= :startDate) "
            + "AND (:endDate IS NULL OR s.date <= :endDate) "
            + "AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "AND (:batchId IS NULL OR bat.id = :batchId) "
            + "ORDER BY s.date ASC, s.lecturePeriod ASC")
    List<ConductedSessionProjection> findConductedSessions(
            @Param("teacherId") Long teacherId,
            @Param("subjectId") Long subjectId,
            @Param("sectionId") Long sectionId,
            @Param("batchId") Long batchId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /** One CONDUCTED session: its identity, date and lecture period. */
    interface ConductedSessionProjection {
        Long getSessionId();

        String getDate();

        String getLecturePeriod();
    }

    interface StudentWiseRecordAggregation {
        Long getStudentId();
        String getRollNumber();
        String getEnrollmentNumber();
        String getStudentName();
        Long getSessionId();
        String getLecturePeriod();
        String getDate();
        String getStatus();
        Boolean getIsPresent();
        Long getSubjectId();
        String getSubjectName();
        Long getSectionId();
        String getSectionCode();
        String getSectionName();
        Long getBatchId();
        String getBatchCode();
    }
}