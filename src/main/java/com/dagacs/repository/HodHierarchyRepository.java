package com.dagacs.repository;

import com.dagacs.entity.SubjectOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only, department-scoped query source for the HOD academic hierarchy and
 * the academic-context-scoped HOD attendance aggregates.
 *
 * <p><b>Read-only by contract</b> (same stance as {@link HodAnalyticsRepository}):
 * the interface extends {@code JpaRepository} only so Spring Data can build the
 * proxy; no hierarchy code path ever mutates through it.</p>
 *
 * <h3>Authoritative academic relationships used</h3>
 * <ul>
 *   <li>{@code Program.department} — the only department-scope hop.</li>
 *   <li>{@code AcademicSession.program} — a session belongs to exactly one
 *       program, so B.Tech and M.Tech sessions can never be conflated.</li>
 *   <li>{@code Semester.academicSession} — a semester belongs to exactly one
 *       session (hence one program).</li>
 *   <li>{@code Section.batch.academicSession} — a section's program is its
 *       batch's session's program.</li>
 *   <li>{@code SubjectOffering.semester.subject} — the only authoritative
 *       statement that a subject is taught in a given academic context.</li>
 * </ul>
 *
 * <p><b>There is no {@code Semester -> Section} foreign key in this schema.</b>
 * Section membership within a semester is therefore <em>derived</em> from the
 * union of two real relationships: students enrolled in that semester
 * ({@code Student.section + Student.semester}) and teachers actually assigned
 * to teach a subject offered in that semester to that section
 * ({@code TeacherSubjectSectionAssignment.section + subjectOffering.semester}).
 * No artificial relationship is introduced. {@code SELECT DISTINCT} keeps a
 * section that satisfies both paths from appearing twice.</p>
 *
 * <p>Program scoping always travels through {@code academicSession.program}
 * (never {@code Student.program}, which is a redundant copy) and the legacy
 * nullable {@code Subject.department} VARCHAR is never used as a scope.</p>
 */
@Repository
public interface HodHierarchyRepository extends JpaRepository<SubjectOffering, Long> {

    // ── Hierarchy: cascade roots ──────────────────────────────────────────

    @Query("SELECT p.id AS id, p.name AS name, p.code AS code "
            + "FROM Program p WHERE p.department.id = :deptId ORDER BY p.name")
    List<OptionProjection> findProgramOptions(@Param("deptId") Long deptId);

    @Query("SELECT s.id AS id, s.name AS name, s.code AS code, "
            + "s.program.id AS programId, s.program.name AS programName "
            + "FROM AcademicSession s WHERE s.program.department.id = :deptId ORDER BY s.name")
    List<OptionProjection> findAcademicSessionOptions(@Param("deptId") Long deptId);

    // ── Hierarchy: cascade children ───────────────────────────────────────

    @Query("SELECT sem.id AS id, sem.name AS name, sem.code AS code, "
            + "(SELECT COUNT(st) FROM Student st WHERE st.semester = sem) AS studentCount "
            + "FROM Semester sem WHERE sem.academicSession.id = :sessionId ORDER BY sem.name")
    List<OptionProjection> findSemesterOptions(@Param("sessionId") Long sessionId);

    /**
     * Sections of one academic session, optionally restricted to one semester.
     *
     * <p>With {@code semesterId} the semester membership is derived from the
     * union of enrolment and teaching assignments (see the class javadoc). The
     * program is deliberately not a predicate: the academic session already
     * determines it, and the service validates that the requested program
     * matches the session's program instead.</p>
     */
    @Query("SELECT DISTINCT sec.id AS id, sec.name AS name, sec.sectionCode AS code, "
            + "sec.batch.id AS batchId, sec.batch.name AS batchName, "
            + "(SELECT COUNT(st) FROM Student st WHERE st.section = sec "
            + "   AND (:semesterId IS NULL OR st.semester.id = :semesterId)) AS studentCount "
            + "FROM Section sec "
            + "WHERE sec.batch.academicSession.id = :sessionId "
            + "AND (:semesterId IS NULL "
            + "     OR EXISTS (SELECT 1 FROM Student st2 WHERE st2.section = sec "
            + "                AND st2.semester.id = :semesterId) "
            + "     OR EXISTS (SELECT 1 FROM TeacherSubjectSectionAssignment a "
            + "                WHERE a.section = sec "
            + "                  AND a.subjectOffering.semester.id = :semesterId)) "
            + "ORDER BY sec.name")
    List<OptionProjection> findSectionOptions(@Param("sessionId") Long sessionId,
                                              @Param("semesterId") Long semesterId);

