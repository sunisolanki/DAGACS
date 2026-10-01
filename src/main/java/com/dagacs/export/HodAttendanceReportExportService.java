package com.dagacs.export;

import com.dagacs.dto.HodAttendanceContextDTO;
import com.dagacs.dto.HodAttendanceOverviewDTO;
import com.dagacs.dto.HodAttendanceSubjectSummaryDTO;
import com.dagacs.dto.HodLowAttendanceReportDTO;
import com.dagacs.dto.HodLowAttendanceStudentDTO;
import com.dagacs.dto.HodLowAttendanceSubjectDTO;
import com.dagacs.dto.HodStudentAttendanceDetailDTO;
import com.dagacs.dto.HodStudentSubjectAttendanceDTO;
import com.dagacs.dto.HodSubjectAttendanceDetailDTO;
import com.dagacs.dto.HodSubjectAttendanceDetailRowDTO;
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
 * Phase 4A: the export orchestrator for the HOD attendance reports that had no
 * export at all - the overview, the student report, the subject report and the
 * low-attendance report.
 *
 * <p><b>Read-only and additive.</b> It reuses {@link HodAttendanceReportService}
 * for the data and the frozen {@link ExcelReportGenerator} /
 * {@link PdfReportGenerator} for the rendering, so no attendance rule and no
 * generator behaviour is duplicated or re-implemented here. The Excel and the PDF
 * of a report are produced from the <b>same</b> {@link ExportData} instance, which
 * is what guarantees the two files can never disagree about a single attendance
 * number.</p>
 *
 * <p><b>No calculation happens here.</b> Every figure - the denominators (the
 * conducted classes), the row totals and the percentages - arrives already
 * computed by the single canonical resolver in
 * {@link HodAttendanceReportService}. This class only arranges those values into
 * the export table, which is exactly why neither file can drift from the
 * on-screen report, and why the Phase 3 denominator is structurally untouchable
 * from here.</p>
 *
 * <p><b>Context isolation.</b> Each method calls the same canonical service
 * method the on-screen report uses, so it performs the identical
 * {@code assertOwned} / {@code assertStudentInContext} / {@code assertSubjectInContext}
 * authorization. An export therefore can never be broader than the screen that
 * produced it, and a cross-department, cross-program, cross-session or
 * cross-section export is impossible.</p>
 *
 * <p><b>Unpaged by construction.</b> The report services return whole result sets,
 * so an export always contains every row of the selected context rather than one
 * page of it. Each report costs one service call and a constant number of grouped
 * queries - never one query per row.</p>
 */
@Service
public class HodAttendanceReportExportService {

    /** Indent marker for a low-attendance culprit sub-row. */
    private static final String CULPRIT_PREFIX = "    - ";

    private static final String NO_FACULTY = "No faculty assigned";
    private static final String NOT_APPLICABLE = "N/A";

    private final HodAttendanceReportService attendanceReportService;
    private final HodExportHeader exportHeader;
    private final ExcelReportGenerator excelGenerator;
    private final PdfReportGenerator pdfGenerator;

