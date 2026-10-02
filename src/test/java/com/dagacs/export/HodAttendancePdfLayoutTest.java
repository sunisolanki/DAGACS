package com.dagacs.export;

import com.dagacs.config.ReportBrandingProperties;
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
import com.dagacs.entity.Department;
import com.dagacs.entity.Teacher;
import com.dagacs.security.AuthenticatedHodResolver;
import com.dagacs.service.HodAcademicSelection;
import com.dagacs.service.HodAttendanceReportService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4C.2: the HOD PDF reports are wired to the 4C.1 rendering infrastructure.
 *
 * <p>4C.1 built the layout options, the footer and the two-pass page counting, and
 * proved they work. What it deliberately did not do is change a single PDF that a
 * user receives. This class closes that gap: it proves the five HOD PDFs now
 * carry {@code Page X of Y} with a total that is genuinely correct, that their
 * orientation is still owned by the data model, and - the property most at risk
 * from a two-pass mechanism - that resolving the page total costs
 * <b>no additional database call</b>.</p>
 *
 * <h3>Why the query-count assertion matters most</h3>
 * <p>Rendering twice is the obvious way to introduce a performance regression into
 * an export. It is safe here only because both passes consume one already-built
 * {@link ExportData}: the canonical report service is still consulted exactly once
 * per export, whichever format is requested. {@link #pdfAndExcelCostTheSameNumberOfServiceCalls()}
 * is that guarantee, asserted directly rather than inferred.</p>
 *
 * <p><b>The canonical service is mocked.</b> This is about rendering and wiring.
 * The arithmetic is proven exhaustively by
 * {@code HodAttendanceConductedSessionIntegrationTest}, and the authorization by
 * {@code HodAttendanceExportSecurityIntegrationTest}; keeping those separate is
 * what lets a rendering regression be identified as a rendering regression.</p>
 */
@ExtendWith(MockitoExtension.class)
class HodAttendancePdfLayoutTest {

    private static final String START = "2026-01-01";
    private static final String END = "2026-01-31";
    private static final LocalDate START_DATE = LocalDate.parse(START);
    private static final LocalDate END_DATE = LocalDate.parse(END);

    @Mock private HodAttendanceReportService attendanceReportService;
    @Mock private AuthenticatedHodResolver hodResolver;

    private final ExcelReportGenerator excelGenerator = new ExcelReportGenerator();
    private final PdfReportGenerator pdfGenerator = new PdfReportGenerator();
    private HodExportHeader header;
    private HodAttendanceReportExportService reportExportService;
    private HodAttendanceMatrixExportService matrixExportService;

    @BeforeEach
    void wire() {
        Department department = Department.builder()
                .id(1L).name("Department of Computer Science & Engineering")
                .code("CSE").createdBy("test").build();
        Teacher hod = Teacher.builder().id(1L).email("hod@dagacs.local")
                .isHod(true).status("ACTIVE").department(department).build();
        lenient().when(hodResolver.resolve()).thenReturn(hod);
        header = new HodExportHeader(new ReportBrandingProperties(), hodResolver);

        reportExportService = new HodAttendanceReportExportService(
                attendanceReportService, header, excelGenerator, pdfGenerator);
        matrixExportService = new HodAttendanceMatrixExportService(
                attendanceReportService, header, excelGenerator, pdfGenerator);
    }

    private static HodAcademicSelection selection() {
        return new HodAcademicSelection(1L, 2L, 3L, 4L);
    }

    private static HodAttendanceContextDTO context() {
        return HodAttendanceContextDTO.builder()
                .academicSessionId(1L).academicSessionName("Academic Session 2026-27")
                .programId(2L).programName("B.Tech CSE")
                .semesterId(3L).semesterName("Semester 3")
                .sectionId(4L).sectionName("A").complete(true).build();
    }

    // ── Fixtures ─────────────────────────────────────────────────────────

    private static HodAttendanceOverviewDTO overview(int students) {
        return HodAttendanceOverviewDTO.builder()
                .context(context()).totalStudents((long) students)
                .overallPresentCount(32L).overallTotalClasses(40L).overallPercentage(80.0)
                .belowThresholdCount(1L).classesConducted(4L).thresholdPercentage(75.0)
                .noConductedClasses(0L)
                .distribution(List.of(HodAttendanceDistributionDTO.builder()
                        .band("90-100").label("Excellent").studentCount(1L).build()))
                .subjects(List.of(HodAttendanceSubjectSummaryDTO.builder()
                        .subjectId(101L).subjectCode("CS301").subjectName("Data Structures")
                        .facultyNames(List.of("Dr Rao")).classesConducted(40L)
                        .presentCount(32L).totalClasses(40L).percentage(80.0)
                        .students((long) students).studentsBelowThreshold(1L).build()))
                .build();
    }

    /** A matrix whose row count can be grown to force multiple PDF pages. */
    private static HodAttendanceMatrixDTO matrix(int students) {
        List<HodAttendanceMatrixRowDTO> rows = new ArrayList<>();
        for (int i = 0; i < students; i++) {
            rows.add(HodAttendanceMatrixRowDTO.builder()
                    .studentId(90L + i)
                    .enrollmentNumber(String.format("CS2025%04d", i))
                    .rollNumber("R" + i)
                    .studentName("Student Number " + i)
                    .sectionName("A")
                    .subjects(List.of(HodAttendanceMatrixCellDTO.builder()
                            .subjectId(101L).present(32L).total(40L).percentage(80.0).build()))
                    .totalPresent(32L).totalClasses(40L).overallPercentage(80.0).build());
        }
        return HodAttendanceMatrixDTO.builder()
                .context(context())
                .subjects(List.of(HodAttendanceMatrixColumnDTO.builder()
                        .subjectId(101L).subjectCode("CS301").subjectName("Data Structures")
                        .facultyNames(List.of("Dr Rao")).build()))
                .students(rows)
                .page(0).size(Integer.MAX_VALUE)
                .totalElements((long) students).totalPages(1)
                .singleSubject(false).thresholdPercentage(75.0).build();
    }

    private static HodLowAttendanceReportDTO low(int students) {
        return HodLowAttendanceReportDTO.builder()
                .context(context()).thresholdPercentage(75.0)
                .totalStudents(students).belowThresholdCount((long) students)
                .students(List.of(HodLowAttendanceStudentDTO.builder()
                        .studentId(93L).enrollmentNumber("E-003").rollNumber("R3")
                        .studentName("Carol").sectionName("A").programName("B.Tech CSE")
                        .semesterName("Semester 3")
                        .presentCount(3L).totalClasses(6L).percentage(50.0)
                        .subjectsBelowThreshold(1L)
                        .belowThresholdSubjects(List.of(
                                HodLowAttendanceSubjectDTO.builder()
                                        .subjectId(101L).subjectCode("CS301")
                                        .subjectName("Data Structures")
                                        .present(1L).total(4L).percentage(25.0).build()))
                        .build()))
                .build();
    }

    private static HodStudentAttendanceDetailDTO student(int subjects) {
        List<HodStudentSubjectAttendanceDTO> list = new ArrayList<>();
        for (int i = 0; i < subjects; i++) {
            list.add(HodStudentSubjectAttendanceDTO.builder()
                    .subjectId(101L + i).subjectCode("CS" + (301 + i))
                    .subjectName("Subject " + i + " with a deliberately long name")
                    .classes(40L).present(32L).notAttended(8L).percentage(80.0).build());
        }
        return HodStudentAttendanceDetailDTO.builder()
                .context(context()).studentId(91L).enrollmentNumber("CS2025001")
                .rollNumber("R1").studentName("Rahul").programName("B.Tech CSE")
                .semesterName("Semester 3").sectionName("A").subjects(list)
                .totalPresent(32L).totalClasses(40L).overallPercentage(80.0).build();
    }

    private static HodSubjectAttendanceDetailDTO subject(int students) {
        List<HodSubjectAttendanceDetailRowDTO> rows = new ArrayList<>();
        for (int i = 0; i < students; i++) {
            rows.add(HodSubjectAttendanceDetailRowDTO.builder()
                    .studentId(90L + i)
                    .enrollmentNumber(String.format("CS2025%04d", i))
                    .rollNumber("R" + i).studentName("Student Number " + i)
                    .present(32L).total(40L).percentage(80.0).build());
        }
        return HodSubjectAttendanceDetailDTO.builder()
                .context(context()).subjectId(101L).subjectCode("CS301")
                .subjectName("Data Structures").programName("B.Tech CSE")
                .semesterName("Semester 3").sectionName("A")
                .facultyNames(List.of("Dr Rao")).totalClasses(40L)
                .students((long) students).presentCount(32L).totalClassesAcrossStudents(40L)
                .averageAttendance(80.0).studentsBelowThreshold(0L)
                .thresholdPercentage(75.0).studentRows(rows).build();
    }

    private void stubStandard(int students) {
        lenient().when(attendanceReportService.getOverview(any(), any(), any()))
                .thenReturn(overview(students));
        lenient().when(attendanceReportService.getMatrix(any(), any(), any(), any(),
                anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(matrix(students));
        lenient().when(attendanceReportService.getLowAttendance(any(), any(), any()))
                .thenReturn(low(students));
        lenient().when(attendanceReportService.getStudentDetail(any(), anyLong0(), any(),
                any(), any())).thenReturn(student(students));
        lenient().when(attendanceReportService.getSubjectDetail(any(), anyLong0(),
                any(), any())).thenReturn(subject(students));
    }

    private static long anyLong0() {
        return org.mockito.ArgumentMatchers.anyLong();
    }

    // ── PDF inspection helpers ────────────────────────────────────────────

    private static List<String> pageTexts(byte[] pdf) throws Exception {
        com.lowagie.text.pdf.PdfReader reader =
                new com.lowagie.text.pdf.PdfReader(new ByteArrayInputStream(pdf));
        try {
            com.lowagie.text.pdf.parser.PdfTextExtractor extractor =
                    new com.lowagie.text.pdf.parser.PdfTextExtractor(reader);
            List<String> texts = new ArrayList<>();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                texts.add(extractor.getTextFromPage(page));
            }
            return texts;
        } finally {
            reader.close();
        }
    }

    private static int countPages(byte[] pdf) throws Exception {
        com.lowagie.text.pdf.PdfReader reader =
                new com.lowagie.text.pdf.PdfReader(new ByteArrayInputStream(pdf));
        try {
            return reader.getNumberOfPages();
        } finally {
            reader.close();
        }
    }

    /**
     * Extracted text with all whitespace runs collapsed to single spaces.
     *
     * <p>Necessary because {@code PdfTextExtractor} inserts a newline wherever the
     * text was laid out across lines - and with content-derived column widths a
     * narrow column legitimately wraps its own heading ("Enrollment" / "No.").
     * Asserting on raw extracted text would therefore fail on correct output, and
     * the test would be measuring the extractor's line breaking rather than the
     * report's content.</p>
     */
    private static String flat(byte[] pdf) throws Exception {
        return String.join(" ", pageTexts(pdf)).replaceAll("\\s+", " ").trim();
    }

    private static boolean isLandscape(byte[] pdf) throws Exception {
        com.lowagie.text.pdf.PdfReader reader =
                new com.lowagie.text.pdf.PdfReader(new ByteArrayInputStream(pdf));
        try {
            var size = reader.getPageSize(1);
            return size.getWidth() > size.getHeight();
        } finally {
            reader.close();
        }
    }

    private static String magic(byte[] bytes) {
        return new String(Arrays.copyOf(bytes, 5), java.nio.charset.StandardCharsets.US_ASCII);
    }

    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1. all five HOD PDFs are wired to the 4C.1 footer and layout")
    void allFiveHodPdfsCarryTheFooter() throws Exception {
        stubStandard(2);

        byte[] overview = reportExportService.exportOverview(
                ExportFormat.PDF, selection(), START_DATE, END_DATE).bytes();
        byte[] student = reportExportService.exportStudent(
                ExportFormat.PDF, selection(), 91L, null, START_DATE, END_DATE).bytes();
        byte[] subject = reportExportService.exportSubject(
                ExportFormat.PDF, selection(), 101L, START_DATE, END_DATE).bytes();
        byte[] low = reportExportService.exportLow(
                ExportFormat.PDF, selection(), START_DATE, END_DATE).bytes();
        byte[] matrix = matrixExportService.export(
                ExportFormat.PDF, selection(), null, START, END);

        for (String name : List.of("overview", "student", "subject", "low", "matrix")) {
            byte[] pdf = switch (name) {
                case "overview" -> overview;
                case "student" -> student;
                case "subject" -> subject;
                case "low" -> low;
                default -> matrix;
            };
            String text = flat(pdf);
            assertTrue(text.contains("Page 1 of "),
                    "the " + name + " PDF must be produced with the 4C.1 footer");
        }
    }

    @Test
    @DisplayName("2. Page X of Y is correct across every page of a multi-page HOD PDF")
    void pageNumbersAreCorrectOnEveryPage() throws Exception {
        stubStandard(120);

        byte[] pdf = matrixExportService.export(
                ExportFormat.PDF, selection(), null, START, END);
        int total = countPages(pdf);
        assertTrue(total > 1, "120 students must span several pages, got " + total);

        List<String> pages = pageTexts(pdf);
        for (int i = 0; i < pages.size(); i++) {
            assertTrue(pages.get(i).contains("Page " + (i + 1) + " of " + total),
                    "page " + (i + 1) + " must read 'Page " + (i + 1) + " of "
                            + total + "', but read: " + pages.get(i).substring(
                            Math.max(0, pages.get(i).length() - 60)));
        }
    }

    @Test
    @DisplayName("3. a single-page HOD PDF stays valid and reads Page 1 of 1")
    void singlePagePdfIsValid() throws Exception {
        stubStandard(2);

        byte[] pdf = reportExportService.exportOverview(
                ExportFormat.PDF, selection(), START_DATE, END_DATE).bytes();

        assertEquals("%PDF-", magic(pdf));
        assertEquals(1, countPages(pdf));
        assertTrue(String.join("\n", pageTexts(pdf)).contains("Page 1 of 1"),
                "a one-page document must say so rather than look truncated");
    }

    @Test
    @DisplayName("4. the existing HOD report content is preserved")
    void reportContentIsPreserved() throws Exception {
        stubStandard(2);

        String text = flat(
                reportExportService.exportStudent(
                        ExportFormat.PDF, selection(), 91L, null, START_DATE, END_DATE).bytes());

        assertAll(
                () -> assertTrue(text.contains("STUDENT ATTENDANCE REPORT"),
                        "the report title must survive"),
                () -> assertTrue(text.contains("Student Name: Rahul"),
                        "the student identity must survive"),
                () -> assertTrue(text.contains("Enrollment No: CS2025001")),
                () -> assertTrue(text.contains("Program: B.Tech CSE"),
                        "the academic context must survive"),
                () -> assertTrue(text.contains("Semester 3")),
                () -> assertTrue(text.contains("Section: A")),
                () -> assertTrue(text.contains("2026-01-01") && text.contains("2026-01-31"),
                        "the date range must survive"),
                () -> assertTrue(text.contains("Department of Computer Science & Engineering"),
                        "the real department must survive"),
                () -> assertTrue(text.contains("Subject Code")),
                () -> assertTrue(text.contains("TOTAL"),
                        "the total row must survive"),
                () -> assertTrue(text.contains("80.00%"),
                        "the percentage must survive verbatim"));
    }

    @Test
    @DisplayName("4b. N/A is never turned into a percentage")
    void naIsNeverFabricated() throws Exception {
        when(attendanceReportService.getStudentDetail(any(), anyLong0(), any(), any(), any()))
                .thenReturn(HodStudentAttendanceDetailDTO.builder()
                        .context(context()).studentId(91L).enrollmentNumber("CS2025001")
                        .rollNumber("R1").studentName("Apoorva").programName("B.Tech CSE")
                        .semesterName("Semester 3").sectionName("A")
                        .subjects(List.of(HodStudentSubjectAttendanceDTO.builder()
                                .subjectId(101L).subjectCode("CS301").subjectName("Never Held")
                                .classes(0L).present(0L).notAttended(0L)
                                .percentage(null).build()))
                        .totalPresent(0L).totalClasses(0L).overallPercentage(null).build());

        String text = flat(
                reportExportService.exportStudent(
                        ExportFormat.PDF, selection(), 91L, null, START_DATE, END_DATE).bytes());

        assertTrue(text.contains("N/A"),
                "a subject with no conducted class must report N/A");
        assertFalse(text.contains("0.00%"),
                "no denominator must never become a fabricated zero");
    }

    @Test
    @DisplayName("5. the matrix PDF stays landscape and the other four stay portrait")
    void orientationIsOwnedByTheDataModel() throws Exception {
        stubStandard(2);

        assertTrue(isLandscape(matrixExportService.export(
                ExportFormat.PDF, selection(), null, START, END)),
                "the wide cross-tab must remain landscape");

        assertFalse(isLandscape(reportExportService.exportOverview(
                ExportFormat.PDF, selection(), START_DATE, END_DATE).bytes()),
                "the 7-column overview must stay portrait");
        assertFalse(isLandscape(reportExportService.exportLow(
                ExportFormat.PDF, selection(), START_DATE, END_DATE).bytes()),
                "the low-attendance report must stay portrait");
        assertFalse(isLandscape(reportExportService.exportStudent(
                ExportFormat.PDF, selection(), 91L, null, START_DATE, END_DATE).bytes()),
                "the student report must stay portrait");
        assertFalse(isLandscape(reportExportService.exportSubject(
                ExportFormat.PDF, selection(), 101L, START_DATE, END_DATE).bytes()),
                "the subject report must stay portrait");
    }

    @Test
    @DisplayName("6. the layout changes actually reach the rendered document")
    void layoutChangesAreApplied() throws Exception {
        stubStandard(2);

        byte[] professional = reportExportService.exportOverview(
                ExportFormat.PDF, selection(), START_DATE, END_DATE).bytes();
        byte[] frozenDefault = pdfGenerator.generate(
                reportExportService.overviewData(selection(), START, END));

        assertEquals("%PDF-", magic(professional));
        assertFalse(Arrays.equals(professional, frozenDefault),
                "the professional layout must actually change the rendered document");
        assertFalse(flat(frozenDefault).contains("Page 1 of"),
                "the frozen default must still carry no footer");
    }

    @Test
    @DisplayName("7. an empty HOD report states why, and fabricates no rows")
    void emptyReportStatesItsReason() throws Exception {
        when(attendanceReportService.getLowAttendance(any(), any(), any()))
                .thenReturn(HodLowAttendanceReportDTO.builder()
                        .context(context()).thresholdPercentage(75.0)
                        .totalStudents(0).belowThresholdCount(0L)
                        .students(List.of()).build());
        stubMatrixAndOverview();

        String text = flat(
                reportExportService.exportLow(
                        ExportFormat.PDF, selection(), START_DATE, END_DATE).bytes());

        assertTrue(text.contains("No rows for the selected criteria."),
                "an empty report must say why rather than present a blank table");
        assertTrue(text.contains("Enrollment No."),
                "the column headings are still shown so the reader knows what was "
                        + "asked for. Rendered text was: [" + text + "]");
    }

    private void stubMatrixAndOverview() {
        lenient().when(attendanceReportService.getOverview(any(), any(), any()))
                .thenReturn(overview(0));
        lenient().when(attendanceReportService.getMatrix(any(), any(), any(), any(),
                anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(matrix(0));
    }

    @Test
    @DisplayName("8. resolving the page total costs no extra service call")
    void pdfAndExcelCostTheSameNumberOfServiceCalls() {
        stubStandard(2);

        reportExportService.exportOverview(
                ExportFormat.XLSX, selection(), START_DATE, END_DATE);
        verify(attendanceReportService, times(1)).getOverview(any(), any(), any());

        // A fresh mock history for the PDF, so the two are measured independently.
        org.mockito.Mockito.clearInvocations(attendanceReportService);

        reportExportService.exportOverview(
                ExportFormat.PDF, selection(), START_DATE, END_DATE);
        // Two renders happen - the counting pass and the final pass - but the
        // canonical service must still be consulted exactly once. This is the
        // whole safety argument for two-pass rendering.
        verify(attendanceReportService, times(1)).getOverview(any(), any(), any());
    }

    @Test
    @DisplayName("8b. the same holds for the matrix, whose page total is the real risk")
    void matrixPdfAndExcelCostTheSameNumberOfServiceCalls() {
        stubStandard(2);

        matrixExportService.export(
                ExportFormat.PDF, selection(), null, START, END);
        verify(attendanceReportService, times(1))
                .getMatrix(any(), any(), any(), any(), anyInt(), anyInt(),
                        anyString(), anyString());
    }

    @Test
    @DisplayName("9. Excel exports are untouched by the PDF wiring")
    void excelExportsAreUnaffected() throws Exception {
        stubStandard(2);

        byte[] xlsx = reportExportService.exportOverview(
                ExportFormat.XLSX, selection(), START_DATE, END_DATE).bytes();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertEquals(1, workbook.getNumberOfSheets());
            assertEquals("Subject Summary", workbook.getSheetName(0),
                    "the Excel sheet name must be exactly what Phase 4B established");
        }
    }

    @Test
    @DisplayName("10. attachment names are unchanged by the PDF wiring")
    void fileNamesAreUnchanged() {
        stubStandard(2);

        String overview = reportExportService.exportOverview(
                ExportFormat.PDF, selection(), START_DATE, END_DATE).fileName();
        String low = reportExportService.exportLow(
                ExportFormat.PDF, selection(), START_DATE, END_DATE).fileName();
        String matrix = matrixExportService.fileName(
                ExportFormat.PDF, START_DATE, END_DATE);

        assertAll(
                () -> assertTrue(overview.startsWith("DAGACS_Attendance_Overview"),
                        overview),
                () -> assertTrue(overview.endsWith(".pdf")),
                () -> assertTrue(low.startsWith("DAGACS_Attendance_Low")),
                () -> assertEquals(
                        "dagacs_hod_attendance_matrix_2026-01-01_to_2026-01-31.pdf", matrix,
                        "the matrix keeps its Phase 4A name verbatim"));
    }
}
