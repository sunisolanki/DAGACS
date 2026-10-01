package com.dagacs.service;

import com.dagacs.dto.HodAttendanceContextDTO;
import com.dagacs.dto.HodAttendanceDistributionDTO;
import com.dagacs.dto.HodAttendanceMatrixCellDTO;
import com.dagacs.dto.HodAttendanceMatrixColumnDTO;
import com.dagacs.dto.HodAttendanceMatrixDTO;
import com.dagacs.dto.HodAttendanceMatrixRowDTO;
import com.dagacs.dto.HodAttendanceOverviewDTO;
import com.dagacs.dto.HodAttendanceSubjectSummaryDTO;
import com.dagacs.dto.HodLowAttendanceReportDTO;
import com.dagacs.dto.HodLowAttendanceStudentDTO;
import com.dagacs.dto.HodLowAttendanceSubjectDTO;
import com.dagacs.dto.HodStudentAttendanceDetailDTO;
import com.dagacs.dto.HodStudentSubjectAttendanceDTO;
import com.dagacs.dto.HodSubjectAttendanceDetailDTO;
import com.dagacs.dto.HodSubjectAttendanceDetailRowDTO;
import com.dagacs.entity.Teacher;
import com.dagacs.exception.AuthErrorCode;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.HodAttendanceReportRepository;
import com.dagacs.repository.HodAttendanceReportRepository.MatrixStudentProjection;
import com.dagacs.repository.HodAttendanceReportRepository.StudentPresentProjection;
import com.dagacs.repository.HodAttendanceReportRepository.StudentSessionProjection;
import com.dagacs.repository.HodAttendanceReportRepository.SubjectColumnProjection;
import com.dagacs.repository.HodAttendanceReportRepository.SubjectPresentProjection;
import com.dagacs.repository.HodAttendanceReportRepository.SubjectSessionProjection;
import com.dagacs.repository.HodHierarchyRepository;
import com.dagacs.security.AuthenticatedHodResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phase 3 HOD attendance intelligence: the academic-context overview, the
 * attendance <b>matrix</b>, the student and subject detail views, and the
 * context-scoped low-attendance report.
 *
 * <h2>THE DENOMINATOR: conducted attendance sessions</h2>
 * <p>Every {@code Total Classes} in every Phase 3 report is a count of
 * <b>conducted {@code AttendanceSession}s</b> - never a count of attendance
 * records. The distinction is not cosmetic:</p>
 * <pre>
 *   Conducted sessions = 10,  Present = 7, Absent = 2, Unmarked = 1
 *
 *   CORRECT   Present 7, Total Classes 10, Attendance 70%
 *   WRONG     Present 7, Total Classes  9, Attendance 77.8%   (record count)
 * </pre>
 * <p>Consequences that are load-bearing throughout this class:</p>
 * <ul>
 *   <li><b>Unmarked = absent.</b> A conducted class nobody was marked in stays in
 *       the denominator and simply contributes nothing to the numerator.</li>
 *   <li><b>A zero-record student is measured, not skipped.</b> They report
 *       {@code 0 / 10 = 0%}, never {@code 0 / 0}.</li>
 *   <li><b>Per-subject denominators differ.</b> A subject with 40 conducted
 *       classes and 30 present is 75%, while another with 30 classes and 24
 *       present is also 80%; neither is derived from the other.</li>
 *   <li><b>Overall is a ratio of sums, never an average of ratios.</b>
 *       {@code 32/40 + 24/30} reports {@code 56/70 = 80%}.</li>
 *   <li><b>No conducted classes = no percentage.</b> The result is
 *       {@code null} (rendered {@code N/A}), never a misleading 0%.</li>
 * </ul>
 *
 * <h2>One canonical aggregation, five consumers</h2>
 * <p>{@link #resolveTotals} is the <b>only</b> place attendance totals are
 * computed. The overview, the matrix, the student detail, the subject detail, the
 * low-attendance report, the Excel export and the PDF export all read from the
 * single {@link Totals} object it returns, so no two surfaces can ever disagree
 * and no second denominator exists. The Excel and PDF render that same object
 * through the frozen generators; they compute nothing themselves.</p>
 *
 * <h2>Security</h2>
 * <p>The department is derived exclusively from the JWT via
 * {@link AuthenticatedHodResolver}; no method accepts a department id. Every
 * client-supplied identifier is a <em>selection</em>, never an authority:
 * {@link HodAcademicSelection#assertOwned} proves the four academic levels belong
 * to the HOD's department (403) and agree with each other (400), and
 * {@code assertStudentInContext} / {@code assertSubjectInContext} prove the
 * requested student/subject really belongs to that exact context. All of it runs
 * <b>before</b> any data query, and every query additionally carries
 * {@code p.department.id = :deptId}, so a bypass would still return no data.</p>
 *
 * <h2>Fixed query budget (no N+1)</h2>
 * <p>Overview: 1 subject set + 1 population + 4 aggregates. Matrix: 1 subject set
 * + 1 population + 1 conducted-per-subject + 1 present-per-cell (+1 aggregate only
 * when sorting by percentage). No path loops a query per student, and the matrix
 * never needs a per-student session query because conducted classes are a
 * property of the section, not of the student.</p>
 */
@Service
public class HodAttendanceReportService {

    /**
     * The application's existing low-attendance threshold, echoed from the frozen
     * {@code HAVING} clause in {@link HodHierarchyRepository}. Phase 3 does not
     * make it configurable and does not add a second threshold.
     */
    public static final double LOW_ATTENDANCE_THRESHOLD = 75.0;

    /** Display bands; presentational only, no new business rule. */
    private static final double HIGH_BAND_FLOOR = 90.0;

    private static final int DEFAULT_MATRIX_PAGE_SIZE = 50;
    private static final int MAX_MATRIX_PAGE_SIZE = 200;

    public static final String SORT_ENROLLMENT_NUMBER = "enrollmentNumber";
    public static final String SORT_NAME = "name";
    public static final String SORT_OVERALL_PERCENTAGE = "overallPercentage";

    /** The exact contract message for a matrix without a complete context. */
    public static final String MATRIX_CONTEXT_REQUIRED_MESSAGE =
            "Select Academic Session, Program, Semester and Section to view the Attendance Matrix.";

    private final AuthenticatedHodResolver hodResolver;
    private final HodHierarchyService hierarchyService;
    private final HodHierarchyRepository hierarchyRepository;
    private final HodAttendanceReportRepository attendanceRepository;

    public HodAttendanceReportService(AuthenticatedHodResolver hodResolver,
                                      HodHierarchyService hierarchyService,
                                      HodHierarchyRepository hierarchyRepository,
                                      HodAttendanceReportRepository attendanceRepository) {
        this.hodResolver = hodResolver;
        this.hierarchyService = hierarchyService;
        this.hierarchyRepository = hierarchyRepository;
        this.attendanceRepository = attendanceRepository;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // THE SINGLE SOURCE OF TRUTH
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The canonical attendance totals of one academic context.
     *
     * <p>Immutable and self-describing: {@code conductedBySubject} is the
     * {@code Total Classes} of each matrix column, {@code conductedByStudent} the
     * overall denominator of each student, and {@code presentBy*} the
     * corresponding numerators.</p>
     *
     * @param conductedBySubject             subject id -> conducted classes
     * @param conductedByStudent            student id -> conducted classes
     * @param presentByStudent               student id -> PRESENT marks
     * @param presentByStudentAndSubject     student id -> (subject id -> PRESENT marks)
     * @param presentBySubject               subject id -> PRESENT marks in the context
     */
    public record Totals(Map<Long, Long> conductedBySubject,
                         Map<Long, Long> conductedByStudent,
                         Map<Long, Long> presentByStudent,
                         Map<Long, Map<Long, Long>> presentByStudentAndSubject,
                         Map<Long, Long> presentBySubject) {

        /** The all-zero totals, used when the context has no subjects at all. */
        static Totals empty() {
            return new Totals(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }

        /** Conducted classes of one subject: the {@code Total Classes} of a column. */
        public long classesOfSubject(Long subjectId) {
            return conductedBySubject.getOrDefault(subjectId, 0L);
        }

        /** Conducted classes one student was expected to attend. */
        public long classesOfStudent(Long studentId) {
            return conductedByStudent.getOrDefault(studentId, 0L);
        }

        /** PRESENT marks of one student across the context. */
        public long presentOfStudent(Long studentId) {
            return presentByStudent.getOrDefault(studentId, 0L);
        }

        /** PRESENT marks of one student in one subject. */
        public long presentOf(Long studentId, Long subjectId) {
            Map<Long, Long> bySubject = presentByStudentAndSubject.get(studentId);
            return bySubject == null ? 0L : bySubject.getOrDefault(subjectId, 0L);
        }

        /** PRESENT marks of one subject across the context. */
        public long presentOfSubject(Long subjectId) {
            return presentBySubject.getOrDefault(subjectId, 0L);
        }

        /**
         * {@code present / total * 100}, or {@code null} when nothing was
         * conducted. The single percentage rule used by every Phase 3 surface.
         */
        public Double percentage(long present, long total) {
            if (total <= 0) {
                return null;
            }
            return (double) present / total * 100.0;
        }

        /**
         * Percentage of one student in one subject, against the subject's
         * conducted classes.
         */
        public Double percentageOf(Long studentId, Long subjectId) {
            return percentage(presentOf(studentId, subjectId), classesOfSubject(subjectId));
        }

        /**
         * Percentage of one student in one subject, against an explicit
         * denominator (used by the subject detail, where the denominator is the
         * subject's conducted classes).
         */
        public Double percentageOf(Long studentId, Long subjectId, long total) {
            return percentage(presentOf(studentId, subjectId), total);
        }
    }

    /**
     * Resolves the canonical totals for a context. <b>The only</b> place Phase 3
     * attendance figures are computed.
     *
     * <p>Runs at most four grouped queries and never one per student:</p>
     * <ol>
     *   <li>conducted classes per subject (one row per subject);</li>
     *   <li>conducted classes per student (one row per student);</li>
     *   <li>present marks per student and subject - restricted to [studentIds]
     *       when supplied, which is how the matrix page stays a single bounded
     *       query;</li>
     *   <li>present marks per subject across the context.</li>
     * </ol>
     *
     * <p>An empty [subjectIds] short-circuits to {@link Totals#empty()}: with no
     * subject in the context there is no denominator, so every figure is zero and
     * every percentage is unavailable - never a fabricated 0%.</p>
     */
    private Totals resolveTotals(Long deptId, HodAcademicSelection selection,
                                 List<Long> subjectIds, List<Long> studentIds,
                                 String startDate, String endDate) {
        if (subjectIds == null || subjectIds.isEmpty()) {
            return Totals.empty();
        }

        Map<Long, Long> conductedBySubject = new LinkedHashMap<>();
        for (SubjectSessionProjection row : attendanceRepository
                .findConductedSessionCountsBySubject(
                        deptId, subjectIds, selection.sectionId(), startDate, endDate)) {
            conductedBySubject.put(row.getSubjectId(), nullSafe(row.getSessionCount()));
        }

        Map<Long, Long> conductedByStudent = new LinkedHashMap<>();
        for (StudentSessionProjection row : attendanceRepository
                .findConductedSessionCountsByStudent(
                        deptId, subjectIds, selection.sessionId(), selection.programId(),
                        selection.semesterId(), selection.sectionId(), startDate, endDate)) {
            conductedByStudent.put(row.getStudentId(), nullSafe(row.getSessionCount()));
        }

        Map<Long, Long> presentByStudent = new LinkedHashMap<>();
        Map<Long, Map<Long, Long>> presentByStudentAndSubject = new LinkedHashMap<>();
        if (studentIds != null && !studentIds.isEmpty()) {
            for (com.dagacs.repository.HodAttendanceReportRepository.MatrixCellProjection row
                    : attendanceRepository.findPresentCountsByStudentAndSubject(
                            deptId, studentIds, subjectIds, selection.sessionId(),
                            selection.programId(), selection.semesterId(),
                            selection.sectionId(), startDate, endDate)) {
                presentByStudentAndSubject
                        .computeIfAbsent(row.getStudentId(), key -> new LinkedHashMap<>())
                        .put(row.getSubjectId(), nullSafe(row.getPresentCount()));
            }
            for (Map.Entry<Long, Map<Long, Long>> entry : presentByStudentAndSubject.entrySet()) {
                presentByStudent.put(entry.getKey(),
                        entry.getValue().values().stream().mapToLong(Long::longValue).sum());
            }
        } else {
            for (StudentPresentProjection row : attendanceRepository.findPresentCountsByStudent(
                    deptId, subjectIds, selection.sessionId(), selection.programId(),
                    selection.semesterId(), selection.sectionId(), startDate, endDate)) {
                presentByStudent.put(row.getStudentId(), nullSafe(row.getPresentCount()));
            }
        }

        Map<Long, Long> presentBySubject = new LinkedHashMap<>();
        for (SubjectPresentProjection row : attendanceRepository.findPresentCountsBySubject(
                deptId, selection.sessionId(), selection.programId(), selection.semesterId(),
                selection.sectionId(), startDate, endDate)) {
            presentBySubject.put(row.getSubjectId(), nullSafe(row.getPresentCount()));
        }

        return new Totals(Map.copyOf(conductedBySubject), Map.copyOf(conductedByStudent),
                Map.copyOf(presentByStudent), deepCopy(presentByStudentAndSubject),
                Map.copyOf(presentBySubject));
    }

    private static Map<Long, Map<Long, Long>> deepCopy(Map<Long, Map<Long, Long>> source) {
        Map<Long, Map<Long, Long>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, Map.copyOf(value)));
        return Map.copyOf(copy);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ATTENDANCE OVERVIEW
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public HodAttendanceOverviewDTO getOverview(HodAcademicSelection selection,
                                                String startDate, String endDate) {
        Long deptId = departmentId();
        selection.assertOwned(deptId, hierarchyService);

        List<SubjectColumnProjection> columns = subjectColumns(deptId, selection);
        List<Long> subjectIds = columns.stream().map(SubjectColumnProjection::getSubjectId).toList();
        Totals totals = resolveTotals(deptId, selection, subjectIds, null, startDate, endDate);

        // The enrolled population, so a student with no marks is still measured.
        List<MatrixStudentProjection> population = studentPopulation(deptId, selection);

        long totalStudents = population.size();
        long overallPresent = 0L;
        long overallClasses = 0L;
        long belowThreshold = 0L;
        long noConductedClasses = 0L;
        long high = 0L;
        long medium = 0L;
        long low = 0L;
        long classesConducted = 0L;

        for (SubjectColumnProjection column : columns) {
            classesConducted += totals.classesOfSubject(column.getSubjectId());
        }

        for (MatrixStudentProjection student : population) {
            Long studentId = student.getStudentId();
            long present = totals.presentOfStudent(studentId);
            long classes = totals.classesOfStudent(studentId);
            overallPresent += present;
            overallClasses += classes;
            if (classes <= 0) {
                // No conducted class: the percentage is genuinely unavailable, so
                // the student is reported separately instead of as 0% or 100%.
                noConductedClasses++;
                continue;
            }
            double percentage = (double) present / classes * 100.0;
            if (percentage < LOW_ATTENDANCE_THRESHOLD) {
                low++;
                belowThreshold++;
            } else if (percentage >= HIGH_BAND_FLOOR) {
                high++;
            } else {
                medium++;
            }
        }

        boolean contextComplete = isComplete(selection);
        return HodAttendanceOverviewDTO.builder()
                .context(resolveContext(deptId, selection))
                .totalStudents(totalStudents)
                .overallPresentCount(overallPresent)
                .overallTotalClasses(overallClasses)
                .overallPercentage(totals.percentage(overallPresent, overallClasses))
                .belowThresholdCount(belowThreshold)
                .classesConducted(contextComplete ? classesConducted : null)
                .thresholdPercentage(LOW_ATTENDANCE_THRESHOLD)
                .noConductedClasses(noConductedClasses)
                .distribution(List.of(
                        band("high", "90-100%", high),
                        band("medium", "75-89%", medium),
                        band("low", "Below 75%", low)))
                .subjects(subjectSummaries(deptId, selection, columns, totals, population,
                        startDate, endDate))
                .build();
    }

    /**
     * The context's subject table.
     *
     * <p>Driven by the same dynamic subject set the matrix uses, with
     * {@code Total Classes} = the subject's conducted classes measured against the
     * enrolled population, and {@code Present} = the subject's PRESENT marks. A
     * subject with no conducted class reports {@code 0} and a {@code null}
     * percentage - the established "no data" state, never a fabricated 0%.</p>
     *
     * <p>Phase 4A adds the reporting columns {@code students} and
     * {@code studentsBelowThreshold}. They introduce <b>no new attendance
     * semantics and no new denominator</b>: the below-threshold count is
     * {@code presentOf(student, subject) / classesOfSubject(subject)}, the exact
     * expression the rest of Phase 3 already uses for that same figure. A subject
     * with no conducted class reports {@code 0} rather than counting every student
     * as failing against a denominator that does not exist.</p>
     */
    private List<HodAttendanceSubjectSummaryDTO> subjectSummaries(Long deptId,
                                                                  HodAcademicSelection selection,
                                                                  List<SubjectColumnProjection> columns,
                                                                  Totals totals,
                                                                  List<MatrixStudentProjection> population,
                                                                  String startDate,
                                                                  String endDate) {
        if (columns.isEmpty()) {
            return List.of();
        }
        Map<Long, List<String>> faculty = facultyBySubject(
                selection.sectionId(), selection.semesterId());
        int studentCount = population.size();

        // Phase 4A: the per-subject below-threshold count needs per-student,
        // per-subject PRESENT marks. The overview's own resolveTotals call
        // aggregates present per STUDENT only, because a null studentIds list
        // deliberately skips the per-subject breakdown, so this is one extra
        // grouped query over the whole context - never one query per student.
        Map<Long, Long> belowThresholdBySubject = belowThresholdBySubject(
                deptId, selection, columns, population, totals, startDate, endDate);

        List<HodAttendanceSubjectSummaryDTO> rows = new ArrayList<>();
        for (SubjectColumnProjection column : columns) {
            long classes = totals.classesOfSubject(column.getSubjectId());
            long present = totals.presentOfSubject(column.getSubjectId());
            long total = classes * Math.max(studentCount, 0);
            rows.add(HodAttendanceSubjectSummaryDTO.builder()
                    .subjectId(column.getSubjectId())
                    .subjectOfferingId(column.getSubjectOfferingId())
                    .subjectCode(column.getSubjectCode())
                    .subjectName(column.getSubjectName())
                    .facultyNames(faculty.getOrDefault(column.getSubjectId(), List.of()))
                    .classesConducted(classes)
                    .presentCount(present)
                    .totalClasses(total)
                    .percentage(totals.percentage(present, total))
                    .students((long) studentCount)
                    .studentsBelowThreshold(belowThresholdBySubject
                            .getOrDefault(column.getSubjectId(), 0L))
                    .build());
        }
        return rows;
    }

    /**
     * Students below the threshold, per subject, from one grouped query.
     *
     * <p>Reuses the same authorized repository method the matrix page uses, with
     * the full enrolled population, so the authorization scope and the
     * date-range filtering are identical to every other Phase 3 read. The
     * denominator comes from the {@code totals} already resolved for this
     * request, so the subject's conducted-class count is never re-queried - the
     * whole method costs exactly one additional grouped query no matter how many
     * subjects the context contains.</p>
     */
    private Map<Long, Long> belowThresholdBySubject(Long deptId,
                                                    HodAcademicSelection selection,
                                                    List<SubjectColumnProjection> columns,
                                                    List<MatrixStudentProjection> population,
                                                    Totals totals,
                                                    String startDate,
                                                    String endDate) {
        Map<Long, Long> below = new LinkedHashMap<>();
        if (population.isEmpty()) {
            return below;
        }
        List<Long> studentIds = population.stream()
                .map(MatrixStudentProjection::getStudentId)
                .toList();
        List<Long> subjectIds = columns.stream()
                .map(SubjectColumnProjection::getSubjectId)
                .toList();

        for (com.dagacs.repository.HodAttendanceReportRepository.MatrixCellProjection cell
                : attendanceRepository.findPresentCountsByStudentAndSubject(
                deptId, studentIds, subjectIds, selection.sessionId(),
                selection.programId(), selection.semesterId(), selection.sectionId(),
                startDate, endDate)) {
            long classes = totals.classesOfSubject(cell.getSubjectId());
            if (classes <= 0) {
                // No denominator: there is no percentage, so nobody "fails" this
                // subject. Counting them would fabricate a 0%.
                continue;
            }
            long present = nullSafe(cell.getPresentCount());
            if ((double) present / classes * 100.0 < LOW_ATTENDANCE_THRESHOLD) {
                below.merge(cell.getSubjectId(), 1L, Long::sum);
            }
        }
        return below;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ATTENDANCE MATRIX
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The HOD attendance cross-tab for one fully-resolved academic context.
     *
     * <p>All four mandatory levels are required: a matrix over a mixed
     * department, a mixed semester or a mixed section would be meaningless, so
     * an incomplete context is rejected with 400 and the exact contract message
     * rather than silently producing a department-wide table.</p>
     */
    @Transactional(readOnly = true)
    public HodAttendanceMatrixDTO getMatrix(HodAcademicSelection selection,
                                            Long subjectId,
                                            String startDate, String endDate,
                                            int page, int size, String sortBy, String direction) {
        Long deptId = departmentId();
        if (!isComplete(selection)) {
            throw new AuthException(MATRIX_CONTEXT_REQUIRED_MESSAGE, 400);
        }
        selection.assertOwned(deptId, hierarchyService);
        String sort = normalizeSort(sortBy);
        boolean ascending = normalizeDirection(direction);
        int pageSize = clampPageSize(size);
        int pageIndex = Math.max(page, 0);

        List<HodAttendanceMatrixColumnDTO> columns = matrixColumns(deptId, selection, subjectId);
        List<Long> columnSubjectIds = columns.stream()
                .map(HodAttendanceMatrixColumnDTO::getSubjectId)
                .toList();

        List<MatrixStudentProjection> population = studentPopulation(deptId, selection);

        // A population-wide total is needed to order the whole context exactly,
        // so a percentage sort is not a per-page sub-order. It is one extra
        // aggregate and is only paid when that sort is actually used; every
        // other sort reads the projections directly.
        List<MatrixStudentProjection> ordered;
        if (SORT_OVERALL_PERCENTAGE.equals(sort)) {
            Totals orderingTotals =
                    resolveTotals(deptId, selection, columnSubjectIds, null, startDate, endDate);
            ordered = order(population, sort, ascending, orderingTotals);
        } else {
            ordered = order(population, sort, ascending, null);
        }

        int from = Math.min(pageIndex * pageSize, ordered.size());
        int to = Math.min(from + pageSize, ordered.size());
        List<MatrixStudentProjection> pageRows = ordered.subList(from, to);

        // The page cells are a second, page-bounded pass over the same canonical
        // layer, so the numbers printed on the page and the numbers the ordering
        // was derived from can never disagree.
        Totals pageTotals = resolveTotals(
                deptId, selection, columnSubjectIds,
                pageRows.stream().map(MatrixStudentProjection::getStudentId).toList(),
                startDate, endDate);

        List<HodAttendanceMatrixRowDTO> rows = new ArrayList<>();
        for (MatrixStudentProjection student : pageRows) {
            rows.add(buildRow(student, columns, pageTotals));
        }

        int total = population.size();
        int totalPages = pageSize <= 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        return HodAttendanceMatrixDTO.builder()
                .context(resolveContext(deptId, selection))
                .subjects(columns)
                .students(rows)
                .page(pageIndex)
                .size(pageSize)
                .totalElements((long) total)
                .totalPages(totalPages)
                .singleSubject(subjectId != null)
                .thresholdPercentage(LOW_ATTENDANCE_THRESHOLD)
                .build();
    }

    /**
     * Builds one cross-tab row from the canonical totals.
     *
     * <p>Each cell is {@code Present / <that subject's> conducted classes}, so
     * subject columns can legitimately have different denominators. The row total
     * is the sum of the row's own cells and the overall percentage is their
     * ratio - never an average of the cell percentages, which would give a
     * 40-class subject the same weight as a 30-class one.</p>
     */
    private HodAttendanceMatrixRowDTO buildRow(MatrixStudentProjection student,
                                               List<HodAttendanceMatrixColumnDTO> columns,
                                               Totals totals) {
        Long studentId = student.getStudentId();
        List<HodAttendanceMatrixCellDTO> cells = new ArrayList<>(columns.size());
        long totalPresent = 0L;
        long totalClasses = 0L;
        for (HodAttendanceMatrixColumnDTO column : columns) {
            long classes = totals.classesOfSubject(column.getSubjectId());
            long present = totals.presentOf(studentId, column.getSubjectId());
            totalPresent += present;
            totalClasses += classes;
            cells.add(HodAttendanceMatrixCellDTO.builder()
                    .subjectId(column.getSubjectId())
                    .present(present)
                    .total(classes)
                    .percentage(totals.percentage(present, classes))
                    .build());
        }
        return HodAttendanceMatrixRowDTO.builder()
                .studentId(studentId)
                .enrollmentNumber(student.getEnrollmentNumber())
                .rollNumber(student.getRollNumber())
                .studentName(student.getStudentName())
                .sectionName(student.getSectionName())
                .subjects(cells)
                .totalPresent(totalPresent)
                .totalClasses(totalClasses)
                .overallPercentage(totals.percentage(totalPresent, totalClasses))
                .build();
    }

    /**
     * Applies the requested ordering.
     *
     * <p>A student with <b>no conducted class</b> has no percentage and is always
     * placed last, in both directions - never treated as 0% and never mixed into
     * the numeric ordering. Every other ordering is a stable sort over the
     * repository's own (enrollment number, name) order, so equal keys never
     * shuffle between requests.</p>
     */
    private List<MatrixStudentProjection> order(List<MatrixStudentProjection> population,
                                                String sort,
                                                boolean ascending,
                                                Totals totals) {
        Comparator<MatrixStudentProjection> comparator;
        if (SORT_OVERALL_PERCENTAGE.equals(sort) && totals != null) {
            comparator = Comparator.comparingDouble(student -> {
                long classes = totals.classesOfStudent(student.getStudentId());
                return (double) totals.presentOfStudent(student.getStudentId()) / classes * 100.0;
            });
            if (ascending) {
                comparator = Comparator
                        .comparingDouble((MatrixStudentProjection student) ->
                                totals.classesOfStudent(student.getStudentId()) <= 0 ? 1 : 0)
                        .thenComparing(comparator);
            } else {
                comparator = Comparator
                        .comparingInt((MatrixStudentProjection student) ->
                                totals.classesOfStudent(student.getStudentId()) <= 0 ? 1 : 0)
                        .thenComparing(comparator.reversed());
            }
        } else if (SORT_NAME.equals(sort)) {
            comparator = Comparator.comparing(MatrixStudentProjection::getStudentName,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(MatrixStudentProjection::getEnrollmentNumber,
                            Comparator.nullsLast(Comparator.naturalOrder()));
            if (!ascending) {
                comparator = comparator.reversed();
            }
        } else {
            // Default: enrollment number, the academically meaningful order. The
            // repository already returns it, so ascending needs no sort at all.
            if (ascending) {
                return new ArrayList<>(population);
            }
            comparator = Comparator.comparing(MatrixStudentProjection::getEnrollmentNumber,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(MatrixStudentProjection::getStudentName,
                            Comparator.nullsLast(Comparator.naturalOrder()));
            comparator = comparator.reversed();
        }
        List<MatrixStudentProjection> ordered = new ArrayList<>(population);
        ordered.sort(comparator);
        return ordered;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STUDENT DETAIL
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public HodStudentAttendanceDetailDTO getStudentDetail(HodAcademicSelection selection,
                                                          Long studentId, Long subjectId,
                                                          String startDate, String endDate) {
        Long deptId = departmentId();
        selection.assertOwned(deptId, hierarchyService);
        // Proves the student belongs to the HOD's department AND to this exact
        // context. Swapping the id in the URL therefore fails with 403 and no
        // data rather than exposing another class.
        hierarchyService.assertStudentInContext(studentId, deptId, selection);

        List<HodAttendanceMatrixColumnDTO> columns = matrixColumns(deptId, selection, subjectId);
        List<Long> subjectIds = columns.stream()
                .map(HodAttendanceMatrixColumnDTO::getSubjectId)
                .toList();
        Totals totals = resolveTotals(deptId, selection, subjectIds, List.of(studentId),
                startDate, endDate);

        List<HodStudentSubjectAttendanceDTO> subjects = new ArrayList<>();
        long totalPresent = 0L;
        long totalClasses = 0L;
        for (HodAttendanceMatrixColumnDTO column : columns) {
            long classes = totals.classesOfSubject(column.getSubjectId());
            long present = totals.presentOf(studentId, column.getSubjectId());
            totalPresent += present;
            totalClasses += classes;
            subjects.add(HodStudentSubjectAttendanceDTO.builder()
                    .subjectId(column.getSubjectId())
                    .subjectCode(column.getSubjectCode())
                    .subjectName(column.getSubjectName())
                    .classes(classes)
                    .present(present)
                    // Conducted classes not attended = explicit absences plus
                    // every unmarked conducted class.
                    .notAttended(classes - present)
                    .percentage(totals.percentage(present, classes))
                    .build());
        }

        MatrixStudentProjection identity = studentPopulation(deptId, selection)
                .stream()
                .filter(s -> Objects.equals(s.getStudentId(), studentId))
                .findFirst()
                .orElse(null);

        HodAttendanceContextDTO context = resolveContext(deptId, selection);
        return HodStudentAttendanceDetailDTO.builder()
                .context(context)
                .studentId(studentId)
                .enrollmentNumber(identity == null ? null : identity.getEnrollmentNumber())
                .rollNumber(identity == null ? null : identity.getRollNumber())
                .studentName(identity == null ? null : identity.getStudentName())
                .programName(context == null ? null : context.getProgramName())
                .semesterName(context == null ? null : context.getSemesterName())
                .sectionName(context == null ? null : context.getSectionName())
                .subjects(subjects)
                .totalPresent(totalPresent)
                .totalClasses(totalClasses)
                .overallPercentage(totals.percentage(totalPresent, totalClasses))
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SUBJECT DETAIL
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public HodSubjectAttendanceDetailDTO getSubjectDetail(HodAcademicSelection selection,
                                                           Long subjectId,
                                                           String startDate, String endDate) {
        Long deptId = departmentId();
        selection.assertOwned(deptId, hierarchyService);
        hierarchyService.assertSubjectInContext(subjectId, deptId, selection);

        List<HodAttendanceMatrixColumnDTO> columns = matrixColumns(deptId, selection, subjectId);
        HodAttendanceMatrixColumnDTO column = columns.stream()
                .filter(c -> Objects.equals(c.getSubjectId(), subjectId))
                .findFirst()
                .orElseThrow(() -> new AuthException(
                        "The selected subject does not belong to the selected academic context",
                        403, AuthErrorCode.HOD_SCOPE_VIOLATION));

        // The enrolled roster is the population, so a student with no marks in
        // this subject is still listed - and is measured against the subject's
        // conducted classes rather than omitted.
        List<MatrixStudentProjection> enrolled = studentPopulation(deptId, selection);
        Totals totals = resolveTotals(deptId, selection, List.of(subjectId),
                enrolled.stream().map(MatrixStudentProjection::getStudentId).toList(),
                startDate, endDate);

        long classes = totals.classesOfSubject(subjectId);
        List<HodSubjectAttendanceDetailRowDTO> rows = new ArrayList<>();
        long presentTotal = 0L;
        long belowThreshold = 0L;
        for (MatrixStudentProjection student : enrolled) {
            long present = totals.presentOf(student.getStudentId(), subjectId);
            presentTotal += present;
            Double percentage = totals.percentage(present, classes);
            if (percentage != null && percentage < LOW_ATTENDANCE_THRESHOLD) {
                belowThreshold++;
            }
            rows.add(HodSubjectAttendanceDetailRowDTO.builder()
                    .studentId(student.getStudentId())
                    .enrollmentNumber(student.getEnrollmentNumber())
                    .rollNumber(student.getRollNumber())
                    .studentName(student.getStudentName())
                    .present(present)
                    .total(classes)
                    .percentage(percentage)
                    .build());
        }

        long totalAcrossStudents = classes * enrolled.size();
        HodAttendanceContextDTO context = resolveContext(deptId, selection);
        return HodSubjectAttendanceDetailDTO.builder()
                .context(context)
                .subjectId(column.getSubjectId())
                .subjectCode(column.getSubjectCode())
                .subjectName(column.getSubjectName())
                .programName(context == null ? null : context.getProgramName())
                .semesterName(context == null ? null : context.getSemesterName())
                .sectionName(context == null ? null : context.getSectionName())
                .facultyNames(column.getFacultyNames() == null ? List.of() : column.getFacultyNames())
                .totalClasses(classes)
                .students((long) enrolled.size())
                .presentCount(presentTotal)
                .totalClassesAcrossStudents(totalAcrossStudents)
                .averageAttendance(totals.percentage(presentTotal, totalAcrossStudents))
                .studentsBelowThreshold(belowThreshold)
                .thresholdPercentage(LOW_ATTENDANCE_THRESHOLD)
                .studentRows(rows)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOW ATTENDANCE
    // ═══════════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public HodLowAttendanceReportDTO getLowAttendance(HodAcademicSelection selection,
                                                      String startDate, String endDate) {
        Long deptId = departmentId();
        selection.assertOwned(deptId, hierarchyService);

        List<SubjectColumnProjection> columns = subjectColumns(deptId, selection);
        List<Long> subjectIds = columns.stream().map(SubjectColumnProjection::getSubjectId).toList();
        List<MatrixStudentProjection> population = studentPopulation(deptId, selection);
        Totals totals = resolveTotals(deptId, selection, subjectIds, null, startDate, endDate);

        Map<Long, SubjectColumnProjection> columnsById = columns.stream()
                .collect(Collectors.toMap(
                        SubjectColumnProjection::getSubjectId,
                        row -> row,
                        (a, b) -> a,
                        LinkedHashMap::new));

        // The application's fixed 75% threshold, applied to the CORRECTED
        // percentage: Present / conducted sessions. A student with no conducted
        // class has no percentage and is therefore not "below" anything.
        List<Long> belowIds = new ArrayList<>();
        for (MatrixStudentProjection student : population) {
            Long studentId = student.getStudentId();
            long classes = totals.classesOfStudent(studentId);
            if (classes <= 0) {
                continue;
            }
            double percentage = (double) totals.presentOfStudent(studentId) / classes * 100.0;
            if (percentage < LOW_ATTENDANCE_THRESHOLD) {
                belowIds.add(studentId);
            }
        }

        // One extra grouped query, restricted to the students actually listed, so
        // the per-subject breakdown is never a per-student loop.
        Totals culpritTotals = belowIds.isEmpty() || subjectIds.isEmpty()
                ? Totals.empty()
                : resolveTotals(deptId, selection, subjectIds, belowIds, startDate, endDate);

        List<HodLowAttendanceStudentDTO> students = new ArrayList<>();
        // Resolved once, not per student: a lookup inside the loop would be N+1.
        HodAttendanceContextDTO context = resolveContext(deptId, selection);
        for (Long studentId : belowIds) {
            MatrixStudentProjection identity = population.stream()
                    .filter(s -> Objects.equals(s.getStudentId(), studentId))
                    .findFirst()
                    .orElse(null);
            long present = totals.presentOfStudent(studentId);
            long classes = totals.classesOfStudent(studentId);

            List<HodLowAttendanceSubjectDTO> culprits = new ArrayList<>();
            for (SubjectColumnProjection column : columns) {
                long subjectClasses = totals.classesOfSubject(column.getSubjectId());
                if (subjectClasses <= 0) {
                    // No class conducted: not the reason this student is low, so
                    // it is deliberately not listed as a 0% culprit.
                    continue;
                }
                long subjectPresent = culpritTotals.presentOf(studentId, column.getSubjectId());
                double percentage = (double) subjectPresent / subjectClasses * 100.0;
                if (percentage < LOW_ATTENDANCE_THRESHOLD) {
                    culprits.add(HodLowAttendanceSubjectDTO.builder()
                            .subjectId(column.getSubjectId())
                            .subjectCode(column.getSubjectCode())
                            .subjectName(column.getSubjectName())
                            .present(subjectPresent)
                            .total(subjectClasses)
                            .percentage(percentage)
                            .build());
                }
            }

            students.add(HodLowAttendanceStudentDTO.builder()
                    .studentId(studentId)
                    .enrollmentNumber(identity == null ? null : identity.getEnrollmentNumber())
                    .rollNumber(identity == null ? null : identity.getRollNumber())
                    .studentName(identity == null ? null : identity.getStudentName())
                    .sectionName(identity == null ? null : identity.getSectionName())
                    .programName(context == null ? null : context.getProgramName())
                    .semesterName(context == null ? null : context.getSemesterName())
                    .academicSessionName(context == null ? null : context.getAcademicSessionName())
                    .presentCount(present)
                    .totalClasses(classes)
                    .percentage(totals.percentage(present, classes))
                    .subjectsBelowThreshold((long) culprits.size())
                    .belowThresholdSubjects(culprits)
                    .build());
        }

        return HodLowAttendanceReportDTO.builder()
                .context(context)
                .thresholdPercentage(LOW_ATTENDANCE_THRESHOLD)
                .totalStudents(population.size())
                .belowThresholdCount((long) students.size())
                .students(students)
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // SHARED HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The context metadata of a selection, authorized exactly like every other
     * Phase 3 report.
     */
    @Transactional(readOnly = true)
    public HodAttendanceContextDTO resolveContextMetadata(HodAcademicSelection selection) {
        Long deptId = departmentId();
        selection.assertOwned(deptId, hierarchyService);
        return resolveContext(deptId, selection);
    }



    /**
     * The enrolled students of a context.
     *
     * <p>Based on {@code Student} - <b>not</b> on {@code AttendanceRecord} or
     * {@code AttendanceSession} - so a student with no attendance record, and a
     * student whose section has no conducted class, are both still part of the
     * population and therefore still measured.</p>
     */
    private List<MatrixStudentProjection> studentPopulation(Long deptId,
                                                             HodAcademicSelection selection) {
        return attendanceRepository.findStudentsForContext(
                deptId, selection.sessionId(), selection.programId(),
                selection.semesterId(), selection.sectionId());
    }

    /**
     * The dynamic subject columns of a context, optionally narrowed to a single
     * subject. The subject set is the context's real offerings/assignments - a
     * subject of the department that is not part of this academic context can
     * never become a column, and therefore can never enter a denominator.
     */
    private List<HodAttendanceMatrixColumnDTO> matrixColumns(Long deptId,
                                                             HodAcademicSelection selection,
                                                             Long subjectId) {
        List<SubjectColumnProjection> columns = subjectColumns(deptId, selection);
        if (subjectId == null) {
            return toColumns(columns, selection);
        }
        // The subject must be a real column of this context, not merely a
        // well-formed id.
        if (columns.stream().noneMatch(c -> Objects.equals(c.getSubjectId(), subjectId))) {
            throw new AuthException(
                    "The selected subject does not belong to the selected academic context",
                    403, AuthErrorCode.HOD_SCOPE_VIOLATION);
        }
        return toColumns(columns.stream()
                .filter(c -> Objects.equals(c.getSubjectId(), subjectId))
                .toList(), selection);
    }

    private List<SubjectColumnProjection> subjectColumns(Long deptId, HodAcademicSelection selection) {
        return attendanceRepository.findSubjectColumnsForContext(
                deptId, selection.sessionId(), selection.programId(),
                selection.semesterId(), selection.sectionId());
    }

    private List<HodAttendanceMatrixColumnDTO> toColumns(List<SubjectColumnProjection> columns,
                                                          HodAcademicSelection selection) {
        Map<Long, List<String>> faculty = facultyBySubject(
                selection.sectionId(), selection.semesterId());
        List<HodAttendanceMatrixColumnDTO> result = new ArrayList<>();
        for (SubjectColumnProjection column : columns) {
            result.add(HodAttendanceMatrixColumnDTO.builder()
                    .subjectId(column.getSubjectId())
                    .subjectOfferingId(column.getSubjectOfferingId())
                    .subjectCode(column.getSubjectCode())
                    .subjectName(column.getSubjectName())
                    .facultyNames(faculty.getOrDefault(column.getSubjectId(), List.of()))
                    .build());
        }
        return result;
    }

    /**
     * Resolves the display context of a request.
     *
     * <p>A section determines its own academic session and program, and a
     * semester determines its session and program, so the most specific
     * supplied level is enough to label the report. The values are read back
     * from the very rows the request was authorized against, so a report can
     * never display a label that disagrees with its own data.</p>
     */
    private HodAttendanceContextDTO resolveContext(Long deptId, HodAcademicSelection selection) {
        Long sessionId = null;
        String sessionName = null;
        Long programId = null;
        String programName = null;
        Long semesterId = selection.semesterId();
        String semesterName = null;
        Long sectionId = selection.sectionId();
        String sectionName = null;

        // Most specific first: a section determines its own session and program.
        if (sectionId != null) {
            for (HodHierarchyRepository.ContextSectionProjection row
                    : hierarchyRepository.findContextForSection(sectionId, deptId)) {
                sessionId = row.getSessionId();
                sessionName = row.getSessionName();
                programId = row.getProgramId();
                programName = row.getProgramName();
                sectionName = row.getSectionName();
            }
        }
        if (sessionId == null && selection.sessionId() != null) {
            for (HodHierarchyRepository.OptionProjection row
                    : hierarchyRepository.findAcademicSessionOptions(deptId)) {
                if (Objects.equals(row.getId(), selection.sessionId())) {
                    sessionId = row.getId();
                    sessionName = row.getName();
                    programId = row.getProgramId();
                    programName = row.getProgramName();
                }
            }
        }
        if (programId == null && selection.programId() != null) {
            for (HodHierarchyRepository.OptionProjection row
                    : hierarchyRepository.findProgramOptions(deptId)) {
                if (Objects.equals(row.getId(), selection.programId())) {
                    programId = row.getId();
                    programName = row.getName();
                }
            }
        }
        // A section says nothing about the semester, so the semester label is
        // resolved on its own whenever one was selected.
        if (semesterId != null) {
            for (HodHierarchyRepository.ContextSemesterProjection row
                    : hierarchyRepository.findContextForSemester(semesterId, deptId)) {
                semesterName = row.getSemesterName();
            }
        }
        if (sessionId == null) {
            sessionId = selection.sessionId();
        }
        if (programId == null) {
            programId = selection.programId();
        }

        return HodAttendanceContextDTO.builder()
                .academicSessionId(sessionId)
                .academicSessionName(sessionName)
                .programId(programId)
                .programName(programName)
                .semesterId(semesterId)
                .semesterName(semesterName)
                .sectionId(sectionId)
                .sectionName(sectionName)
                .complete(isComplete(selection))
                .build();
    }

    private Map<Long, List<String>> facultyBySubject(Long sectionId, Long semesterId) {
        if (sectionId == null || semesterId == null) {
            return Map.of();
        }
        Map<Long, Set<String>> grouped = new LinkedHashMap<>();
        for (HodHierarchyRepository.FacultyProjection row
                : hierarchyRepository.findFacultyForSectionAndSemester(sectionId, semesterId)) {
            grouped.computeIfAbsent(row.getSubjectId(), key -> new java.util.TreeSet<>())
                    .add(row.getTeacherName());
        }
        Map<Long, List<String>> result = new LinkedHashMap<>();
        grouped.forEach((subjectId, names) -> result.put(subjectId, List.copyOf(names)));
        return result;
    }

    private Long departmentId() {
        Teacher hod = hodResolver.resolve();
        return hod.getDepartment().getId();
    }

    private static boolean isComplete(HodAcademicSelection selection) {
        return selection.sessionId() != null
                && selection.programId() != null
                && selection.semesterId() != null
                && selection.sectionId() != null;
    }

    private static HodAttendanceDistributionDTO band(String key, String label, long count) {
        return HodAttendanceDistributionDTO.builder()
                .band(key)
                .label(label)
                .studentCount(count)
                .build();
    }

    private static String normalizeSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return SORT_ENROLLMENT_NUMBER;
        }
        return switch (sortBy.trim()) {
            case SORT_NAME -> SORT_NAME;
            case SORT_OVERALL_PERCENTAGE -> SORT_OVERALL_PERCENTAGE;
            case SORT_ENROLLMENT_NUMBER -> SORT_ENROLLMENT_NUMBER;
            default -> throw new AuthException(
                    "sortBy must be one of enrollmentNumber, name or overallPercentage", 400);
        };
    }

    private static boolean normalizeDirection(String direction) {
        if (direction == null || direction.isBlank()) {
            return true;
        }
        return switch (direction.trim().toLowerCase(Locale.ROOT)) {
            case "asc" -> true;
            case "desc" -> false;
            default -> throw new AuthException("direction must be asc or desc", 400);
        };
    }

    private static int clampPageSize(int size) {
        if (size <= 0) {
            return DEFAULT_MATRIX_PAGE_SIZE;
        }
        return Math.min(size, MAX_MATRIX_PAGE_SIZE);
    }

    private static long nullSafe(Long value) {
        return value == null ? 0L : value;
    }
}
