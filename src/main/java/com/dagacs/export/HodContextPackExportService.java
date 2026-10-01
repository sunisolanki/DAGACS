package com.dagacs.export;

import com.dagacs.dto.HodAttendanceContextDTO;
import com.dagacs.dto.HodAttendanceDistributionDTO;
import com.dagacs.dto.HodAttendanceMatrixDTO;
import com.dagacs.dto.HodAttendanceMatrixRowDTO;
import com.dagacs.dto.HodAttendanceOverviewDTO;
import com.dagacs.dto.HodAttendanceSubjectSummaryDTO;
import com.dagacs.dto.HodLowAttendanceReportDTO;
import com.dagacs.service.HodAcademicSelection;
import com.dagacs.service.HodAttendanceReportService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 4B: the HOD Context Pack - one workbook holding the reports of a single
 * authorized academic context.
 *
 * <h3>Why this is not a duplicate of the Matrix</h3>
 * <p>The Matrix is a cross-tab: unreadable past a handful of subjects, and it
 * answers "how did this student do in every subject" rather than "which students
 * need attention". The Pack bundles the five questions a HOD actually asks of a
 * context, each answerable at a glance, in the order a review would ask them:
 * how is the cohort doing (1), subject by subject (4), who is at risk and why (3),
 * per student (5), with the full cross-tab (2) for the detail that only some
 * readers need.</p>
 *
 * <h3>One context, one authorization, three service calls</h3>
 * <p>The Pack accepts <b>no</b> department, program, session, semester, section,
 * student or subject identity from the client beyond the same academic-selection
 * parameters the Phase 4A context reports take. That single
 * {@link HodAcademicSelection} is passed to the canonical
 * {@link HodAttendanceReportService}, so every sheet inherits the identical
 * {@code assertOwned} / context checks and is therefore confined to the HOD's
 * own department and context by construction - not by a second implementation of
 * the same rule.</p>
 *
 * <p><b>No sheet re-queries.</b> Three service calls fill the whole workbook:
 * {@code getOverview} feeds sheets 1 and 4, {@code getMatrix} (unpaged) feeds
 * sheets 2 and 5, and {@code getLowAttendance} feeds sheet 3. The count is
 * therefore a constant in both student and subject count - a genuine N+1 would
 * show up here as a count that grows with the population, which
 * {@code HodAttendanceExportQueryCountIntegrationTest} asserts against.</p>
 *
 * <h3>No calculation happens here</h3>
 * <p>Every figure is already computed by the frozen canonical resolver and is
 * copied verbatim into the workbook. This class only chooses which columns to
 * show and in what order; it never re-derives a percentage, a denominator or a
 * threshold. {@code N/A} stays {@code N/A}, and a context with no conducted class
 * produces five sheets that each state that plainly rather than zeros.</p>
 */
@Service
public class HodContextPackExportService {

    /** Sheet 1. */
    public static final String SHEET_EXECUTIVE_SUMMARY = "Executive Summary";
    /** Sheet 2. */
    public static final String SHEET_MATRIX = "Attendance Matrix";
    /** Sheet 3. */
    public static final String SHEET_LOW_ATTENDANCE = "Low Attendance";
    /** Sheet 4. */
    public static final String SHEET_SUBJECT_SUMMARY = "Subject Summary";
    /** Sheet 5. */
    public static final String SHEET_STUDENT_SUMMARY = "Student Summary";

    /** The tab order of the workbook. Every sheet is justified by a Phase 3 DTO. */
    public static final List<String> SHEET_ORDER = List.of(
            SHEET_EXECUTIVE_SUMMARY,
            SHEET_MATRIX,
            SHEET_LOW_ATTENDANCE,
            SHEET_SUBJECT_SUMMARY,
            SHEET_STUDENT_SUMMARY);

    private static final String NOT_APPLICABLE = "N/A";
    private static final String CULPRIT_PREFIX = "    - ";
    private static final String NO_FACULTY = "No faculty assigned";

    private final HodAttendanceReportService attendanceReportService;
    private final HodAttendanceMatrixExportService matrixExportService;
    private final HodAttendanceReportExportService reportExportService;
    private final HodExportHeader exportHeader;
    private final ExcelReportGenerator excelGenerator;

