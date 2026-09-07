package com.dagacs.repository;

import com.dagacs.entity.AttendanceAuditLog;
import com.dagacs.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only, department-scoped aggregation queries for the M6.2 HOD analytics
 * (M4 stays untouched). Every WHERE clause scopes data through the verified
 * department FK chains (see the M6.2 plan, section 2); no client-supplied
 * entity ID ever reaches a query.
 */
@Repository
public interface HodAnalyticsRepository extends JpaRepository<AttendanceRecord, Long> {

    // ----- Dashboard counts -----

    @Query("SELECT COUNT(p) FROM Program p WHERE p.department.id = :deptId")
    long countProgramsByDepartment(@Param("deptId") Long deptId);

    @Query("SELECT COUNT(b) FROM Batch b JOIN b.academicSession acs JOIN acs.program p WHERE p.department.id = :deptId")
    long countBatchesByDepartment(@Param("deptId") Long deptId);

    @Query("SELECT COUNT(s) FROM Section s JOIN s.batch b JOIN b.academicSession acs JOIN acs.program p WHERE p.department.id = :deptId")
    long countSectionsByDepartment(@Param("deptId") Long deptId);

    @Query("SELECT COUNT(st) FROM Student st JOIN st.program p WHERE p.department.id = :deptId")
    long countStudentsByDepartment(@Param("deptId") Long deptId);

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar "
            + "JOIN ar.section sec JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
            + "WHERE p.department.id = :deptId "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) AND (:endDate IS NULL OR ar.date <= :endDate)")
    long countRecordedByDepartmentAndDateRange(@Param("deptId") Long deptId,
                                               @Param("startDate") String startDate,
                                               @Param("endDate") String endDate);

    @Query("SELECT COUNT(ar) FROM AttendanceRecord ar "
            + "JOIN ar.section sec JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
            + "WHERE p.department.id = :deptId AND ar.isPresent = true "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) AND (:endDate IS NULL OR ar.date <= :endDate)")
    long countPresentByDepartmentAndDateRange(@Param("deptId") Long deptId,
                                              @Param("startDate") String startDate,
                                              @Param("endDate") String endDate);

    // ----- Section-wise -----

    @Query("SELECT sec.id AS sectionId, sec.sectionCode AS sectionCode, sec.name AS sectionName, "
            + "COUNT(ar) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM AttendanceRecord ar JOIN ar.section sec JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
            + "WHERE p.department.id = :deptId "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY sec.id, sec.sectionCode, sec.name")
    List<SectionAggregation> aggregateAttendanceBySection(@Param("deptId") Long deptId,
                                                          @Param("startDate") String startDate,
                                                          @Param("endDate") String endDate);

    @Query("SELECT sec.id AS sectionId, COUNT(st) AS studentCount "
            + "FROM Student st JOIN st.section sec "
            + "JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
            + "WHERE p.department.id = :deptId "
            + "GROUP BY sec.id")
    List<SectionStudentCount> countStudentsBySection(@Param("deptId") Long deptId);

    // ----- Subject-wise (department scope enforced via section chain, never Subject.department) -----

    @Query("SELECT sub.id AS subjectId, sub.code AS subjectCode, sub.name AS subjectName, "
            + "COUNT(ar) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM AttendanceRecord ar JOIN ar.section sec JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
            + "JOIN ar.subject sub "
            + "WHERE p.department.id = :deptId "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY sub.id, sub.code, sub.name")
    List<SubjectAggregation> aggregateAttendanceBySubject(@Param("deptId") Long deptId,
                                                          @Param("startDate") String startDate,
                                                          @Param("endDate") String endDate);

    // ----- Student-wise -----

    @Query("SELECT st.rollNumber AS rollNumber, st.name AS studentName, sec.name AS sectionName, "
            + "COUNT(ar) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM AttendanceRecord ar JOIN ar.student st JOIN ar.section sec "
            + "JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
            + "WHERE p.department.id = :deptId "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY st.id, st.rollNumber, st.name, sec.id, sec.name")
    List<StudentAggregation> aggregateAttendanceByStudent(@Param("deptId") Long deptId,
                                                          @Param("startDate") String startDate,
                                                          @Param("endDate") String endDate);

    // ----- Low attendance (fixed threshold: percentage < 75.0) -----

    @Query("SELECT st.rollNumber AS rollNumber, st.name AS studentName, sec.name AS sectionName, "
            + "COUNT(ar) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM AttendanceRecord ar JOIN ar.student st JOIN ar.section sec "
            + "JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
            + "WHERE p.department.id = :deptId "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY st.id, st.rollNumber, st.name, sec.id, sec.name "
            + "HAVING (SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) * 100.0 / COUNT(ar)) < 75.0")
    List<StudentAggregation> aggregateLowAttendanceStudents(@Param("deptId") Long deptId,
                                                            @Param("startDate") String startDate,
                                                            @Param("endDate") String endDate);

    // ----- Rollups (monthly | quarterly only; semester is NOT DERIVABLE, see plan 10) -----

    @Query("SELECT SUBSTRING(ar.date, 1, 7) AS period, "
            + "COUNT(ar) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM AttendanceRecord ar JOIN ar.section sec JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
            + "WHERE p.department.id = :deptId "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY period ORDER BY period")
    List<RollupAggregation> aggregateMonthlyRollup(@Param("deptId") Long deptId,
                                                   @Param("startDate") String startDate,
                                                   @Param("endDate") String endDate);

    String QUARTER_EXPR = "(CASE "
            + "WHEN CAST(SUBSTRING(ar.date, 6, 2) AS integer) <= 3 THEN 'Q1' "
            + "WHEN CAST(SUBSTRING(ar.date, 6, 2) AS integer) <= 6 THEN 'Q2' "
            + "WHEN CAST(SUBSTRING(ar.date, 6, 2) AS integer) <= 9 THEN 'Q3' "
            + "ELSE 'Q4' END)";

    @Query("SELECT CONCAT(CONCAT(SUBSTRING(ar.date, 1, 4), '-'), " + QUARTER_EXPR + ") AS period, "
            + "COUNT(ar) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM AttendanceRecord ar JOIN ar.section sec JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
            + "WHERE p.department.id = :deptId "
            + "AND (:startDate IS NULL OR ar.date >= :startDate) AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY period ORDER BY period")
    List<RollupAggregation> aggregateQuarterlyRollup(@Param("deptId") Long deptId,
                                                     @Param("startDate") String startDate,
                                                     @Param("endDate") String endDate);

    // ----- Audit logs (read-only, department-scoped, no internal IDs exposed) -----

    @Query("SELECT al FROM AttendanceAuditLog al JOIN al.attendance ar JOIN ar.section sec "
            + "JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
            + "WHERE p.department.id = :deptId "
            + "AND (:startDate IS NULL OR al.date >= :startDate) AND (:endDate IS NULL OR al.date <= :endDate) "
            + "ORDER BY al.updatedAt DESC")
    List<AttendanceAuditLog> findAuditLogsByDepartmentAndDateRange(@Param("deptId") Long deptId,
                                                                   @Param("startDate") String startDate,
                                                                   @Param("endDate") String endDate);

    // ----- Projections -----

    interface SectionAggregation {
        Long getSectionId();
        String getSectionCode();
        String getSectionName();
        Long getPresentCount();
        Long getTotalRecorded();
    }

    interface SectionStudentCount {
        Long getSectionId();
        Long getStudentCount();
    }

    interface SubjectAggregation {
        Long getSubjectId();
        String getSubjectCode();
        String getSubjectName();
        Long getPresentCount();
        Long getTotalRecorded();
    }

    interface StudentAggregation {
        String getRollNumber();
        String getStudentName();
        String getSectionName();
        Long getPresentCount();
        Long getTotalRecorded();
    }

    interface RollupAggregation {
        String getPeriod();
        Long getPresentCount();
        Long getTotalRecorded();
    }
}