    @Query("SELECT so.subject.id AS id, so.subject.code AS code, so.subject.name AS name, "
            + "so.semester.id AS semesterId, so.semester.name AS semesterName, "
            + "so.semester.academicSession.program.id AS programId, "
            + "so.semester.academicSession.program.name AS programName "
            + "FROM SubjectOffering so WHERE so.semester.id = :semesterId "
            + "ORDER BY so.subject.name")
    List<SubjectProjection> findSubjectOptions(@Param("semesterId") Long semesterId);

    @Query("SELECT a.subjectOffering.subject.id AS subjectId, a.teacher.fullName AS teacherName "
            + "FROM TeacherSubjectSectionAssignment a "
            + "WHERE a.section.id = :sectionId AND a.subjectOffering.semester.id = :semesterId "
            + "ORDER BY a.teacher.fullName")
    List<FacultyProjection> findFacultyForSectionAndSemester(
            @Param("sectionId") Long sectionId, @Param("semesterId") Long semesterId);

    // ── Ownership checks (dept scope + combination validity) ──────────────

    @Query("SELECT COUNT(p) FROM Program p WHERE p.id = :id AND p.department.id = :deptId")
    long countProgramInDepartment(@Param("id") Long id, @Param("deptId") Long deptId);

    @Query("SELECT COUNT(s) FROM AcademicSession s "
            + "WHERE s.id = :id AND s.program.department.id = :deptId")
    long countAcademicSessionInDepartment(@Param("id") Long id, @Param("deptId") Long deptId);

    @Query("SELECT COUNT(sem) FROM Semester sem "
            + "WHERE sem.id = :id AND sem.academicSession.program.department.id = :deptId")
    long countSemesterInDepartment(@Param("id") Long id, @Param("deptId") Long deptId);

    @Query("SELECT COUNT(sec) FROM Section sec "
            + "WHERE sec.id = :id AND sec.batch.academicSession.program.department.id = :deptId")
    long countSectionInDepartment(@Param("id") Long id, @Param("deptId") Long deptId);

    /**
     * Phase 3 (additive): a student inside the exact academic context of a
     * request.
     *
     * <p>The department hop is {@code Student.academicSession ->
     * AcademicSession.program -> Department}, the same authoritative chain every
     * scoped aggregate already uses - never the redundant {@code Student.program}
     * copy, which could disagree with the enrolled session. Each supplied level
     * is an additional {@code AND}, so a student from another session, program,
     * semester or section can never satisfy the count even when the caller
     * holds a valid id for it.</p>
     */
    @Query("SELECT COUNT(st) FROM Student st JOIN st.academicSession acs JOIN acs.program p "
            + "LEFT JOIN st.section sec LEFT JOIN st.semester sem "
            + "WHERE st.id = :id AND p.department.id = :deptId "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR sem.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId)")
    long countStudentInContext(@Param("id") Long id,
                               @Param("deptId") Long deptId,
                               @Param("sessionId") Long sessionId,
                               @Param("programId") Long programId,
                               @Param("semesterId") Long semesterId,
                               @Param("sectionId") Long sectionId);