    public HodAttendanceReportExportService(HodAttendanceReportService attendanceReportService,
                                           HodExportHeader exportHeader,
                                           ExcelReportGenerator excelGenerator,
                                           PdfReportGenerator pdfGenerator) {
        this.attendanceReportService = attendanceReportService;
        this.exportHeader = exportHeader;
        this.excelGenerator = excelGenerator;
        this.pdfGenerator = pdfGenerator;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Public entry points
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * One generated report: its bytes and the deterministic attachment name.
     *
     * <p>Returned together so the name can be built from the very report data that
     * was already fetched - the resolved context, the real enrolment number, the
     * real subject code. Deriving the name separately would mean a second
     * authorized service call and a second set of queries purely to name a file,
     * and would risk a name describing a context the bytes are not from.</p>
     */
    public record ExportedFile(byte[] bytes, String fileName) {
    }

    @Transactional(readOnly = true)
    public ExportedFile exportOverview(ExportFormat format, HodAcademicSelection selection,
                                       LocalDate startDate, LocalDate endDate) {
        ExportData data = overviewData(selection, iso(startDate), iso(endDate));
        return new ExportedFile(render(format, data, HodReportStyles.overview()),
                overviewFileName(format, data, startDate, endDate));
    }

    @Transactional(readOnly = true)
    public ExportedFile exportStudent(ExportFormat format, HodAcademicSelection selection,
                                      Long studentId, Long subjectId,
                                      LocalDate startDate, LocalDate endDate) {
        ExportData data = studentData(selection, studentId, subjectId,
                iso(startDate), iso(endDate));
        return new ExportedFile(render(format, data, HodReportStyles.student()),
                studentFileName(format, data, enrollmentOf(data), startDate, endDate));
    }

    @Transactional(readOnly = true)
    public ExportedFile exportSubject(ExportFormat format, HodAcademicSelection selection,
                                      Long subjectId,
                                      LocalDate startDate, LocalDate endDate) {
        ExportData data = subjectData(selection, subjectId, iso(startDate), iso(endDate));
        return new ExportedFile(render(format, data, HodReportStyles.subject()),
                subjectFileName(format, data, subjectCodeOf(data), startDate, endDate));
    }

    @Transactional(readOnly = true)
    public ExportedFile exportLow(ExportFormat format, HodAcademicSelection selection,
                                  LocalDate startDate, LocalDate endDate) {
        ExportData data = lowData(selection, iso(startDate), iso(endDate));
        return new ExportedFile(render(format, data, HodReportStyles.lowAttendance()),
                lowAttendanceFileName(format, data, startDate, endDate));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Deterministic attachment names
    // ═══════════════════════════════════════════════════════════════════════

    String overviewFileName(ExportFormat format, ExportData data,
                            LocalDate startDate, LocalDate endDate) {
        return HodExportFileNames.build("Overview", contextSegment(data), null,
                format, startDate, endDate);
    }

    String studentFileName(ExportFormat format, ExportData data, String enrollmentNumber,
                           LocalDate startDate, LocalDate endDate) {
        return HodExportFileNames.build("Student", contextSegment(data), enrollmentNumber,
                format, startDate, endDate);
    }

    String subjectFileName(ExportFormat format, ExportData data, String subjectCode,
                           LocalDate startDate, LocalDate endDate) {
        return HodExportFileNames.build("Subject", contextSegment(data), subjectCode,
                format, startDate, endDate);
    }

    String lowAttendanceFileName(ExportFormat format, ExportData data,
                                 LocalDate startDate, LocalDate endDate) {
        return HodExportFileNames.build("Low", contextSegment(data), null,
                format, startDate, endDate);
    }

    /**
     * The context segment, read back from the context lines of the report that was
     * just built, so the name and the body can never describe different contexts.
     */
    private static String contextSegment(ExportData data) {
        String program = null;
        String semester = null;
        String section = null;
        for (String line : data.metaLines()) {
            if (line == null) {
                continue;
            }
            if (line.startsWith("Program: ")) {
                program = line.substring("Program: ".length());
            } else if (line.startsWith("Semester: ")) {
                semester = line.substring("Semester: ".length());
            } else if (line.startsWith("Section: ")) {
                section = line.substring("Section: ".length());
            }
        }
        return HodExportFileNames.labelledContextSegment(program, semester, section);
    }

    private static String iso(LocalDate date) {
        return date == null ? null : date.toString();
    }

    /** The student's own enrolment number, read back from the report's own header. */
    private static String enrollmentOf(ExportData data) {
        return metaValue(data, "Enrollment No: ");
    }

    /** The subject's own code, read back from the report's own header. */
    private static String subjectCodeOf(ExportData data) {
        return metaValue(data, "Subject Code: ");
    }

    private static String metaValue(ExportData data, String prefix) {
        for (String line : data.metaLines()) {
            if (line != null && line.startsWith(prefix)) {
                return line.substring(prefix.length()).trim();
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Attendance overview -> subject summary table
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The overview export.
     *
     * <p>The headline metrics go in the header block - they are context about the
     * whole report, not rows of it - and the single table is the per-subject
     * summary, which is the actionable content. Faculty comes from the real
     * teaching assignments carried on the summary rows; a subject with none says
     * so and is never given an invented teacher.</p>
     */
    ExportData overviewData(HodAcademicSelection selection,
                            String startDate, String endDate) {
        HodAttendanceOverviewDTO overview = attendanceReportService
                .getOverview(selection, startDate, endDate);
        HodAttendanceContextDTO context = overview.getContext();
        LocalDateTime generatedAt = LocalDateTime.now();

        List<HodAttendanceSubjectSummaryDTO> subjects = overview.getSubjects() == null
                ? List.of() : overview.getSubjects();

        List<String> headline = new ArrayList<>(List.of(
                "Total Students: " + zeroSafe(overview.getTotalStudents()),
                "Classes Conducted: " + (overview.getClassesConducted() == null
                        ? NOT_APPLICABLE : overview.getClassesConducted()),
                "Total Present: " + zeroSafe(overview.getOverallPresentCount()),
                "Total Classes: " + zeroSafe(overview.getOverallTotalClasses()),
                "Overall Attendance: " + percentageText(overview.getOverallPercentage()),
                "Below " + percentageText(overview.getThresholdPercentage()) + ": "
                        + zeroSafe(overview.getBelowThresholdCount()),
                "Students with no conducted class: " + zeroSafe(overview.getNoConductedClasses())));

        boolean empty = zeroSafe(overview.getOverallTotalClasses()) == 0L;
        HodExportHeader.Block header = (empty
                ? exportHeader.buildEmpty("HOD ATTENDANCE OVERVIEW", context,
                startDate, endDate, generatedAt)
                : exportHeader.build("HOD ATTENDANCE OVERVIEW", context,
                startDate, endDate, generatedAt));

        // The headline metrics read as context about the whole report, so they sit
        // above the shared academic-context block rather than as table rows.
        List<String> metaLines = new ArrayList<>(headline);
        metaLines.addAll(header.metaLines());

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
                "Subject Summary", headers, rows, metaLines, false);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Student attendance
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The student report.
     *
     * <p>The identity block sits in the header and the table is the subject
     * breakdown followed by a total row. The total row repeats the DTO's own
     * {@code totalPresent} / {@code totalClasses} / {@code overallPercentage}
     * verbatim - the very same values the matrix shows for this student - so the
     * report cannot present a different formula from the cross-tab.</p>
     */
    ExportData studentData(HodAcademicSelection selection, Long studentId, Long subjectId,
                           String startDate, String endDate) {
        HodStudentAttendanceDetailDTO detail = attendanceReportService
                .getStudentDetail(selection, studentId, subjectId, startDate, endDate);
        HodAttendanceContextDTO context = detail.getContext();
        LocalDateTime generatedAt = LocalDateTime.now();

        List<HodStudentSubjectAttendanceDTO> subjects = detail.getSubjects() == null
                ? List.of() : detail.getSubjects();

        String[] identity = {
                "Student Name: " + orDash(detail.getStudentName()),
                "Enrollment No: " + enrollment(detail.getEnrollmentNumber(), detail.getRollNumber()),
                "Program: " + orDash(detail.getProgramName()),
                "Semester: " + orDash(detail.getSemesterName()),
                "Section: " + orDash(detail.getSectionName())};

        boolean empty = zeroSafe(detail.getTotalClasses()) == 0L;
        HodExportHeader.Block header = (empty
                ? exportHeader.buildEmpty("STUDENT ATTENDANCE REPORT", context,
                startDate, endDate, generatedAt, identity)
                : exportHeader.build("STUDENT ATTENDANCE REPORT", context,
                startDate, endDate, generatedAt, identity));

        List<String> headers = List.of(
                "Subject Code", "Subject", "Present", "Total Classes", "Attendance %");

        List<List<Object>> rows = new ArrayList<>();
        for (HodStudentSubjectAttendanceDTO subject : subjects) {
            rows.add(List.of(
                    orDash(subject.getSubjectCode()),
                    orDash(subject.getSubjectName()),
                    zeroSafe(subject.getPresent()),
                    zeroSafe(subject.getClasses()),
                    percentageText(subject.getPercentage())));
        }
        // The total row is the DTO's own figure, never a re-derivation.
        int totalRowIndex = rows.size();
        rows.add(List.of("", "TOTAL",
                zeroSafe(detail.getTotalPresent()),
                zeroSafe(detail.getTotalClasses()),
                percentageText(detail.getOverallPercentage())));

        // Phase 4B marks the total so the renderer can emphasise it. This is
        // presentation only: the row's values are the DTO's, exactly as in 4A.
        return new ExportData(header.institution(), header.reportTitle(),
                "Student Attendance", headers, rows, header.metaLines(), false)
                .withTotalRows(totalRowIndex);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Subject attendance
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The subject report.
     *
     * <p>The full subject header (code, name, faculty, program, semester,
     * section, session) sits in the header block and the table is the student
     * roster. {@code averageAttendance} is echoed verbatim from the DTO rather
     * than recomputed from the rows, so the printed average is by construction the
     * same number the on-screen report shows.</p>
     */
    ExportData subjectData(HodAcademicSelection selection, Long subjectId,
                           String startDate, String endDate) {
        HodSubjectAttendanceDetailDTO detail = attendanceReportService
                .getSubjectDetail(selection, subjectId, startDate, endDate);
        HodAttendanceContextDTO context = detail.getContext();
        LocalDateTime generatedAt = LocalDateTime.now();

        List<HodSubjectAttendanceDetailRowDTO> students = detail.getStudentRows() == null
                ? List.of() : detail.getStudentRows();

        String[] identity = {
                "Subject Code: " + orDash(detail.getSubjectCode()),
                "Subject Name: " + orDash(detail.getSubjectName()),
                "Faculty: " + facultyLabel(detail.getFacultyNames()),
                "Program: " + orDash(detail.getProgramName()),
                "Semester: " + orDash(detail.getSemesterName()),
                "Section: " + orDash(detail.getSectionName()),
                "Students: " + zeroSafe(detail.getStudents()),
                "Present: " + zeroSafe(detail.getPresentCount()),
                "Total Classes Across Students: " + zeroSafe(detail.getTotalClassesAcrossStudents()),
                "Average Attendance: " + percentageText(detail.getAverageAttendance()),
                "Students Below Threshold: " + zeroSafe(detail.getStudentsBelowThreshold())};

        boolean empty = zeroSafe(detail.getTotalClasses()) == 0L;
        HodExportHeader.Block header = (empty
                ? exportHeader.buildEmpty("SUBJECT ATTENDANCE REPORT", context,
                startDate, endDate, generatedAt, identity)
                : exportHeader.build("SUBJECT ATTENDANCE REPORT", context,
                startDate, endDate, generatedAt, identity));

        List<String> headers = List.of(
                "Enrollment No.", "Student Name", "Present", "Total Classes",
                "Attendance %", "Below Threshold");

        List<List<Object>> rows = new ArrayList<>();
        for (HodSubjectAttendanceDetailRowDTO row : students) {
            boolean below = row.getPercentage() != null
                    && row.getPercentage() < HodAttendanceReportService.LOW_ATTENDANCE_THRESHOLD;
            rows.add(List.of(
                    enrollment(row.getEnrollmentNumber(), row.getRollNumber()),
                    orDash(row.getStudentName()),
                    zeroSafe(row.getPresent()),
                    zeroSafe(row.getTotal()),
                    percentageText(row.getPercentage()),
                    below ? "Yes" : "No"));
        }

        return new ExportData(header.institution(), header.reportTitle(),
                "Subject Attendance", headers, rows, header.metaLines(), false);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Low attendance
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * The low-attendance report.
     *
     * <p>One student per primary row, followed by one indented sub-row per subject
     * responsible for that student's shortfall. The sub-row's Present / Total
     * Classes / Attendance % columns carry the <i>subject's</i> figures, and its
     * "Subjects Below Threshold" cell is blank, so the two grains stay visually
     * distinct in both the spreadsheet and the PDF. A subject with no conducted
     * class never produces a sub-row: it is not the reason the student is low, and
     * a fabricated 0% entry would invent data.</p>
     */
    ExportData lowData(HodAcademicSelection selection, String startDate, String endDate) {
        return lowData(attendanceReportService.getLowAttendance(selection, startDate, endDate),
                startDate, endDate);
    }

    /**
     * Arranges an already-loaded low-attendance report onto the export table.
     *
     * <p>Phase 4B: separated from the loading so the Context Pack - which needs
     * this arrangement for one of its five sheets - can hand over the report it
     * already fetched instead of making the database produce it a second time.
     * Re-querying per sheet is exactly the per-sheet N+1 this project forbids.</p>
     *
     * <p>Pure arrangement: the threshold, the ranking and every percentage come
     * from the DTO, so the Pack's sheet cannot disagree with the standalone
     * report.</p>
     */
    ExportData lowData(HodLowAttendanceReportDTO report,
                       String startDate, String endDate) {
        HodAttendanceContextDTO context = report.getContext();
        LocalDateTime generatedAt = LocalDateTime.now();

        List<HodLowAttendanceStudentDTO> students = report.getStudents() == null
                ? List.of() : report.getStudents();

        String[] summary = {
                "Total Students: " + zeroSafe(report.getTotalStudents()),
                "Students Below Threshold: " + zeroSafe(report.getBelowThresholdCount()),
                "Threshold: " + percentageText(report.getThresholdPercentage())};

        boolean empty = students.isEmpty();
        HodExportHeader.Block header = (empty
                ? exportHeader.buildEmpty("LOW ATTENDANCE REPORT", context,
                startDate, endDate, generatedAt, summary)
                : exportHeader.build("LOW ATTENDANCE REPORT", context,
                startDate, endDate, generatedAt, summary));

        List<String> metaLines = new ArrayList<>(header.metaLines());
        metaLines.add("An indented \"- Subject\" line under a student shows that "
                + "subject's own Present / Total Classes / Attendance %.");

        List<String> headers = List.of(
                "Enrollment No.", "Student Name", "Program", "Semester", "Section",
                "Present", "Total Classes", "Attendance %", "Subjects Below Threshold");

        List<List<Object>> rows = new ArrayList<>();
        for (HodLowAttendanceStudentDTO student : students) {
            String enrollment = enrollment(student.getEnrollmentNumber(), student.getRollNumber());
            rows.add(List.of(
                    enrollment,
                    orDash(student.getStudentName()),
                    orDash(student.getProgramName()),
                    orDash(student.getSemesterName()),
                    orDash(student.getSectionName()),
                    zeroSafe(student.getPresentCount()),
                    zeroSafe(student.getTotalClasses()),
                    percentageText(student.getPercentage()),
                    zeroSafe(student.getSubjectsBelowThreshold())));

            List<HodLowAttendanceSubjectDTO> culprits = student.getBelowThresholdSubjects() == null
                    ? List.of() : student.getBelowThresholdSubjects();
            for (HodLowAttendanceSubjectDTO culprit : culprits) {
                rows.add(List.of(
                        enrollment,
                        CULPRIT_PREFIX + orDash(culprit.getSubjectName())
                                + (notBlank(culprit.getSubjectCode())
                                ? " (" + culprit.getSubjectCode().trim() + ")" : ""),
                        "", "", "",
                        zeroSafe(culprit.getPresent()),
                        zeroSafe(culprit.getTotal()),
                        percentageText(culprit.getPercentage()),
                        ""));
            }
        }

        return new ExportData(header.institution(), header.reportTitle(),
                "Low Attendance", headers, rows, metaLines, false);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private byte[] render(ExportFormat format, ExportData data) {
        return render(format, data, ExcelStyleOptions.defaults());
    }

    /**
     * Renders one report in the requested format, from the single
     * {@link ExportData} instance both formats share.
     *
     * <p>Phase 4B applies the professional Excel formatting. The PDF path is
     * deliberately untouched: {@code PdfReportGenerator} is Phase 4C's subject,
     * and its chrome, pagination and page numbering are explicitly out of scope
     * here. Keeping the two branches separate is what makes that boundary
     * enforceable rather than aspirational.</p>
     *
     * <p>The data is identical in both formats, which is what guarantees an Excel
     * file and a PDF of the same report can never disagree about a number.</p>
     */
    private byte[] render(ExportFormat format, ExportData data, ExcelStyleOptions options) {
        return switch (format) {
            case XLSX -> excelGenerator.generate(data, options);
            // Phase 4A keeps the frozen generators exactly as they are; the
            // professional print/PDF chrome is Phase 4C's scope.
            case PDF -> pdfGenerator.generate(data);
        };
    }

    /** Real teaching assignments, or an explicit statement that there are none. */
    static String facultyLabel(List<String> facultyNames) {
        if (facultyNames == null || facultyNames.isEmpty()) {
            return NO_FACULTY;
        }
        List<String> present = facultyNames.stream()
                .filter(HodAttendanceReportExportService::notBlank)
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
     * A percentage, or {@code N/A} when the denominator is zero.
     *
     * <p>Never a fabricated 0.00% - the Phase 3 rule that no class was conducted
     * means the percentage is genuinely unavailable.</p>
     */
    static String percentageText(Double percentage) {
        return percentage == null ? NOT_APPLICABLE
                : String.format(Locale.ROOT, "%.2f%%", percentage);
    }

    /** Null-safe for both {@code Long} and {@code Integer} DTO counters. */
    private static long zeroSafe(Number value) {
        return value == null ? 0L : value.longValue();
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
