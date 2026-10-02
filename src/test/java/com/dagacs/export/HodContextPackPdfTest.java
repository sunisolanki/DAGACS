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
import com.dagacs.entity.Department;
import com.dagacs.entity.Teacher;
import com.dagacs.security.AuthenticatedHodResolver;
import com.dagacs.service.HodAcademicSelection;
import com.dagacs.service.HodAttendanceReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * Phase 4C.3: the HOD Context Pack as one PDF.
 *
 * <p>4B produced the Pack as a five-sheet workbook. This proves the same five
 * reports as a single PDF: the same sections in the same order, each on its own
 * page, the cross-tab genuinely landscape and the other four portrait, one
 * continuous {@code Page X of Y} across the whole file, and - the property most at
 * risk from a two-pass, merge-based mechanism - <b>exactly the same three
 * canonical service calls the workbook already made</b>.</p>
 *
 * <h3>Why mixed orientation is a merge</h3>
 * <p>OpenPDF 1.3.39 fixes a page's {@code MediaBox} when the page object is
 * created, so one {@code Document} cannot change size part way through. Each
 * section is therefore rendered by the unchanged generator - which already sizes
 * itself from {@link ExportData#landscape()} - and the pages are merged. The
 * assertions below check the result is a <i>real</i> landscape page: width greater
 * than height, and no {@code /Rotate} entry that would only be a viewer trick.</p>
 *
 * <p><b>The canonical service is mocked.</b> This is about presentation and
 * wiring. The arithmetic is proven by
 * {@code HodAttendanceConductedSessionIntegrationTest}, the authorization by
 * {@code HodContextPackSecurityIntegrationTest}, and the query budget by
 * {@code HodAttendanceExportQueryCountIntegrationTest}; keeping those separate is
 * what lets a regression here be identified as a presentation regression.</p>
 */
@ExtendWith(MockitoExtension.class)
class HodContextPackPdfTest {

    private static final String START = "2026-01-01";
    private static final String END = "2026-01-31";
    private static final LocalDate START_DATE = LocalDate.parse(START);
    private static final LocalDate END_DATE = LocalDate.parse(END);

    /** The five sections, in the order the workbook already established. */
    private static final List<String> SECTIONS = List.of(
            "Executive Summary",
            "Attendance Matrix",
            "Low Attendance",
            "Subject Summary",
            "Student Summary");

    @Mock private HodAttendanceReportService attendanceReportService;
    @Mock private AuthenticatedHodResolver hodResolver;

    private final ExcelReportGenerator excelGenerator = new ExcelReportGenerator();
    private final PdfReportGenerator pdfGenerator = new PdfReportGenerator();
    private HodExportHeader header;
    private HodContextPackExportService packService;

    @BeforeEach
    void wire() {
        Department department = Department.builder()
                .id(1L).name("Department of Computer Science & Engineering")
                .code("CSE").createdBy("test").build();
        Teacher hod = Teacher.builder().id(1L).email("hod@dagacs.local")
                .isHod(true).status("ACTIVE").department(department).build();
        lenient().when(hodResolver.resolve()).thenReturn(hod);
        header = new HodExportHeader(new ReportBrandingProperties(), hodResolver);

        packService = new HodContextPackExportService(
                attendanceReportService,
                new HodAttendanceMatrixExportService(attendanceReportService, header,
                        excelGenerator, pdfGenerator),
                new HodAttendanceReportExportService(attendanceReportService, header,
                        excelGenerator, pdfGenerator),
                header,
                excelGenerator,
                new PdfSectionedReportGenerator(pdfGenerator));
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

    // ── Fixtures ────────────────────────────────────────────────────────────

    private static HodAttendanceOverviewDTO overview(int students) {
        return HodAttendanceOverviewDTO.builder()
                .context(context())
                .totalStudents((long) students)
                .overallPresentCount(32L)
                .overallTotalClasses(students == 0 ? 0L : 40L)
                .overallPercentage(students == 0 ? null : 80.0)
                .belowThresholdCount(1L)
                .classesConducted(students == 0 ? null : 4L)
                .thresholdPercentage(75.0)
                .noConductedClasses(0L)
                .distribution(students == 0 ? List.of() : List.of(
                        HodAttendanceDistributionDTO.builder()
                                .band("90-100").label("Excellent")
                                .studentCount(1L).build()))
                .subjects(students == 0 ? List.of() : List.of(
                        HodAttendanceSubjectSummaryDTO.builder()
                                .subjectId(101L).subjectCode("CS301")
                                .subjectName("Data Structures")
                                .facultyNames(List.of("Dr Rao"))
                                .classesConducted(40L).presentCount(32L)
                                .totalClasses(40L).percentage(80.0)
                                .students((long) students)
                                .studentsBelowThreshold(1L).build()))
                .build();
    }

    private static HodAttendanceMatrixDTO matrix(int students) {
        List<HodAttendanceMatrixRowDTO> rows = new ArrayList<>();
        for (int i = 0; i < students; i++) {
            rows.add(HodAttendanceMatrixRowDTO.builder()
                    .studentId(90L + i)
                    .enrollmentNumber(String.format("CS2026%04d", i))
                    .rollNumber("R" + i)
                    .studentName("Student Number " + i)
                    .sectionName("A")
                    .subjects(List.of(HodAttendanceMatrixCellDTO.builder()
                            .subjectId(101L).present(32L).total(40L)
                            .percentage(80.0).build()))
                    .totalPresent(32L).totalClasses(40L)
                    .overallPercentage(80.0).build());
        }
        return HodAttendanceMatrixDTO.builder()
                .context(context())
                .subjects(students == 0 ? List.of() : List.of(
                        HodAttendanceMatrixColumnDTO.builder()
                                .subjectId(101L).subjectCode("CS301")
                                .subjectName("Data Structures")
                                .facultyNames(List.of("Dr Rao")).build()))
                .students(rows)
                .page(0).size(Integer.MAX_VALUE)
                .totalElements((long) students).totalPages(1)
                .singleSubject(false).thresholdPercentage(75.0).build();
    }

    private static HodLowAttendanceReportDTO low(int students) {
        return HodLowAttendanceReportDTO.builder()
                .context(context()).thresholdPercentage(75.0)
                .totalStudents(students).belowThresholdCount(1L)
                .students(students == 0 ? List.of() : List.of(
                        HodLowAttendanceStudentDTO.builder()
                                .studentId(93L).enrollmentNumber("E-003").rollNumber("R3")
                                .studentName("Carol").sectionName("A")
                                .programName("B.Tech CSE").semesterName("Semester 3")
                                .academicSessionName("Academic Session 2026-27")
                                .presentCount(3L).totalClasses(6L).percentage(50.0)
                                .subjectsBelowThreshold(1L)
                                .belowThresholdSubjects(List.of(
                                        HodLowAttendanceSubjectDTO.builder()
                                                .subjectId(101L).subjectCode("CS301")
                                                .subjectName("Data Structures")
                                                .present(1L).total(4L)
                                                .percentage(25.0).build()))
                                .build()))
                .build();
    }

    private void stub(int students) {
        lenient().when(attendanceReportService.getOverview(any(), any(), any()))
                .thenReturn(overview(students));
        lenient().when(attendanceReportService.getMatrix(any(), any(), any(), any(),
                anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(matrix(students));
        lenient().when(attendanceReportService.getLowAttendance(any(), any(), any()))
                .thenReturn(low(students));
    }

    private byte[] renderPack(int students) {
        stub(students);
        return packService.exportPdf(selection(), START_DATE, END_DATE).bytes();
    }

    // ── PDF inspection ──────────────────────────────────────────────────────

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

    private static String flat(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    private static String flat(byte[] pdf) throws Exception {
        return flat(String.join(" ", pageTexts(pdf)));
    }

    private static int pageCount(byte[] pdf) throws Exception {
        try (com.lowagie.text.pdf.PdfReader reader =
                     new com.lowagie.text.pdf.PdfReader(pdf)) {
            return reader.getNumberOfPages();
        }
    }

    private static boolean isLandscape(byte[] pdf, int page) throws Exception {
        try (com.lowagie.text.pdf.PdfReader reader =
                     new com.lowagie.text.pdf.PdfReader(pdf)) {
            com.lowagie.text.Rectangle size = reader.getPageSize(page);
            return size.getWidth() > size.getHeight();
        }
    }

    private static String magic(byte[] bytes) {
        return new String(java.util.Arrays.copyOf(bytes, 5),
                java.nio.charset.StandardCharsets.US_ASCII);
    }

    /** The 1-based page a section's heading appears on, or -1 when absent. */
    private static int pageOfSection(List<String> pages, String section) {
        for (int i = 0; i < pages.size(); i++) {
            if (flat(pages.get(i)).contains("Attendance Context Pack - " + section)) {
                return i + 1;
            }
        }
        return -1;
    }

    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("A/B. the five sections exist, in the workbook's order")
    void fiveSectionsInOrder() throws Exception {
        byte[] pdf = renderPack(2);

        assertEquals("%PDF-", magic(pdf), "the Pack must be a real PDF");
        List<String> pages = pageTexts(pdf);
        String all = flat(String.join(" ", pages));

        int previous = -1;
        for (String section : SECTIONS) {
            int page = pageOfSection(pages, section);
            assertTrue(page > 0, "the " + section + " section must be present");
            assertTrue(page > previous,
                    "the " + section + " section must come after the previous one; "
                            + "found it on page " + page + " after page " + previous);
            previous = page;
            assertTrue(all.contains(section), "the section name must be printed");
        }
    }

    @Test
    @DisplayName("C. each section starts on its own page")
    void eachSectionStartsOnANewPage() throws Exception {
        List<String> pages = pageTexts(renderPack(2));

        int seen = 0;
        for (String text : pages) {
            String flat = flat(text);
            int onThisPage = 0;
            for (String section : SECTIONS) {
                if (flat.contains("Attendance Context Pack - " + section)) {
                    onThisPage++;
                }
            }
            assertTrue(onThisPage <= 1,
                    "page " + (seen + 1) + " carries " + onThisPage
                            + " section headings: a section must not run on into the "
                            + "next one, and two must not share a page");
            seen++;
        }

        // Five sections, each on a page of its own, in order.
        int previous = 0;
        for (String section : SECTIONS) {
            int page = pageOfSection(pages, section);
            assertTrue(page > previous,
                    section + " must start on a page after the previous section's");
            previous = page;
        }
    }

    @Test
    @DisplayName("D/E. the matrix section is landscape, the other four are portrait")
    void matrixIsLandscapeAndTheRestArePortrait() throws Exception {
        List<String> pages = pageTexts(renderPack(2));

        int matrixPage = pageOfSection(pages, "Attendance Matrix");
        assertTrue(matrixPage > 0, "the matrix section must be present");

        assertTrue(isLandscape(renderPack(2), matrixPage),
                "the cross-tab section must be a genuinely landscape page");

        for (String section : List.of("Executive Summary", "Low Attendance",
                "Subject Summary", "Student Summary")) {
            int page = pageOfSection(pages, section);
            assertTrue(page > 0, section + " must be present");
            assertFalse(isLandscape(renderPack(2), page),
                    section + " must stay portrait - only the cross-tab is wide");
        }
    }

    @Test
    @DisplayName("E2. the landscape page is wide, not a rotated portrait page")
    void landscapeIsRealNotRotated() throws Exception {
        byte[] pdf = renderPack(2);
        int matrixPage = pageOfSection(pageTexts(pdf), "Attendance Matrix");

        try (com.lowagie.text.pdf.PdfReader reader =
                     new com.lowagie.text.pdf.PdfReader(pdf)) {
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                assertEquals(0, reader.getPageSize(page).getRotation(),
                        "page " + page + " must carry no /Rotate entry: a rotated "
                                + "portrait page is not a landscape page");
            }
            com.lowagie.text.Rectangle wide = reader.getPageSize(matrixPage);
            assertTrue(wide.getWidth() > wide.getHeight(),
                    "the matrix section must have width > height, was "
                            + wide.getWidth() + "x" + wide.getHeight());
            com.lowagie.text.Rectangle rotated = reader.getPageSizeWithRotation(matrixPage);
            assertTrue(rotated.getWidth() > rotated.getHeight(),
                    "the matrix section must still be landscape once rotation applies");
        }
    }

    @Test
    @DisplayName("F. Page X of Y is continuous across the whole document")
    void pageNumbersAreContinuous() throws Exception {
        // Enough students that the matrix section alone spans several pages, so a
        // per-section numbering scheme would be caught rather than tolerated.
        byte[] pdf = renderPack(60);
        int total = pageCount(pdf);
        assertTrue(total > 3, "the fixture must span several pages, got " + total);

        List<String> pages = pageTexts(pdf);
        for (int i = 0; i < pages.size(); i++) {
            String flat = flat(pages.get(i));
            assertTrue(flat.contains("Page " + (i + 1) + " of " + total),
                    "page " + (i + 1) + " must read 'Page " + (i + 1) + " of " + total
                            + "', but read: ["
                            + flat.substring(Math.max(0, flat.length() - 70)) + "]");
            // A section must never restart its own numbering.
            assertFalse(flat.contains("Page 1 of 1"),
                    "page " + (i + 1) + " must not be numbered as its own document");
        }
    }

    @Test
    @DisplayName("G. the footer names the pack, the section and the academic context")
    void footerCarriesTheContext() throws Exception {
        List<String> pages = pageTexts(renderPack(2));

        for (int i = 0; i < pages.size(); i++) {
            String flat = flat(pages.get(i));
            assertTrue(flat.contains("Attendance Context Pack"),
                    "page " + (i + 1) + " must name the pack");
            assertTrue(flat.contains("Page " + (i + 1) + " of "),
                    "page " + (i + 1) + " must carry its page number");
        }
        // The academic context must be printed, on the first page of the file.
        String first = flat(pages.get(0));
        assertAll(
                () -> assertTrue(first.contains("Academic Session 2026-27"), first),
                () -> assertTrue(first.contains("B.Tech CSE"), first),
                () -> assertTrue(first.contains("Semester 3"), first),
                () -> assertTrue(first.contains("2026-01-01"), first),
                () -> assertTrue(first.contains("2026-01-31"), first),
                () -> assertTrue(first.contains(
                        "Department of Computer Science & Engineering"), first));
    }

    @Test
    @DisplayName("H. an empty context renders all five sections and invents nothing")
    void emptyContextRendersSafely() throws Exception {
        byte[] pdf = renderPack(0);

        assertEquals("%PDF-", magic(pdf));
        List<String> pages = pageTexts(pdf);
        for (String section : SECTIONS) {
            assertTrue(pageOfSection(pages, section) > 0,
                    "even with nothing conducted, the " + section + " section must "
                            + "be printed rather than crash or vanish");
        }
        String all = flat(String.join(" ", pages));
        assertTrue(all.contains("No rows for the selected criteria."),
                "an empty section must say why it is empty");
        assertTrue(all.contains("No attendance sessions were conducted"),
                "the empty state must keep the established wording");
        assertFalse(all.contains("0.00%"),
                "a context with no conducted class must never report a fabricated 0%");
    }

    @Test
    @DisplayName("I. a long cross-tab neither crashes nor loses its last row")
    void longContentSurvives() throws Exception {
        byte[] pdf = renderPack(120);
        int total = pageCount(pdf);
        assertTrue(total > 5, "120 students must span several pages, got " + total);

        String all = flat(String.join(" ", pageTexts(pdf)));
        // The very last student of the fixture must still be printed, so a page
        // that silently dropped its tail would be caught.
        assertTrue(all.contains("CS20260119"),
                "the last student of a 120-row cross-tab must be printed");
        for (int i = 1; i <= total; i++) {
            assertTrue(flat(pdf).contains("Page " + i + " of " + total),
                    "page " + i + " must be numbered in a 120-student pack");
        }
    }

    @Test
    @DisplayName("J. the canonical figures and identity survive into the PDF")
    void existingDataIsPreserved() throws Exception {
        String all = flat(renderPack(2));

        assertAll(
                () -> assertTrue(all.contains("HOD ATTENDANCE CONTEXT PACK"), all),
                () -> assertTrue(all.contains("80.00%"),
                        "the canonical percentage must be printed verbatim"),
                () -> assertTrue(all.contains("E-003"),
                        "the low-attendance student's enrolment number must appear"),
                () -> assertTrue(all.contains("CS301"), "subject codes must appear"),
                () -> assertTrue(all.contains("Data Structures"),
                        "subject names must appear"),
                () -> assertTrue(all.contains("Dr Rao"),
                        "real faculty must appear"),
                () -> assertTrue(all.contains("Carol"),
                        "the low-attendance student's name must appear"),
                () -> assertTrue(all.contains("Student Number 1"),
                        "the cross-tab must contain the canonical students"));
    }

    @Test
    @DisplayName("K. the file is a valid, readable PDF")
    void isAValidReadablePdf() throws Exception {
        byte[] pdf = renderPack(2);
        assertEquals("%PDF-", magic(pdf));

        try (com.lowagie.text.pdf.PdfReader reader =
                     new com.lowagie.text.pdf.PdfReader(pdf)) {
            assertEquals(pageCount(pdf), reader.getNumberOfPages());
            assertTrue(reader.getNumberOfPages() >= 5,
                    "five sections must occupy at least five pages, got "
                            + reader.getNumberOfPages());
        }
    }

    @Test
    @DisplayName("L. the attachment name is deterministic and follows the 4B convention")
    void fileNameIsDeterministic() {
        stub(2);
        String first = packService.exportPdf(selection(), START_DATE, END_DATE).fileName();
        String second = packService.exportPdf(selection(), START_DATE, END_DATE).fileName();

        assertEquals(first, second, "the same context must always produce the same name");
        assertAll(
                () -> assertTrue(first.startsWith("DAGACS_Attendance_ContextPack"),
                        first),
                () -> assertTrue(first.endsWith(".pdf"), first),
                () -> assertTrue(first.contains("2026-01-01") && first.contains("2026-01-31"),
                        "the name must describe the applied range: " + first));
    }

    @Test
    @DisplayName("M. the PDF loads each canonical report exactly once, like the workbook")
    void pdfCostsTheSameThreeServiceCalls() {
        stub(2);
        packService.exportPdf(selection(), START_DATE, END_DATE);

        // The very same budget the 4B workbook already proved: three calls fill
        // all five sections, and rendering adds none.
        verify(attendanceReportService, times(1))
                .getOverview(any(), any(), any());
        verify(attendanceReportService, times(1))
                .getMatrix(any(), any(), any(), any(), anyInt(), anyInt(),
                        anyString(), anyString());
        verify(attendanceReportService, times(1))
                .getLowAttendance(any(), any(), any());
    }

    @Test
    @DisplayName("M2. the matrix is requested unpaged, as the workbook already does")
    void matrixIsAlwaysUnpaged() {
        stub(2);
        packService.exportPdf(selection(), START_DATE, END_DATE);

        verify(attendanceReportService, times(1))
                .getMatrix(any(), any(), any(), any(), anyInt(), anyInt(),
                        anyString(), anyString());
        org.mockito.ArgumentCaptor<Integer> size =
                org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(attendanceReportService, times(1))
                .getMatrix(any(), any(), any(), any(), anyInt(), size.capture(),
                        anyString(), anyString());
        assertEquals(Integer.MAX_VALUE, size.getValue(),
                "a pack is a file-generation operation: the cross-tab must never be "
                        + "truncated to one page of students");
    }

    @Test
    @DisplayName("N. the spreadsheet Pack is unchanged by the PDF being added")
    void workbookIsUnaffected() throws Exception {
        stub(2);
        byte[] xlsx = packService.export(selection(), START_DATE, END_DATE).bytes();
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                             new ByteArrayInputStream(xlsx))) {
            assertEquals(5, workbook.getNumberOfSheets());
            for (int i = 0; i < SECTIONS.size(); i++) {
                assertEquals(SECTIONS.get(i), workbook.getSheetName(i),
                        "sheet " + (i + 1) + " must be exactly what 4B established");
            }
        }
    }
}