    /**
     * Phase 3 (additive): a subject inside the exact academic context of a
     * request.
     *
     * <p>The predicate is deliberately byte-for-byte the same shape as the
     * dynamic-column query in {@code HodAttendanceReportRepository}, so the
     * subjects a HOD is allowed to open in isolation are exactly the subjects
     * that can appear as matrix columns. A subject is in context when it is
     * offered in the selected semester, or when it actually has a teaching
     * assignment to the selected section - both scoped through
     * {@code Semester -> AcademicSession -> Program -> Department}.</p>
     */
    @Query("SELECT COUNT(DISTINCT so.subject.id) FROM SubjectOffering so "
            + "JOIN so.semester sem JOIN sem.academicSession acs JOIN acs.program p "
            + "WHERE so.subject.id = :id AND p.department.id = :deptId "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND ((:semesterId IS NULL OR sem.id = :semesterId) "
            + "       OR EXISTS (SELECT 1 FROM TeacherSubjectSectionAssignment a "
            + "           WHERE a.section.id = :sectionId "
            + "             AND a.subjectOffering.id = so.id))")
    long countSubjectInContext(@Param("id") Long id,
                               @Param("deptId") Long deptId,
                               @Param("sessionId") Long sessionId,
                               @Param("programId") Long programId,
                               @Param("semesterId") Long semesterId,
                               @Param("sectionId") Long sectionId);

    // ── Scoped aggregates (context filters are all optional) ──────────────

    /**
     * Section-wise attendance restricted to an academic context.
     *
     * <p>Based on {@code Student} with a {@code LEFT JOIN AttendanceRecord} so a
     * student with no recorded attendance is still counted. Percentage is
     * computed by the caller with the shared {@code present/total*100} helper
     * and is null when nothing was recorded — identical to the frozen queries.</p>
     */
    @Query("SELECT sec.id AS sectionId, sec.sectionCode AS sectionCode, sec.name AS sectionName, "
            + "COUNT(DISTINCT st.id) AS studentCount, "
            + "COUNT(ar.id) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM Student st JOIN st.section sec JOIN sec.batch b "
            + "JOIN st.academicSession acs JOIN acs.program p "
            + "LEFT JOIN AttendanceRecord ar ON ar.student = st "
            + "    AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "    AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "WHERE p.department.id = :deptId "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR st.semester.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "GROUP BY sec.id, sec.sectionCode, sec.name ORDER BY sec.name")
    List<SectionContextProjection> aggregateSectionAttendanceForContext(
            @Param("deptId") Long deptId,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    @Query("SELECT st.id AS studentId, st.rollNumber AS rollNumber, "
            + "st.enrollmentNumber AS enrollmentNumber, st.name AS studentName, "
            + "sec.name AS sectionName, sem.name AS semesterName, "
            + "acs.name AS academicSessionName, acs.program.name AS programName, "
            + "COUNT(ar.id) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM Student st JOIN st.academicSession acs JOIN acs.program p "
            + "LEFT JOIN st.section sec LEFT JOIN st.semester sem "
            + "LEFT JOIN AttendanceRecord ar ON ar.student = st "
            + "    AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "    AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "WHERE p.department.id = :deptId "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR sem.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "GROUP BY st.id, st.rollNumber, st.enrollmentNumber, st.name, "
            + "         sec.name, sem.name, acs.name, acs.program.name "
            + "ORDER BY st.enrollmentNumber, st.name")
    List<StudentContextProjection> aggregateStudentAttendanceForContext(
            @Param("deptId") Long deptId,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * Context-scoped students below the fixed 75% threshold.
     *
     * <p>The HAVING expression is byte-identical in semantics to the frozen
     * {@code aggregateLowAttendanceStudents} query, so the threshold and the
     * denominator semantics are not re-implemented here.</p>
     */
    @Query("SELECT st.id AS studentId, st.rollNumber AS rollNumber, "
            + "st.enrollmentNumber AS enrollmentNumber, st.name AS studentName, "
            + "sec.name AS sectionName, sem.name AS semesterName, "
            + "acs.name AS academicSessionName, acs.program.name AS programName, "
            + "COUNT(ar.id) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM Student st JOIN st.academicSession acs JOIN acs.program p "
            + "LEFT JOIN st.section sec LEFT JOIN st.semester sem "
            + "LEFT JOIN AttendanceRecord ar ON ar.student = st "
            + "    AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "    AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "WHERE p.department.id = :deptId "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR sem.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "GROUP BY st.id, st.rollNumber, st.enrollmentNumber, st.name, "
            + "         sec.name, sem.name, acs.name, acs.program.name "
            + "HAVING (SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) * 100.0 "
            + "        / COUNT(ar.id)) < 75.0 "
            + "ORDER BY st.enrollmentNumber, st.name")
    List<StudentContextProjection> aggregateLowAttendanceForContext(
            @Param("deptId") Long deptId,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * Subject-wise attendance for an academic context.
     *
     * <p>Based on {@code SubjectOffering} (never a bare {@code SELECT * FROM
     * subject}) so the columns are exactly the subjects actually offered in the
     * selected semester, and never every subject of the department.</p>
     */
    @Query("SELECT sub.id AS subjectId, sub.code AS subjectCode, sub.name AS subjectName, "
            + "sem.name AS semesterName, acs.program.name AS programName, "
            + "COUNT(ar.id) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM SubjectOffering so JOIN so.subject sub JOIN so.semester sem "
            + "JOIN sem.academicSession acs JOIN acs.program p "
            + "LEFT JOIN AttendanceRecord ar ON ar.subject = sub "
            + "    AND (:sectionId IS NULL OR ar.section.id = :sectionId) "
            + "    AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "    AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "WHERE p.department.id = :deptId "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR sem.id = :semesterId) "
            + "GROUP BY sub.id, sub.code, sub.name, sem.name, acs.program.name "
            + "ORDER BY sub.name")
    List<SubjectContextProjection> aggregateSubjectAttendanceForContext(
            @Param("deptId") Long deptId,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    // ── Projections ───────────────────────────────────────────────────────

    /**
     * Phase 3 (additive): the academic context a single section determines.
     *
     * <p>A section belongs to exactly one batch, a batch to exactly one academic
     * session, and a session to exactly one program, so this single row is the
     * authoritative context of a section. It is used only to label a report with
     * the context it was computed for - never as a scope predicate, because the
     * scope predicates are always the caller's own supplied ids.</p>
     */
    @Query("SELECT sec.id AS sectionId, sec.name AS sectionName, "
            + "acs.id AS sessionId, acs.name AS sessionName, "
            + "p.id AS programId, p.name AS programName "
            + "FROM Section sec JOIN sec.batch b JOIN b.academicSession acs JOIN acs.program p "
            + "WHERE sec.id = :sectionId AND p.department.id = :deptId")
    List<ContextSectionProjection> findContextForSection(
            @Param("sectionId") Long sectionId, @Param("deptId") Long deptId);

    /**
     * Phase 3 (additive): the academic context a single semester determines
     * (semester -> academic session -> program -> department).
     */
    @Query("SELECT sem.id AS semesterId, sem.name AS semesterName, "
            + "acs.id AS sessionId, acs.name AS sessionName, "
            + "p.id AS programId, p.name AS programName "
            + "FROM Semester sem JOIN sem.academicSession acs JOIN acs.program p "
            + "WHERE sem.id = :semesterId AND p.department.id = :deptId")
    List<ContextSemesterProjection> findContextForSemester(
            @Param("semesterId") Long semesterId, @Param("deptId") Long deptId);

    interface ContextSectionProjection {
        Long getSectionId();

        String getSectionName();

        Long getSessionId();

        String getSessionName();

        Long getProgramId();

        String getProgramName();
    }

    interface ContextSemesterProjection {
        Long getSemesterId();

        String getSemesterName();

        Long getSessionId();

        String getSessionName();

        Long getProgramId();

        String getProgramName();
    }

    interface OptionProjection {
        Long getId();

        String getName();

        String getCode();

        Long getProgramId();

        String getProgramName();

        Long getBatchId();

        String getBatchName();

        Long getStudentCount();
    }

    interface SubjectProjection {
        Long getId();

        String getCode();

        String getName();

        Long getSemesterId();

        String getSemesterName();

        Long getProgramId();

        String getProgramName();
    }

    interface FacultyProjection {
        Long getSubjectId();

        String getTeacherName();
    }

    interface SectionContextProjection {
        Long getSectionId();

        String getSectionCode();

        String getSectionName();

        Long getStudentCount();

        Long getPresentCount();

        Long getTotalRecorded();
    }

    interface StudentContextProjection {
        Long getStudentId();

        String getRollNumber();

        String getEnrollmentNumber();

        String getStudentName();

        String getSectionName();

        String getSemesterName();

        String getAcademicSessionName();

        String getProgramName();

        Long getPresentCount();

        Long getTotalRecorded();
    }

    interface SubjectContextProjection {
        Long getSubjectId();

        String getSubjectCode();

        String getSubjectName();

        String getSemesterName();

        String getProgramName();

        Long getPresentCount();

        Long getTotalRecorded();
    }
}
