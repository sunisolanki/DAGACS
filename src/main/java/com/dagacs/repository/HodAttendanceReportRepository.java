package com.dagacs.repository;

import com.dagacs.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only, department-scoped aggregation source for the Phase 3 HOD
 * attendance intelligence layer (overview, matrix, student detail, subject
 * detail, low-attendance breakdown).
 *
 * <p><b>Read-only by contract</b>, exactly like {@link HodAnalyticsRepository}
 * and {@link HodHierarchyRepository}: the interface extends {@code JpaRepository}
 * only so Spring Data can build the proxy, and no Phase 3 code path ever mutates
 * through it. Nothing in this interface modifies the frozen M4/M6.2/M7.1
 * queries.</p>
 *
 * <h3>Attendance semantics are NOT re-implemented here</h3>
 * <p>The <b>record</b> aggregates in this interface ({@code findMatrixCellsForStudents},
 * {@code findStudentSubjectCellsForContext},
 * {@code findSubjectStudentsForContext}) are retained for the frozen Phase 2
 * endpoints and are not used as a Phase 3 denominator. The Phase 3 reports
 * derive every {@code Total Classes} from the conducted-session queries
 * declared first below.</p>
 *
 * <h3>Authorization posture</h3>
 * <p>Every query carries {@code p.department.id = :deptId}, resolved exclusively
 * from the authenticated HOD via
 * {@link com.dagacs.security.AuthenticatedHodResolver}. No client-supplied id
 * is ever an authority; the service proves every supplied id belongs to that
 * department before these queries run, so a bypass would still return no data.
 * The department hop always travels {@code AcademicSession -> Program ->
 * Department} - never the redundant {@code Student.program} copy and never the
 * legacy nullable {@code Subject.department} column.</p>
 *
 * <h3>No N+1 by construction</h3>
 * <p>The matrix never loops over students. The whole page of cross-tab cells is
 * one grouped aggregate over {@code student.id IN :studentIds} and
 * {@code subject.id IN :subjectIds}, so a 50-student x 8-subject page is a single
 * row-set of at most 400 rows instead of 50 queries. The same holds for the
 * low-attendance subject breakdown, which reuses one context-wide grouped
 * aggregate rather than re-querying per student.</p>
 *
 * <h3>CORRECTED DENOMINATOR: conducted sessions, not attendance records</h3>
 * <p><b>Total Classes is the number of conducted {@code AttendanceSession}s.</b>
 * The attendance-record count is <b>never</b> the denominator, and an unmarked
 * (missing) mark is <b>never</b> removed from it. This is the whole point of the
 * four queries below:</p>
 * <pre>
 *   Conducted sessions      = 10
 *   Present = 7, Absent = 2, Unmarked = 1
 *
 *   Total Classes = 10            (the conducted sessions)
 *   Present      = 7
 *   Attendance   = 7 / 10 = 70%  (NOT 7/9, and never 7/attendance-record-count)
 * </pre>
 * <p>The corollary that drives every design choice here: a student with
 * <b>zero</b> attendance records still has the full conducted-session
 * denominator, so they report {@code 0 / 10 = 0%} rather than disappearing or
 * reporting a meaningless {@code 0 / 0}.</p>
 *
 * <p>Because {@code Present} is always counted through a join to the conducted
 * session set, the numerator is a strict subset of the denominator by
 * construction - a PRESENT mark on a non-conducted session can never be counted,
 * and a conducted session without a mark is always in the denominator.</p>
 *
 * <p>Note the two deliberately different group-by shapes:</p>
 * <ul>
 *   <li><b>conducted per subject / per student</b> start from
 *       {@code AttendanceSession}, so they include sessions nobody was marked
 *       in;</li>
 *   <li><b>present per student-subject</b> starts from {@code AttendanceRecord}
 *       but <em>joins the conducted session</em> and filters on the session's
 *       own date and status.</li>
 * </ul>
 */
@Repository
public interface HodAttendanceReportRepository extends JpaRepository<AttendanceRecord, Long> {