    public HodContextPackExportService(HodAttendanceReportService attendanceReportService,
                                       HodAttendanceMatrixExportService matrixExportService,
                                       HodAttendanceReportExportService reportExportService,
                                       HodExportHeader exportHeader,
                                       ExcelReportGenerator excelGenerator) {
        this.attendanceReportService = attendanceReportService;
        this.matrixExportService = matrixExportService;
        this.reportExportService = reportExportService;
        this.exportHeader = exportHeader;
        this.excelGenerator = excelGenerator;
    }

    /**
     * One generated workbook: its bytes and its deterministic attachment name.
     *
     * <p>Returned together so the name is derived from the context that was just
     * authorized and used, rather than from a second lookup that could describe a
     * different context than the bytes.</p>
     */
    public record ExportedPack(byte[] bytes, String fileName) {
    }

    /**
     * Builds the whole Context Pack.
     *
     * <p>XLSX only: a multi-sheet PDF is deliberately Phase 4C's subject, and
     * shipping a PDF pack now would mean designing pagination before the print
     * chrome exists.</p>
     */
    @Transactional(readOnly = true)
    public ExportedPack export(HodAcademicSelection selection,
                               LocalDate startDate, LocalDate endDate) {
        String start = iso(startDate);
        String end = iso(endDate);
        LocalDateTime generatedAt = LocalDateTime.now();

        // Three calls, reused across five sheets.
        HodAttendanceOverviewDTO overview =
                attendanceReportService.getOverview(selection, start, end);
        HodAttendanceMatrixDTO matrix = attendanceReportService.getMatrix(
                selection, null, start, end,
                0, Integer.MAX_VALUE,
                HodAttendanceReportService.SORT_ENROLLMENT_NUMBER, "asc");
        HodLowAttendanceReportDTO low =
                attendanceReportService.getLowAttendance(selection, start, end);

List<ExportWorkbook.SheetSpec> sheets = List.of(
                // Three service calls, reused across five sheets. The matrix and
                // low-attendance arrangements are handed the DTOs already loaded
                // above rather than being asked to fetch their own, so no sheet
                // costs an extra query.
                ExportWorkbook.sheet(SHEET_EXECUTIVE_SUMMARY,
                        executiveSummaryData(overview, start, end, generatedAt),
                        executiveSummaryStyle()),
                // The cross-tab reuses the matrix export's own arrangement, so
                // the Pack's sheet is the standalone matrix report with the same
                // landscape/fit-to-width layout.
                ExportWorkbook.sheet(SHEET_MATRIX,
                        matrixExportService.matrixData(matrix, null, start, end),
                        HodReportStyles.matrix()),
                // Likewise the threshold report, so the Pack can never rank a
                // student differently from the report a HOD already trusts.
                ExportWorkbook.sheet(SHEET_LOW_ATTENDANCE,
                        reportExportService.lowData(low, start, end),
                        HodReportStyles.lowAttendance()),
                ExportWorkbook.sheet(SHEET_SUBJECT_SUMMARY,
                        subjectSummaryData(overview, start, end, generatedAt),
                        HodReportStyles.overview()),
                ExportWorkbook.sheet(SHEET_STUDENT_SUMMARY,
                        studentSummaryData(matrix, start, end, generatedAt),
                        studentSummaryStyle()));

        byte[] bytes = excelGenerator.generateWorkbook(new ExportWorkbook(sheets));
        return new ExportedPack(bytes,
                HodExportFileNames.build("ContextPack", contextSegment(matrix.getContext()),
                        null, ExportFormat.XLSX, startDate, endDate));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Sheet 1 - Executive Summary
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The cohort at a glance: headline metrics plus the distribution bands.
     *
     * <p>Every metric names the {@code HodAttendanceOverviewDTO} field it comes
     * from. The one derived figure - each band's share of the cohort - is labelled
     * as a percentage and is a division of two numbers already on the sheet, so a
     * reader can check it by eye.</p>
     */
    ExportData executiveSummaryData(HodAttendanceOverviewDTO overview,
                                    String startDate, String endDate,
                                    LocalDateTime generatedAt) {
        HodAttendanceContextDTO context = overview.getContext();
        boolean empty = zeroSafe(overview.getOverallTotalClasses()) == 0L;

        String[] metrics = {
                "Total Students: " + zeroSafe(overview.getTotalStudents()),
                "Total Subjects Reported: " + subjectsOf(overview),
                "Classes Conducted: " + (overview.getClassesConducted() == null
                        ? NOT_APPLICABLE : overview.getClassesConducted()),
                "Total Present: " + zeroSafe(overview.getOverallPresentCount()),
                "Total Classes: " + zeroSafe(overview.getOverallTotalClasses()),
                "Overall Attendance: " + percentageText(overview.getOverallPercentage()),
                "Below " + percentageText(overview.getThresholdPercentage()) + ": "
                        + zeroSafe(overview.getBelowThresholdCount()),
                "Students With No Conducted Class: "
                        + zeroSafe(overview.getNoConductedClasses())};

        HodExportHeader.Block header = (empty
                ? exportHeader.buildEmpty("HOD ATTENDANCE CONTEXT PACK", context,
                startDate, endDate, generatedAt, metrics)
                : exportHeader.build("HOD ATTENDANCE CONTEXT PACK", context,
                startDate, endDate, generatedAt, metrics));

        List<String> headers = List.of("Band", "Label", "Students", "% of Students");
        List<List<Object>> rows = new ArrayList<>();
        long totalStudents = zeroSafe(overview.getTotalStudents());
        for (HodAttendanceDistributionDTO band : overview.getDistribution() == null
                ? List.<HodAttendanceDistributionDTO>of() : overview.getDistribution()) {
            long count = zeroSafe(band.getStudentCount());
            rows.add(List.of(
                    orDash(band.getBand()),
                    orDash(band.getLabel()),
                    count,
                    percentageText(totalStudents > 0
                            ? (count * 100.0) / totalStudents
                            : null)));
        }

        return new ExportData(header.institution(), header.reportTitle(),
                SHEET_EXECUTIVE_SUMMARY, headers, rows, header.metaLines(), false);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Sheet 4 - Subject Summary
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Per-subject performance, from the same overview DTO as sheet 1.
     *
     * <p>Distinct from sheet 1: sheet 1 aggregates the cohort, this aggregates by
     * subject. A HOD asking "which subject is dragging this section down" reads
     * this sheet and no other.</p>
     */
    ExportData subjectSummaryData(HodAttendanceOverviewDTO overview,
                                  String startDate, String endDate,
                                  LocalDateTime generatedAt) {
        HodAttendanceContextDTO context = overview.getContext();
        List<HodAttendanceSubjectSummaryDTO> subjects = overview.getSubjects() == null
                ? List.of() : overview.getSubjects();

        String[] metrics = {
                "Total Subjects Reported: " + subjects.size(),
                "Total Present: " + zeroSafe(overview.getOverallPresentCount()),
                "Total Classes: " + zeroSafe(overview.getOverallTotalClasses()),
                "Overall Attendance: " + percentageText(overview.getOverallPercentage())};

        boolean empty = zeroSafe(overview.getOverallTotalClasses()) == 0L;
        HodExportHeader.Block header = (empty
                ? exportHeader.buildEmpty("SUBJECT SUMMARY", context,
                startDate, endDate, generatedAt, metrics)
                : exportHeader.build("SUBJECT SUMMARY", context,
                startDate, endDate, generatedAt, metrics));

        List<String> headers = List.of(
                "Subject Code", "Subject Name", "Faculty",
                "Total Classes", "Average Attendance", "Students", "Below Threshold");
        List<List<Object>> rows = new ArrayList<>();
        for (HodAttendanceSubjectSummaryDTO subject : subjects) {
            rows.add(List.of(
                    orDash(subject.getSubjectCode()),
                    orDash(subject.getSubjectName()),
                    facultyLabel(subject.getFacultyNames()),
                    zeroSafe(subject.getTotalClasses()),
                    percentageText(subject.getPercentage()),
                    zeroSafe(subject.getStudents()),
                    zeroSafe(subject.getStudentsBelowThreshold())));
        }

        return new ExportData(header.institution(), header.reportTitle(),
                SHEET_SUBJECT_SUMMARY, headers, rows, header.metaLines(), false);
    }

    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Per-student rollup, from the same matrix DTO as sheet 2.
     *
     * <p><b>Not a duplicate of the cross-tab.</b> The Matrix answers a
     * subject-by-subject question and is unusable for a cohort of any size; this
     * is the same students reduced to the five columns a HOD reads down a list,
     * which is the common case ("who is below 75%") that the cross-tab forces you
     * to scan column by column to answer.</p>
     *
     * <p>Totals are the matrix rows' own {@code totalPresent} /
     * {@code totalClasses} / {@code overallPercentage}, copied verbatim - the
     * same figures the cross-tab prints for that student.</p>
     */
    ExportData studentSummaryData(HodAttendanceMatrixDTO matrix,
                                  String startDate, String endDate,
                                  LocalDateTime generatedAt) {
        HodAttendanceContextDTO context = matrix.getContext();
        List<HodAttendanceMatrixRowDTO> students = matrix.getStudents() == null
                ? List.<HodAttendanceMatrixRowDTO>of() : matrix.getStudents();

        String[] metrics = {
                "Total Students: " + students.size(),
                "Threshold: " + percentageText(matrix.getThresholdPercentage())};

        boolean empty = zeroSafe(matrix.getTotalElements()) == 0L;
        HodExportHeader.Block header = (empty
                ? exportHeader.buildEmpty("STUDENT SUMMARY", context,
                startDate, endDate, generatedAt, metrics)
                : exportHeader.build("STUDENT SUMMARY", context,
                startDate, endDate, generatedAt, metrics));

        List<String> headers = List.of(
                "Enrollment No.", "Student Name", "Section",
                "Total Present", "Total Classes", "Overall Attendance %");
        List<List<Object>> rows = new ArrayList<>();
        for (HodAttendanceMatrixRowDTO student : students) {
            rows.add(List.of(
                    enrollment(student.getEnrollmentNumber(), student.getRollNumber()),
                    orDash(student.getStudentName()),
                    orDash(student.getSectionName()),
                    zeroSafe(student.getTotalPresent()),
                    zeroSafe(student.getTotalClasses()),
                    percentageText(student.getOverallPercentage())));
        }

        return new ExportData(header.institution(), header.reportTitle(),
                SHEET_STUDENT_SUMMARY, headers, rows, header.metaLines(), false);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Styles
    // ═══════════════════════════════════════════════════════════════════════

    /** A summary sheet is a label/value list, so it is pinned but not filtered. */
    private static ExcelStyleOptions executiveSummaryStyle() {
        return ExcelStyleOptions.defaults()
                .withPrintSetup(true)
                .withFreezeHeader(true)
                .withRepeatHeaderRows(true)
                .withPercentageHeaders("% of Students")
                .withIntegerHeaders("Students");
    }

    /** The student rollup is a sortable list, and its percentages must sort. */
    private static ExcelStyleOptions studentSummaryStyle() {
        return ExcelStyleOptions.defaults()
                .withPrintSetup(true)
                .withFreezeHeader(true)
                .withRepeatHeaderRows(true)
                .withAutoFilter(true)
                .withPercentageHeaders("Overall Attendance %")
                .withIntegerHeaders("Total Present", "Total Classes");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private static int subjectsOf(HodAttendanceOverviewDTO overview) {
        return overview.getSubjects() == null ? 0 : overview.getSubjects().size();
    }

    /** The context segment for the file name, read back from the resolved context. */
    private static String contextSegment(HodAttendanceContextDTO context) {
        if (context == null) {
            return "";
        }
        return HodExportFileNames.labelledContextSegment(context.getProgramName(),
                context.getSemesterName(), context.getSectionName());
    }

    static String facultyLabel(List<String> facultyNames) {
        if (facultyNames == null || facultyNames.isEmpty()) {
            return NO_FACULTY;
        }
        List<String> present = facultyNames.stream()
                .filter(HodContextPackExportService::notBlank)
                .toList();
        return present.isEmpty() ? NO_FACULTY : String.join(", ", present);
    }

    private static String enrollment(String enrollmentNumber, String rollNumber) {
        if (notBlank(enrollmentNumber)) {
            return enrollmentNumber.trim();
        }
        return notBlank(rollNumber) ? rollNumber.trim() : NOT_APPLICABLE;
    }

    private static String orDash(String value) {
        return notBlank(value) ? value.trim() : NOT_APPLICABLE;
    }

    /**
     * A percentage, or {@code N/A} when there is no denominator.
     *
     * <p>Never a fabricated 0.00% - a student who was never marked in a conducted
     * class has no percentage, and saying 0% would misreport that as attendance.</p>
     */
    static String percentageText(Double percentage) {
        return percentage == null ? NOT_APPLICABLE
                : String.format(Locale.ROOT, "%.2f%%", percentage);
    }

    private static long zeroSafe(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String iso(LocalDate date) {
        return date == null ? null : date.toString();
    }
}