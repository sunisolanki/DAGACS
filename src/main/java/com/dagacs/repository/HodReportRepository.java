package com.dagacs.repository;

import com.dagacs.entity.AttendanceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Read-only, department-scoped report queries for M7.1 (HOD daily-lecture and
 * recording-coverage feeds). Both feeds are RECORD-BACKED: rows are derived from
 * AttendanceRecord only, so a session without records (SCHEDULED with no marks,
 * CANCELLED) never contributes. Department scope is enforced through the verified
 * section chain {@code AttendanceRecord -> Section -> Batch -> AcademicSession ->
 * Program -> Department} — never via Subject.department and never via
 * Student.program (mirrors M6.2 correction #2). No client-supplied entity ID
 * reaches any query.
 */
@Repository
public interface HodReportRepository extends JpaRepository<AttendanceRecord, Long> {

    // ----- Daily-lecture feed (one row per record-backed session) -----

    @Query(value = "SELECT ar.session.id AS sessionId, "
            + "ar.date AS date, ar.lecturePeriod AS lecturePeriod, "
            + "sub.code AS subjectCode, sub.name AS subjectName, "
            + "sec.sectionCode AS sectionCode, sec.name AS sectionName, "
            + "COUNT(ar) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM AttendanceRecord ar "
            + "JOIN ar.subject sub JOIN ar.section sec "
            + "JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
            + "WHERE p.department.id = :deptId "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY ar.session.id, ar.date, ar.lecturePeriod, sub.id, sub.code, sub.name, sec.id, sec.sectionCode, sec.name "
            + "ORDER BY ar.date ASC, sub.code ASC, sec.sectionCode ASC, ar.lecturePeriod ASC",
            countQuery = "SELECT COUNT(DISTINCT ar.session.id) "
                    + "FROM AttendanceRecord ar JOIN ar.section sec "
                    + "JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
                    + "WHERE p.department.id = :deptId "
                    + "AND (:startDate IS NULL OR ar.date >= :startDate) "
                    + "AND (:endDate IS NULL OR ar.date <= :endDate)")
    Page<HodDailyLectureAggregation> findDailyLectureByDepartmentAndDateRange(
            @Param("deptId") Long deptId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            Pageable pageable);

    /**
     * Phase-2 additive batch twin of the daily-lecture feed. Groups by batch
     * (zero-section batch-mode sessions). Department scope is enforced through
     * {@code AttendanceRecord -> Batch -> AcademicSession -> Program -> Department}.
     * Added only; the section-based query above is frozen.
     */
    @Query(value = "SELECT ar.session.id AS sessionId, "
            + "ar.date AS date, ar.lecturePeriod AS lecturePeriod, "
            + "sub.code AS subjectCode, sub.name AS subjectName, "
            + "bat.batchCode AS batchCode, "
            + "COUNT(ar) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM AttendanceRecord ar "
            + "JOIN ar.subject sub JOIN ar.batch bat "
            + "JOIN bat.academicSession acs JOIN acs.program p "
            + "WHERE p.department.id = :deptId "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY ar.session.id, ar.date, ar.lecturePeriod, sub.id, sub.code, sub.name, bat.id, bat.batchCode "
            + "ORDER BY ar.date ASC, sub.code ASC, bat.batchCode ASC, ar.lecturePeriod ASC",
            countQuery = "SELECT COUNT(DISTINCT ar.session.id) "
                    + "FROM AttendanceRecord ar JOIN ar.batch bat "
                    + "JOIN bat.academicSession acs JOIN acs.program p "
                    + "WHERE p.department.id = :deptId "
                    + "AND (:startDate IS NULL OR ar.date >= :startDate) "
                    + "AND (:endDate IS NULL OR ar.date <= :endDate)")
    Page<HodDailyLectureBatchAggregation> findDailyLectureByDepartmentAndDateRangeBatch(
            @Param("deptId") Long deptId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            Pageable pageable);

    // ----- Recording-coverage feed (one row per record-backed subject+section) -----

    @Query(value = "SELECT sub.code AS subjectCode, sub.name AS subjectName, "
            + "sec.sectionCode AS sectionCode, sec.name AS sectionName, "
            + "COUNT(DISTINCT ar.date) AS recordedDateCount, "
            + "COUNT(DISTINCT ar.session.id) AS sessionCount "
            + "FROM AttendanceRecord ar "
            + "JOIN ar.subject sub JOIN ar.section sec "
            + "JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
            + "WHERE p.department.id = :deptId "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY sub.id, sub.code, sub.name, sec.id, sec.sectionCode, sec.name "
            + "ORDER BY sub.code ASC, sec.sectionCode ASC",
            countQuery = "SELECT COUNT(DISTINCT CONCAT(CONCAT(sub.code, '|'), sec.sectionCode)) "
                    + "FROM AttendanceRecord ar "
                    + "JOIN ar.subject sub JOIN ar.section sec "
                    + "JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
                    + "WHERE p.department.id = :deptId "
                    + "AND (:startDate IS NULL OR ar.date >= :startDate) "
                    + "AND (:endDate IS NULL OR ar.date <= :endDate)")
    Page<HodCoverageAggregation> findCoverageByDepartmentAndDateRange(
            @Param("deptId") Long deptId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            Pageable pageable);

    /**
     * Phase-2 additive batch twin of the recording-coverage feed. Groups by
     * subject + batch. Added only; the section-based query above is frozen.
     */
    @Query(value = "SELECT sub.code AS subjectCode, sub.name AS subjectName, "
            + "bat.batchCode AS batchCode, "
            + "COUNT(DISTINCT ar.date) AS recordedDateCount, "
            + "COUNT(DISTINCT ar.session.id) AS sessionCount "
            + "FROM AttendanceRecord ar "
            + "JOIN ar.subject sub JOIN ar.batch bat "
            + "JOIN bat.academicSession acs JOIN acs.program p "
            + "WHERE p.department.id = :deptId "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY sub.id, sub.code, sub.name, bat.id, bat.batchCode "
            + "ORDER BY sub.code ASC, bat.batchCode ASC",
            countQuery = "SELECT COUNT(DISTINCT CONCAT(CONCAT(sub.code, '|'), bat.batchCode)) "
                    + "FROM AttendanceRecord ar "
                    + "JOIN ar.subject sub JOIN ar.batch bat "
                    + "JOIN bat.academicSession acs JOIN acs.program p "
                    + "WHERE p.department.id = :deptId "
                    + "AND (:startDate IS NULL OR ar.date >= :startDate) "
                    + "AND (:endDate IS NULL OR ar.date <= :endDate)")
    Page<HodCoverageBatchAggregation> findCoverageByDepartmentAndDateRangeBatch(
            @Param("deptId") Long deptId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            Pageable pageable);

    // ----- Projections -----

    interface HodDailyLectureAggregation {
        Long getSessionId();
        String getDate();
        String getLecturePeriod();
        String getSubjectCode();
        String getSubjectName();
        String getSectionCode();
        String getSectionName();
        Long getPresentCount();
        Long getTotalRecorded();
    }

    interface HodCoverageAggregation {
        String getSubjectCode();
        String getSubjectName();
        String getSectionCode();
        String getSectionName();
        Long getRecordedDateCount();
        Long getSessionCount();
    }

    interface HodDailyLectureBatchAggregation {
        Long getSessionId();
        String getDate();
        String getLecturePeriod();
        String getSubjectCode();
        String getSubjectName();
        String getBatchCode();
        Long getPresentCount();
        Long getTotalRecorded();
    }

    interface HodCoverageBatchAggregation {
        String getSubjectCode();
        String getSubjectName();
        String getBatchCode();
        Long getRecordedDateCount();
        Long getSessionCount();
    }
}