    // ═══════════════════════════════════════════════════════════════════════
    // CANONICAL PHASE 3 AGGREGATION LAYER
    //
    // Total Classes = conducted AttendanceSessions.
    // Present      = PRESENT records attached to those conducted sessions.
    // Every Phase 3 report reads its numbers from these four queries and from
    // nothing else; there is no second denominator anywhere.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The authoritative {@code Total Classes} per subject column: how many
     * classes were actually conducted for that subject, in that section, in the
     * selected date range.
     *
     * <p>Scoped through {@code AttendanceSession.sectionEntity -> Section ->
     * Batch -> AcademicSession -> Program -> Department}, the same authoritative
     * chain every other scoped query uses, so a class of another department,
     * program, academic session or section can never enter a denominator. Batch
     * -mode sessions have no section and are correctly excluded from a
     * section-scoped report.</p>
     *
     * <p>Only {@code status = 'CONDUCTED'} counts: a SCHEDULED session nobody
     * was marked in, and a CANCELLED session, are not classes. A conducted
     * session is counted <b>whether or not anybody was marked</b>, which is
     * exactly what keeps unmarked students in the denominator.</p>
     *
     * <p>One row per subject, so it bounds the whole matrix regardless of how
     * many students are on the page - the reason the matrix needs no per-student
     * session query at all.</p>
     */
    @Query("SELECT s.subjectEntity.id AS subjectId, COUNT(DISTINCT s.id) AS sessionCount "
            + "FROM AttendanceSession s "
            + "JOIN s.sectionEntity sec "
            + "WHERE s.status = 'CONDUCTED' "
            + "  AND sec.batch.academicSession.program.department.id = :deptId "
            + "  AND s.subjectEntity.id IN :subjectIds "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "  AND (:startDate IS NULL OR s.date >= :startDate) "
            + "  AND (:endDate IS NULL OR s.date <= :endDate) "
            + "GROUP BY s.subjectEntity.id")
    List<SubjectSessionProjection> findConductedSessionCountsBySubject(
            @Param("deptId") Long deptId,
            @Param("subjectIds") List<Long> subjectIds,
            @Param("sectionId") Long sectionId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * The authoritative {@code Total Classes} per student: how many of the
     * context's conducted classes that student was expected to attend.
     *
     * <p>Reached from the <b>student</b>, through the student's own
     * {@code section}, so each student is measured against the classes actually
     * conducted for their own section. A student whose section has no conducted
     * classes reports {@code 0}, and the percentage is then reported as
     * unavailable rather than as 0%.</p>
     *
     * <p>The {@code LEFT JOIN} is essential: a student with no attendance record
     * at all still produces a row, so the zero-record student of the
     * specification is measured, not skipped.</p>
     */
    @Query("SELECT st.id AS studentId, COUNT(DISTINCT s.id) AS sessionCount "
            + "FROM Student st JOIN st.academicSession acs JOIN acs.program p "
            + "LEFT JOIN st.section sec LEFT JOIN st.semester sem "
            + "LEFT JOIN AttendanceSession s ON s.sectionEntity.id = sec.id "
            + "     AND s.status = 'CONDUCTED' "
            + "     AND s.subjectEntity.id IN :subjectIds "
            + "     AND (:startDate IS NULL OR s.date >= :startDate) "
            + "     AND (:endDate IS NULL OR s.date <= :endDate) "
            + "WHERE p.department.id = :deptId "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR sem.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "GROUP BY st.id")
    List<StudentSessionProjection> findConductedSessionCountsByStudent(
            @Param("deptId") Long deptId,
            @Param("subjectIds") List<Long> subjectIds,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * {@code Present} per student and per subject, counted only against classes
     * that were actually conducted.
     *
     * <p>The join to {@code AttendanceSession} is the crux: it restricts the
     * numerator to conducted sessions and makes the <b>session's</b> date the
     * filter, so {@code Present} is always a subset of the denominator and a
     * date range can never include a record whose class was excluded.</p>
     *
     * <p>Absent records and missing (unmarked) sessions are both simply absent
     * from this result - they remain in the denominator supplied by
     * {@link #findConductedSessionCountsBySubject} /
     * {@link #findConductedSessionCountsByStudent}, which is how "unmarked
     * counts as absent" is realised without a second status rule.</p>
     */
    @Query("SELECT ar.student.id AS studentId, ar.subject.id AS subjectId, "
            + "COUNT(ar.id) AS presentCount "
            + "FROM AttendanceRecord ar JOIN ar.session s "
            + "JOIN ar.student st JOIN st.academicSession acs JOIN acs.program p "
            + "LEFT JOIN st.section sec "
            + "WHERE s.status = 'CONDUCTED' "
            + "  AND ar.isPresent = true "
            + "  AND p.department.id = :deptId "
            + "  AND ar.student.id IN :studentIds "
            + "  AND ar.subject.id IN :subjectIds "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR st.semester.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "  AND (:startDate IS NULL OR s.date >= :startDate) "
            + "  AND (:endDate IS NULL OR s.date <= :endDate) "
            + "GROUP BY ar.student.id, ar.subject.id")
    List<MatrixCellProjection> findPresentCountsByStudentAndSubject(
            @Param("deptId") Long deptId,
            @Param("studentIds") List<Long> studentIds,
            @Param("subjectIds") List<Long> subjectIds,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * {@code Present} per student across the whole context, on the same
     * conducted-session basis as the per-subject query.
     *
     * <p>One row per student, so the overview and the low-attendance report stay
     * bounded by the student count rather than by students x subjects.</p>
     */
    @Query("SELECT ar.student.id AS studentId, COUNT(ar.id) AS presentCount "
            + "FROM AttendanceRecord ar JOIN ar.session s "
            + "JOIN ar.student st JOIN st.academicSession acs JOIN acs.program p "
            + "LEFT JOIN st.section sec "
            + "WHERE s.status = 'CONDUCTED' "
            + "  AND ar.isPresent = true "
            + "  AND p.department.id = :deptId "
            + "  AND ar.subject.id IN :subjectIds "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR st.semester.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "  AND (:startDate IS NULL OR s.date >= :startDate) "
            + "  AND (:endDate IS NULL OR s.date <= :endDate) "
            + "GROUP BY ar.student.id")
    List<StudentPresentProjection> findPresentCountsByStudent(
            @Param("deptId") Long deptId,
            @Param("subjectIds") List<Long> subjectIds,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * {@code Present} per subject across the whole context.
     *
     * <p>Feeds the overview's subject table. One row per subject, so the
     * department-wide case stays as cheap as the pre-existing department-wide
     * subject report.</p>
     */
    @Query("SELECT ar.subject.id AS subjectId, COUNT(ar.id) AS presentCount "
            + "FROM AttendanceRecord ar JOIN ar.session s "
            + "JOIN ar.student st JOIN st.academicSession acs JOIN acs.program p "
            + "LEFT JOIN st.section sec "
            + "WHERE s.status = 'CONDUCTED' "
            + "  AND ar.isPresent = true "
            + "  AND p.department.id = :deptId "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR st.semester.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "  AND (:startDate IS NULL OR s.date >= :startDate) "
            + "  AND (:endDate IS NULL OR s.date <= :endDate) "
            + "GROUP BY ar.subject.id")
    List<SubjectPresentProjection> findPresentCountsBySubject(
            @Param("deptId") Long deptId,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    // ── Dynamic subject columns ───────────────────────────────────────────

    /**
     * The subject columns of an academic context.
     *
     * <p>Derived from the real academic relationships only - never from
     * "all subjects of the department":</p>
     * <ul>
     *   <li>every {@code SubjectOffering} of the selected semester, and</li>
     *   <li>every subject with an actual teaching assignment
     *       ({@code TeacherSubjectSectionAssignment}) to the selected
     *       section.</li>
     * </ul>
     * <p>Both arms are department- and program-scoped, so a subject from another
     * department, program, academic session or semester can never become a
     * column. When both a semester and a section are supplied the result is the
     * semester's offered subjects; the assignment arm is the safety net that
     * keeps a subject actually taught to the section from ever being hidden.</p>
     *
     * <p>{@code SELECT DISTINCT} on the offering id guarantees
     * <b>no duplicate subject column</b>, even when a subject is both offered
     * and assigned. The ordering is stable (code, then name, then id) so the
     * column order is reproducible between requests and between the API and the
     * Excel export.</p>
     */
    @Query("SELECT DISTINCT so.subject.id AS subjectId, so.id AS subjectOfferingId, "
            + "so.subject.code AS subjectCode, so.subject.name AS subjectName "
            + "FROM SubjectOffering so "
            + "JOIN so.semester sem JOIN sem.academicSession acs JOIN acs.program p "
            + "WHERE p.department.id = :deptId "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND ((:semesterId IS NULL OR sem.id = :semesterId) "
            + "       OR EXISTS (SELECT 1 FROM TeacherSubjectSectionAssignment a "
            + "           WHERE a.section.id = :sectionId "
            + "             AND a.subjectOffering.id = so.id)) "
            + "ORDER BY so.subject.code, so.subject.name, so.subject.id")
    List<SubjectColumnProjection> findSubjectColumnsForContext(
            @Param("deptId") Long deptId,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId);

    // ── Student population ────────────────────────────────────────────────

    /**
     * Enrolled students of an academic context.
     *
     * <p>Based on {@code Student} - <b>not</b> on {@code AttendanceRecord} - so
     * a student with zero attendance records is still part of the population and
     * therefore still appears in the matrix as {@code 0 / 0}. The LEFT JOINs on
     * section and semester keep a student whose optional foreign keys are null
     * visible instead of dropping them.</p>
     *
     * <p>Grouping by the student primary key guarantees <b>no duplicate student
     * row</b>, which is what makes the cross-tab positionally safe.</p>
     */
    @Query("SELECT st.id AS studentId, st.enrollmentNumber AS enrollmentNumber, "
            + "st.rollNumber AS rollNumber, st.name AS studentName, sec.name AS sectionName "
            + "FROM Student st JOIN st.academicSession acs JOIN acs.program p "
            + "LEFT JOIN st.section sec LEFT JOIN st.semester sem "
            + "WHERE p.department.id = :deptId "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR sem.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "ORDER BY st.enrollmentNumber, st.name, st.id")
    List<MatrixStudentProjection> findStudentsForContext(
            @Param("deptId") Long deptId,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId);

    @Query("SELECT COUNT(DISTINCT st.id) FROM Student st "
            + "JOIN st.academicSession acs JOIN acs.program p "
            + "LEFT JOIN st.section sec LEFT JOIN st.semester sem "
            + "WHERE p.department.id = :deptId "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR sem.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId)")
    long countStudentsForContext(
            @Param("deptId") Long deptId,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId);

    // ── Record-based aggregates ───────────────────────────────────────────
    //
    // NOT a Phase 3 denominator. These measure what was *marked*, which is a
    // different question from "how many classes were conducted". They remain
    // available for the frozen Phase 2 endpoints and are deliberately not
    // consumed by the Phase 3 reports.

    /**
     * The cross-tab cells for one page of students against the context's subject
     * columns - <b>one</b> grouped query for the whole page.
     *
     * <p>Measured in attendance <em>records</em>, so it is NOT the Phase 3
     * denominator. Retained for the frozen Phase 2 paths only.</p>
     *
     * <p>The academic context and the date range are combined with {@code AND}
     * only. There is deliberately no {@code OR} between the context predicates
     * and the date predicates, so a date filter can never widen the context and
     * a context can never leak rows outside the selected period.</p>
     *
     * <p>The join goes through {@code ar.student -> Student.academicSession ->
     * AcademicSession.program}, so the scope is decided by the enrolled
     * student's academic placement (authoritative) rather than by the
     * denormalised {@code attendance_records.section} snapshot.</p>
     *
     * <p>Only students that actually have records appear, which is precisely why
     * this query cannot serve as the Phase 3 denominator.</p>
     */
    @Query("SELECT ar.student.id AS studentId, ar.subject.id AS subjectId, "
            + "COUNT(ar.id) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM AttendanceRecord ar JOIN ar.student st "
            + "JOIN st.academicSession acs JOIN acs.program p LEFT JOIN st.section sec "
            + "WHERE p.department.id = :deptId "
            + "  AND ar.student.id IN :studentIds "
            + "  AND ar.subject.id IN :subjectIds "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR st.semester.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "  AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "  AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY ar.student.id, ar.subject.id")
    List<MatrixCellProjection> findMatrixCellsForStudents(
            @Param("deptId") Long deptId,
            @Param("studentIds") List<Long> studentIds,
            @Param("subjectIds") List<Long> subjectIds,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    /**
     * The same per-(student, subject) aggregate for the <b>whole</b> context.
     *
     * <p>Used for the two reports that need every student of the context rather
     * than one page: the exact "sort by overall attendance" ordering, and the
     * low-attendance per-subject breakdown. Reusing one query for both keeps the
     * request at a fixed number of round trips instead of a per-student loop.</p>
     */
    @Query("SELECT ar.student.id AS studentId, ar.subject.id AS subjectId, "
            + "COUNT(ar.id) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM AttendanceRecord ar JOIN ar.student st "
            + "JOIN st.academicSession acs JOIN acs.program p LEFT JOIN st.section sec "
            + "WHERE p.department.id = :deptId "
            + "  AND ar.subject.id IN :subjectIds "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR st.semester.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "  AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "  AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY ar.student.id, ar.subject.id")
    List<MatrixCellProjection> findStudentSubjectCellsForContext(
            @Param("deptId") Long deptId,
            @Param("subjectIds") List<Long> subjectIds,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    // ── Record-backed classes conducted ───────────────────────────────────

    /**
     * Classes conducted for a context, using the M7.1 record-backed definition
     * {@code COUNT(DISTINCT attendance_records.session_id)}.
     *
     * <p><b>Not a Phase 3 denominator.</b> It counts sessions that produced at
     * least one mark, so a class nobody was marked in is missing from it - the
     * exact failure the conducted-session queries exist to fix. Retained for
     * the frozen Phase 2 endpoints.</p>
     */
    @Query("SELECT COUNT(DISTINCT ar.session.id) "
            + "FROM AttendanceRecord ar JOIN ar.student st "
            + "JOIN st.academicSession acs JOIN acs.program p LEFT JOIN st.section sec "
            + "WHERE p.department.id = :deptId "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR st.semester.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "  AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "  AND (:endDate IS NULL OR ar.date <= :endDate)")
    long countClassesConductedForContext(
            @Param("deptId") Long deptId,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    @Query("SELECT ar.subject.id AS subjectId, COUNT(DISTINCT ar.session.id) AS sessionCount "
            + "FROM AttendanceRecord ar JOIN ar.student st "
            + "JOIN st.academicSession acs JOIN acs.program p LEFT JOIN st.section sec "
            + "WHERE p.department.id = :deptId "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR st.semester.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "  AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "  AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY ar.subject.id")
    List<SubjectSessionProjection> findClassesConductedPerSubjectForContext(            @Param("deptId") Long deptId,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    // ── Subject detail ────────────────────────────────────────────────────

    /**
     * Per-student attendance for one subject inside an academic context.
     *
     * <p>Only students who actually have marks in this subject appear; the
     * service unions them with the enrolled student population so a student
     * with no marks is still listed.</p>
     */
    @Query("SELECT ar.student.id AS studentId, ar.student.enrollmentNumber AS enrollmentNumber, "
            + "ar.student.rollNumber AS rollNumber, ar.student.name AS studentName, "
            + "COUNT(ar.id) AS totalRecorded, "
            + "SUM(CASE WHEN ar.isPresent = true THEN 1 ELSE 0 END) AS presentCount "
            + "FROM AttendanceRecord ar JOIN ar.student st "
            + "JOIN st.academicSession acs JOIN acs.program p LEFT JOIN st.section sec "
            + "WHERE p.department.id = :deptId "
            + "  AND ar.subject.id = :subjectId "
            + "  AND (:sessionId IS NULL OR acs.id = :sessionId) "
            + "  AND (:programId IS NULL OR acs.program.id = :programId) "
            + "  AND (:semesterId IS NULL OR st.semester.id = :semesterId) "
            + "  AND (:sectionId IS NULL OR sec.id = :sectionId) "
            + "  AND (:startDate IS NULL OR ar.date >= :startDate) "
            + "  AND (:endDate IS NULL OR ar.date <= :endDate) "
            + "GROUP BY ar.student.id, ar.student.enrollmentNumber, "
            + "         ar.student.rollNumber, ar.student.name "
            + "ORDER BY ar.student.enrollmentNumber, ar.student.name")
    List<SubjectStudentProjection> findSubjectStudentsForContext(
            @Param("deptId") Long deptId,
            @Param("subjectId") Long subjectId,
            @Param("sessionId") Long sessionId,
            @Param("programId") Long programId,
            @Param("semesterId") Long semesterId,
            @Param("sectionId") Long sectionId,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate);

    // ── Projections ───────────────────────────────────────────────────────

    /** Conducted classes of one subject: the {@code Total Classes} of a column. */
    interface SubjectSessionProjection {
        Long getSubjectId();

        Long getSessionCount();
    }

    /** Conducted classes one student was expected to attend. */
    interface StudentSessionProjection {
        Long getStudentId();

        Long getSessionCount();
    }

    /** Present marks of one student. */
    interface StudentPresentProjection {
        Long getStudentId();

        Long getPresentCount();
    }

    /** Present marks of one subject across the context. */
    interface SubjectPresentProjection {
        Long getSubjectId();

        Long getPresentCount();
    }

    interface SubjectColumnProjection {
        Long getSubjectId();

        Long getSubjectOfferingId();

        String getSubjectCode();

        String getSubjectName();
    }

    interface MatrixStudentProjection {
        Long getStudentId();

        String getEnrollmentNumber();

        String getRollNumber();

        String getStudentName();

        String getSectionName();
    }

    interface MatrixCellProjection {
        Long getStudentId();

        Long getSubjectId();

        Long getPresentCount();

        Long getTotalRecorded();
    }

    interface SubjectStudentProjection {
        Long getStudentId();

        String getEnrollmentNumber();

        String getRollNumber();

        String getStudentName();

        Long getPresentCount();

        Long getTotalRecorded();
    }
}
