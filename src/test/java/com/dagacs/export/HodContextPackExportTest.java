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
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 4B: the Context Pack workbook.
 *
 * <p>The Pack exists to answer the five questions a HOD asks of one academic
 * context in a single download. This class proves it is a real multi-sheet
 * workbook with those five sheets in the promised order, that each sheet carries
 * the academic context and the numbers the canonical service produced, and that
 * no sheet invents a metric or a calculation.</p>
 *
 * <p><b>The canonical service is mocked, deliberately.</b> What is under test is
 * the <i>arrangement</i>: which sheets exist, what they contain, in what order,
 * and how they are formatted. The arithmetic is proven exhaustively and
 * separately by {@code HodAttendanceConductedSessionIntegrationTest}. Mocking
 * here keeps the two concerns independent - if the Pack ever re-derived a
 * percentage, that would need a real service to notice, which is a different
 * test's job.
</p>
 */
@ExtendWith(MockitoExtension.class)
class HodContextPackExportTest {

    private static final String START = "2026-01-01";
    private static final String END = "2026-01-31";

    @Mock private HodAttendanceReportService attendanceReportService;
    @Mock private AuthenticatedHodResolver hodResolver;

    private final ExcelReportGenerator excelGenerator = new ExcelReportGenerator();
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
                        excelGenerator, new PdfReportGenerator()),
                new HodAttendanceReportExportService(attendanceReportService, header,
                        excelGenerator, new PdfReportGenerator()),
                header,
                excelGenerator);
    }

    private static HodAcademicSelection selection() {
        return new HodAcademicSelection(1L, 2L, 3L, 4L);
    }

    private static HodAttendanceContextDTO context() {
        return HodAttendanceContextDTO.builder()
                .academicSessionId(1L).academicSessionName("Academic Session 2026-27")
                .programId(2L).programName("B.Tech CSE")
                .semesterId(3L).semesterName("Semester 3")
                .sectionId(4L).sectionName("A")
                .complete(true)
                .build();
    }

    // ── Fixtures ─────────────────────────────────────────────────────────

    private static HodAttendanceOverviewDTO overview(boolean withData) {
        List<HodAttendanceSubjectSummaryDTO> subjects = withData
                ? List.of(subject(101L, "CS301", "Data Structures", List.of("Dr Rao"),
                40L, 32L, 40L, 80.0, 3L, 1L))
                : List.of();
        return HodAttendanceOverviewDTO.builder()
                .context(context())
                .totalStudents(withData ? 3L : 0L)
                .overallPresentCount(withData ? 32L : 0L)
                .overallTotalClasses(withData ? 40L : 0L)
                .overallPercentage(withData ? 80.0 : null)
                .belowThresholdCount(withData ? 1L : 0L)
                .classesConducted(withData ? 4L : null)
.thresholdPercentage(75.0)
                .noConductedClasses(0L)
                // An empty context has no bands to report; fabricating two would
                // be exactly the invented data this report must not contain.
                .distribution(withData
                        ? List.of(
                        HodAttendanceDistributionDTO.builder()
                                .band("90-100").label("Excellent").studentCount(1L).build(),
                        HodAttendanceDistributionDTO.builder()
                                .band("N/A").label("No conducted class").studentCount(0L).build())
                        : List.of())
                .subjects(subjects)
                .build();
    }

    private static HodAttendanceSubjectSummaryDTO subject(long id, String code, String name,
                                                         List<String> faculty, long classes,
                                                         long present, long total,
                                                         Double percentage,
                                                         long students, long below) {
        return HodAttendanceSubjectSummaryDTO.builder()
                .subjectId(id).subjectCode(code).subjectName(name).facultyNames(faculty)
                .classesConducted(classes).presentCount(present)
                .totalClasses(total).percentage(percentage)
                .students(students).studentsBelowThreshold(below)
                .build();
    }

    private static HodAttendanceMatrixDTO matrix(boolean withData) {
        List<HodAttendanceMatrixColumnDTO> columns = withData
                ? List.of(HodAttendanceMatrixColumnDTO.builder()
                .subjectId(101L).subjectCode("CS301").subjectName("Data Structures")
                .facultyNames(List.of("Dr Rao")).build())
                : List.of();
        List<HodAttendanceMatrixRowDTO> students = withData
                ? List.of(
                HodAttendanceMatrixRowDTO.builder()
                        .studentId(91L).enrollmentNumber("CS2025001").rollNumber("R1")
                        .studentName("Rahul").sectionName("A")
                        .subjects(List.of(HodAttendanceMatrixCellDTO.builder()
                                .subjectId(101L).present(32L).total(40L).percentage(80.0).build()))
                        .totalPresent(32L).totalClasses(40L).overallPercentage(80.0).build(),
                // The canonical "no conducted class" student: N/A, never 0%.
                HodAttendanceMatrixRowDTO.builder()
                        .studentId(92L).enrollmentNumber("CS2025003").rollNumber("R3")
                        .studentName("Apoorva").sectionName("A")
                        .subjects(List.of(HodAttendanceMatrixCellDTO.builder()
                                .subjectId(101L).present(0L).total(0L).percentage(null).build()))
                        .totalPresent(0L).totalClasses(0L).overallPercentage(null).build())
                : List.of();
        return HodAttendanceMatrixDTO.builder()
                .context(context()).subjects(columns).students(students)
                .page(0).size(Integer.MAX_VALUE)
                .totalElements(withData ? 2L : 0L).totalPages(1)
                .singleSubject(false).thresholdPercentage(75.0)
                .build();
    }

    private static HodLowAttendanceReportDTO low(boolean withData) {
        List<HodLowAttendanceStudentDTO> students = withData
                ? List.of(HodLowAttendanceStudentDTO.builder()
                .studentId(93L).enrollmentNumber("E-003").rollNumber("R3")
                .studentName("Carol").sectionName("A").programName("B.Tech CSE")
                .semesterName("Semester 3").academicSessionName("Academic Session 2026-27")
                .presentCount(3L).totalClasses(6L).percentage(50.0)
                .subjectsBelowThreshold(1L)
                .belowThresholdSubjects(List.of(
                        HodLowAttendanceSubjectDTO.builder()
                                .subjectId(101L).subjectCode("CS301")
                                .subjectName("Data Structures")
                                .present(1L).total(4L).percentage(25.0).build()))
                .build())
                : List.of();
        return HodLowAttendanceReportDTO.builder()
                .context(context()).thresholdPercentage(75.0)
                .totalStudents(withData ? 3 : 0).belowThresholdCount(withData ? 1L : 0L)
                .students(students)
                .build();
    }

    private void stubAll(boolean withData) {
        lenient().when(attendanceReportService.getOverview(any(), any(), any()))
                .thenReturn(overview(withData));
        lenient().when(attendanceReportService.getMatrix(any(), any(), any(), any(),
                anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(matrix(withData));
        lenient().when(attendanceReportService.getLowAttendance(any(), any(), any()))
                .thenReturn(low(withData));
    }

    private XSSFWorkbook renderPack(boolean withData) throws IOException {
        stubAll(withData);
        byte[] bytes = packService.export(selection(),
                java.time.LocalDate.parse(START), java.time.LocalDate.parse(END)).bytes();
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    /** The first data row of a sheet, i.e. the row below its header row. */
    private static int firstDataRow(Sheet sheet) {
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || row.getLastCellNum() < 2) {
                continue;
            }
            Cell a = row.getCell(0);
            Cell b = row.getCell(1);
            if (a != null && b != null && b.getCellType() == CellType.STRING
                    && "Band".equals(a.getStringCellValue())) {
                return r + 1;
            }
        }
        throw new AssertionError("no data row found");
    }

    private static String text(Row row, int column) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(column);
        if (cell == null) {
            return null;
        }
        return cell.getCellType() == CellType.NUMERIC
                ? String.valueOf(cell.getNumericCellValue())
                : cell.getStringCellValue();
    }

    private static List<String> metaText(Sheet sheet) {
        List<String> lines = new ArrayList<>();
        for (int r = 0; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null || row.getCell(0) == null) {
                continue;
            }
            if (row.getCell(1) != null && row.getCell(1).getCellType() == CellType.STRING) {
                // A header row: stop collecting meta lines.
                break;
            }
            lines.add(row.getCell(0).getStringCellValue());
        }
        return lines;
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Workbook structure")
    class Structure {

        @Test
        @DisplayName("contains exactly the five promised sheets, in order")
        void fiveSheetsInOrder() throws IOException {
            try (XSSFWorkbook workbook = renderPack(true)) {
                assertEquals(5, workbook.getNumberOfSheets(),
                        "the Pack is five sheets, no more and no fewer");
                assertEquals(List.of("Executive Summary", "Attendance Matrix",
                        "Low Attendance", "Subject Summary", "Student Summary"),
                        List.of(workbook.getSheetName(0), workbook.getSheetName(1),
                                workbook.getSheetName(2), workbook.getSheetName(3),
                                workbook.getSheetName(4)));
                assertEquals(HodContextPackExportService.SHEET_ORDER.size(),
                        workbook.getNumberOfSheets(),
                        "the rendered workbook matches the declared sheet order");
            }
        }

@Test
        @DisplayName("every sheet carries the academic context and the date range")
        void everySheetIdentifiesItsContext() throws IOException {
            try (XSSFWorkbook workbook = renderPack(true)) {
                for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                    final int index = s;
                    List<String> meta = metaText(workbook.getSheetAt(s));
                    assertAll(
                            () -> assertTrue(meta.stream().anyMatch(
                                    l -> l.startsWith("Program: B.Tech CSE")),
                                    workbook.getSheetName(index) + " must name the program"),
                            () -> assertTrue(meta.stream().anyMatch(
                                    l -> l.contains("Semester 3")),
                                    workbook.getSheetName(index) + " must name the semester"),
                            () -> assertTrue(meta.stream().anyMatch(
                                    l -> l.contains("Section: A")),
                                    workbook.getSheetName(index) + " must name the section"),
                            () -> assertTrue(meta.stream().anyMatch(
                                    l -> l.contains("2026-01-01") && l.contains("2026-01-31")),
                                    workbook.getSheetName(index) + " must state the date range"),
                            () -> assertTrue(meta.stream().anyMatch(
                                    l -> l.startsWith("Department: Department of Computer Science")),
                                    workbook.getSheetName(index)
                                            + " must name the real department"));
                }
            }
        }

        @Test
        @DisplayName("is a valid workbook that Excel can reopen")
        void opensAsAValidWorkbook() throws IOException {
            stubAll(true);
            byte[] bytes = packService.export(selection(),
                    java.time.LocalDate.parse(START), java.time.LocalDate.parse(END)).bytes();
            assertTrue(bytes.length > 0);
            // A zip container: the .xlsx signature POI itself writes.
            assertEquals('P', bytes[0]);
            assertEquals('K', bytes[1]);
            try (XSSFWorkbook reopened = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
                assertEquals(5, reopened.getNumberOfSheets());
            }
        }

        @Test
        @DisplayName("the file name is deterministic and describes the context and range")
        void fileNameIsDeterministic() {
            stubAll(true);
            var first = packService.export(selection(),
                    java.time.LocalDate.parse(START), java.time.LocalDate.parse(END));
            var second = packService.export(selection(),
                    java.time.LocalDate.parse(START), java.time.LocalDate.parse(END));
            assertEquals(first.fileName(), second.fileName(),
                    "the same context must always produce the same file name");
            assertAll(
                    () -> assertTrue(first.fileName().startsWith("DAGACS_Attendance_ContextPack")),
                    () -> assertTrue(first.fileName().contains("2026-01-01")),
                    () -> assertTrue(first.fileName().endsWith(".xlsx")));
        }
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Sheet contents come from the canonical data")
    class Contents {

        @Test
        @DisplayName("the executive summary states the canonical headline metrics")
        void executiveSummaryUsesTheOverview() throws IOException {
            try (XSSFWorkbook workbook = renderPack(true)) {
                List<String> meta = metaText(workbook.getSheet("Executive Summary"));
                assertAll(
                        () -> assertTrue(meta.stream().anyMatch(
                                l -> l.equals("Total Students: 3"))),
                        () -> assertTrue(meta.stream().anyMatch(
                                l -> l.equals("Total Present: 32"))),
                        () -> assertTrue(meta.stream().anyMatch(
                                l -> l.equals("Total Classes: 40"))),
                        () -> assertTrue(meta.stream().anyMatch(
                                l -> l.equals("Overall Attendance: 80.00%"))),
                        () -> assertTrue(meta.stream().anyMatch(
                                l -> l.startsWith("Below 75.00%: ")),
                                "the threshold is the DTO's, never re-declared"));
            }
        }

        @Test
        @DisplayName("the executive summary lists the canonical distribution bands")
        void executiveSummaryListsBands() throws IOException {
            try (XSSFWorkbook workbook = renderPack(true)) {
                Sheet sheet = workbook.getSheet("Executive Summary");
                Row first = sheet.getRow(firstDataRow(sheet));
                assertEquals("90-100", text(first, 0));
                assertEquals("Excellent", text(first, 1));
                assertEquals(1.0, first.getCell(2).getNumericCellValue(), 0.0001);
            }
        }

        @Test
        @DisplayName("the matrix sheet is the cross-tab, with the canonical overall figure")
        void matrixSheetIsTheCrossTab() throws IOException {
            try (XSSFWorkbook workbook = renderPack(true)) {
                Sheet sheet = workbook.getSheet("Attendance Matrix");
                assertNotNull(sheet);
                int headerRow = 0;
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row != null && row.getCell(0) != null
                            && "Enrollment No.".equals(row.getCell(0).getStringCellValue())) {
                        headerRow = r;
                        break;
                    }
                }
                Row header = sheet.getRow(headerRow);
                assertAll(
                        () -> assertEquals("CS301 - Data Structures", text(header, 2)),
                        () -> assertEquals("Total Present", text(header, 3)),
                        () -> assertEquals("Total Classes", text(header, 4)),
                        () -> assertEquals("Overall Attendance %", text(header, 5)));

                Row first = sheet.getRow(headerRow + 1);
                assertEquals("CS2025001", text(first, 0));
                assertEquals("32 / 40", text(first, 2));
            }
        }

        @Test
        @DisplayName("the student summary repeats the matrix row totals verbatim")
        void studentSummaryCopiesTheMatrixTotals() throws IOException {
            try (XSSFWorkbook workbook = renderPack(true)) {
                Sheet sheet = workbook.getSheet("Student Summary");
                int headerRow = 0;
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row != null && row.getCell(0) != null
                            && "Enrollment No.".equals(row.getCell(0).getStringCellValue())) {
                        headerRow = r;
                        break;
                    }
                }
                Row first = sheet.getRow(headerRow + 1);
                assertEquals("CS2025001", text(first, 0));
                assertEquals(32.0, first.getCell(3).getNumericCellValue(), 0.0001);
                assertEquals(40.0, first.getCell(4).getNumericCellValue(), 0.0001);
                assertEquals("80.00%",
                        new DataFormatter(Locale.ROOT).formatCellValue(first.getCell(5)));
            }
        }

        @Test
        @DisplayName("the subject summary reports real faculty, and says so when there is none")
        void subjectSummaryFacultyIsReal() throws IOException {
            when(attendanceReportService.getOverview(any(), any(), any())).thenReturn(
                    HodAttendanceOverviewDTO.builder()
                            .context(context()).totalStudents(3L).overallPresentCount(32L)
                            .overallTotalClasses(40L).overallPercentage(80.0)
                            .belowThresholdCount(1L).classesConducted(4L)
                            .thresholdPercentage(75.0).noConductedClasses(0L)
                            .distribution(List.of())
                            .subjects(List.of(
                                    subject(101L, "CS301", "Data Structures",
                                            List.of("Dr Rao"), 40L, 32L, 40L, 80.0, 3L, 1L),
                                    subject(102L, "CS302", "Computer Networks",
                                            List.of(), 30L, 24L, 30L, 80.0, 3L, 1L)))
                            .build());
            stubMatrixAndLow();

            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                    packService.export(selection(), java.time.LocalDate.parse(START),
                            java.time.LocalDate.parse(END)).bytes()))) {
                Sheet sheet = workbook.getSheet("Subject Summary");
                int headerRow = 0;
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row != null && row.getCell(0) != null
                            && "Subject Code".equals(row.getCell(0).getStringCellValue())) {
                        headerRow = r;
                        break;
                    }
                }
                Row first = sheet.getRow(headerRow + 1);
                assertEquals("Dr Rao", text(first, 2));
                Row second = sheet.getRow(headerRow + 2);
                assertEquals("No faculty assigned", text(second, 2),
                        "an unassigned subject says so rather than inventing a teacher");
            }
        }

        @Test
        @DisplayName("a subject with no conducted class reports N/A, not 0.00%")
        void zeroDenominatorStaysNa() throws IOException {
            when(attendanceReportService.getOverview(any(), any(), any())).thenReturn(
                    HodAttendanceOverviewDTO.builder()
                            .context(context()).totalStudents(1L).overallPresentCount(0L)
                            .overallTotalClasses(10L).overallPercentage(60.0)
                            .belowThresholdCount(0L).classesConducted(2L)
                            .thresholdPercentage(75.0).noConductedClasses(1L)
                            .distribution(List.of())
                            .subjects(List.of(subject(102L, "CS302", "Unconducted",
                                    List.of(), 0L, 0L, 0L, null, 1L, 0L)))
                            .build());
            stubMatrixAndLow();

            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                    packService.export(selection(), java.time.LocalDate.parse(START),
                            java.time.LocalDate.parse(END)).bytes()))) {
                Sheet sheet = workbook.getSheet("Subject Summary");
                int headerRow = 0;
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row != null && row.getCell(0) != null
                            && "Subject Code".equals(row.getCell(0).getStringCellValue())) {
                        headerRow = r;
                        break;
                    }
                }
                Row row = sheet.getRow(headerRow + 1);
                Cell percentage = row.getCell(4);
                assertEquals(CellType.STRING, percentage.getCellType(),
                        "N/A must remain text, never a fabricated 0.00%");
                assertEquals("N/A", percentage.getStringCellValue());
            }
        }

        @Test
        @DisplayName("a student with no conducted class shows N/A, not 0%")
        void studentWithNoConductedClassShowsNa() throws IOException {
            stubAll(true);
            try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                    packService.export(selection(), java.time.LocalDate.parse(START),
                            java.time.LocalDate.parse(END)).bytes()))) {
                Sheet sheet = workbook.getSheet("Student Summary");
                int headerRow = 0;
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row != null && row.getCell(0) != null
                            && "Enrollment No.".equals(row.getCell(0).getStringCellValue())) {
                        headerRow = r;
                        break;
                    }
                }
                Row second = sheet.getRow(headerRow + 2);
                assertEquals("CS2025003", text(second, 0));
                Cell percentage = second.getCell(5);
                assertEquals(CellType.STRING, percentage.getCellType());
                assertEquals("N/A", percentage.getStringCellValue(),
                        "a student with no conducted class has no percentage, not 0%");
            }
        }

@Test
        @DisplayName("sorting is the canonical enrollment-number order")
        void studentsAreInEnrollmentOrder() throws IOException {
            renderPack(true).close();
            verify(attendanceReportService, org.mockito.Mockito.atLeastOnce())
                    .getMatrix(any(), any(), any(), any(),
                            eq(0), anyInt(), eq(HodAttendanceReportService.SORT_ENROLLMENT_NUMBER),
                            eq("asc"));
        }
    }

    private void stubMatrixAndLow() {
        lenient().when(attendanceReportService.getMatrix(any(), any(), any(), any(),
                anyInt(), anyInt(), anyString(), anyString())).thenReturn(matrix(true));
        lenient().when(attendanceReportService.getLowAttendance(any(), any(), any()))
                .thenReturn(low(true));
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Empty states")
    class EmptyStates {

        @Test
        @DisplayName("a context with nothing conducted still yields all five sheets")
        void emptyContextStillHasFiveSheets() throws IOException {
            try (XSSFWorkbook workbook = renderPack(false)) {
                assertEquals(5, workbook.getNumberOfSheets(),
                        "an empty context is a real, auditable selection");
            }
        }

        @Test
        @DisplayName("every sheet says no sessions were conducted, rather than showing zeros")
        void everySheetStatesTheEmptyReason() throws IOException {
            try (XSSFWorkbook workbook = renderPack(false)) {
                for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                    List<String> meta = metaText(workbook.getSheetAt(s));
                    assertTrue(meta.stream().anyMatch(
                                    l -> l.startsWith("No attendance sessions were conducted")),
                            workbook.getSheetName(s)
                                    + " must state why it is empty, not present zeros");
                }
            }
        }

        @Test
        @DisplayName("no sheet fabricates a data row for an empty context")
        void noFabricatedRows() throws IOException {
            try (XSSFWorkbook workbook = renderPack(false)) {
                for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                    Sheet sheet = workbook.getSheetAt(s);
                    // Only the header row survives: no body rows at all.
                    assertEquals(0, bodyRowCount(sheet),
                            workbook.getSheetName(s) + " must not invent rows");
                }
            }
        }

        private int bodyRowCount(Sheet sheet) {
            int headerRow = -1;
            int body = 0;
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || row.getLastCellNum() < 2) {
                    continue;
                }
                Cell b = row.getCell(1);
                if (b == null) {
                    continue;
                }
                if (b.getCellType() == CellType.STRING) {
                    if (headerRow < 0) {
                        headerRow = r;
                    } else {
                        body++;
                    }
                }
            }
            return body;
        }
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Professional formatting")
    class Formatting {

        @Test
        @DisplayName("the matrix sheet is landscape and fit-to-width")
        void matrixIsLandscapeAndFitsWidth() throws IOException {
            try (XSSFWorkbook workbook = renderPack(true)) {
                var setup = workbook.getSheet("Attendance Matrix").getPrintSetup();
                assertTrue(setup.getLandscape(),
                        "a dynamic cross-tab is unreadable clipped to a portrait page");
                assertEquals(1, setup.getFitWidth(),
                        "it must fit across one page width");
                assertEquals(0, setup.getFitHeight(),
                        "height is unbounded: fit width, not squeeze onto one page");
                assertTrue(workbook.getSheet("Attendance Matrix").getFitToPage());
            }
        }

        @Test
        @DisplayName("the matrix sheet deliberately has no autofilter")
        void matrixHasNoAutoFilter() throws IOException {
            try (XSSFWorkbook workbook = renderPack(true)) {
                assertNull(((XSSFSheet) workbook.getSheet("Attendance Matrix")).getCTWorksheet()
                                .getAutoFilter(),
                        "filtering a cross-tab row would hide a student's other subjects");
            }
        }

        @Test
        @DisplayName("the list sheets are landscape=false, filtered and frozen")
        void listSheetsAreFilteredAndFrozen() throws IOException {
            try (XSSFWorkbook workbook = renderPack(true)) {
                for (String name : List.of("Low Attendance", "Subject Summary", "Student Summary")) {
                    Sheet sheet = workbook.getSheet(name);
                    assertFalse(sheet.getPrintSetup().getLandscape(),
                            name + " is narrow and stays portrait");
                    assertNotNull(((XSSFSheet) sheet).getCTWorksheet().getAutoFilter(),
                            name + " is a sortable list");
                    assertNotNull(sheet.getPaneInformation(),
                            name + " must stay readable while scrolling");
                }
            }
        }

        @Test
        @DisplayName("every sheet has A4 print setup and a repeating header row")
        void everySheetHasPrintSetup() throws IOException {
            try (XSSFWorkbook workbook = renderPack(true)) {
                for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                    Sheet sheet = workbook.getSheetAt(s);
                    assertEquals(org.apache.poi.ss.usermodel.PrintSetup.A4_PAPERSIZE,
                            sheet.getPrintSetup().getPaperSize(),
                            sheet.getSheetName() + " must print on A4");
                    assertNotNull(sheet.getRepeatingRows(),
                            sheet.getSheetName()
                                    + " must repeat its column labels on every printed page");
                    assertNotNull(workbook.getPrintArea(s),
                            sheet.getSheetName() + " must have an explicit print area");
                }
            }
        }

        @Test
        @DisplayName("percentages are real numbers, and read exactly as Phase 4A printed them")
        void percentagesAreNumeric() throws IOException {
            try (XSSFWorkbook workbook = renderPack(true)) {
                Sheet sheet = workbook.getSheet("Student Summary");
                int headerRow = 0;
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row != null && row.getCell(0) != null
                            && "Enrollment No.".equals(row.getCell(0).getStringCellValue())) {
                        headerRow = r;
                        break;
                    }
                }
                Row first = sheet.getRow(headerRow + 1);
                Cell percentage = first.getCell(5);
                assertEquals(CellType.NUMERIC, percentage.getCellType(),
                        "a HOD must be able to sort this column");
                assertEquals(0.80, percentage.getNumericCellValue(), 0.0001);
                assertEquals("0.00%", percentage.getCellStyle().getDataFormatString());
                assertEquals("80.00%",
                        new DataFormatter(Locale.ROOT).formatCellValue(percentage),
                        "the displayed figure must be identical to the Phase 4A text");
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Spreadsheet safety")
    class Safety {

        @Test
        @DisplayName("a student name that looks like a formula is neutralised")
        void formulaLikeStudentNameIsNeutralised() throws IOException {
            when(attendanceReportService.getMatrix(any(), any(), any(), any(),
                    anyInt(), anyInt(), anyString(), anyString())).thenReturn(
                    HodAttendanceMatrixDTO.builder()
                            .context(context())
                            .subjects(List.of())
                            .students(List.of(HodAttendanceMatrixRowDTO.builder()
                                    .studentId(99L).enrollmentNumber("CS2025999")
                                    .rollNumber("R99").studentName("=1+1")
                                    .sectionName("A")
                                    .subjects(List.of())
                                    .totalPresent(1L).totalClasses(2L)
                                    .overallPercentage(50.0).build()))
                            .page(0).size(1).totalElements(1L).totalPages(1)
                            .singleSubject(false).thresholdPercentage(75.0).build());
            stubOverviewAndLow();

try (XSSFWorkbook workbook = renderFormulaPack()) {
                Sheet sheet = workbook.getSheet("Student Summary");
                boolean found = false;
                for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                    Row row = sheet.getRow(r);
                    if (row == null || row.getCell(1) == null) {
                        continue;
                    }
                    Cell cell = row.getCell(1);
                    if (cell.getCellType() == CellType.STRING
                            && cell.getStringCellValue().contains("=1+1")) {
                        found = true;
                        // The apostrophe marks the value as literal text and Excel
                        // hides it, so the reader still sees "=1+1" - but the cell
                        // is inert text, never an evaluated formula.
                        assertTrue(cell.getStringCellValue().startsWith("'"),
                                "a leading '=' must be marked as literal text");
                    }
                }
                assertTrue(found, "the student's name must still be readable");
            }
        }

        @Test
        @DisplayName("an ordinary name is written verbatim, with no escaping")
        void ordinaryNamesAreUnchanged() {
            assertAll(
                    () -> assertEquals("Rahul", ExcelReportGenerator.safeCellText("Rahul")),
                    () -> assertEquals("O'Brien", ExcelReportGenerator.safeCellText("O'Brien")),
                    () -> assertEquals("Semester 3", ExcelReportGenerator.safeCellText("Semester 3")),
                    () -> assertEquals("B.Tech CSE", ExcelReportGenerator.safeCellText("B.Tech CSE")),
                    () -> assertEquals("Department of CSE & Engineering",
                            ExcelReportGenerator.safeCellText("Department of CSE & Engineering")));
        }

        @Test
        @DisplayName("every dangerous leading character is neutralised")
        void dangerousLeadingCharactersAreNeutralised() {
            for (char dangerous : new char[]{'=', '+', '-', '@', '\t', '\r'}) {
                String hostile = dangerous + "cmd|'/c calc'!A0";
                assertEquals("'" + hostile, ExcelReportGenerator.safeCellText(hostile),
                        "a leading " + dangerous + " must be neutralised");
            }
        }

        @Test
        @DisplayName("only a leading character is dangerous")
        void onlyLeadingCharactersMatter() {
            assertEquals("a-b", ExcelReportGenerator.safeCellText("a-b"));
            assertEquals("x@y", ExcelReportGenerator.safeCellText("x@y"));
            assertEquals("CS-301", ExcelReportGenerator.safeCellText("CS-301"));
        }

private void stubOverviewAndLow() {
            lenient().when(attendanceReportService.getOverview(any(), any(), any()))
                    .thenReturn(overview(true));
            lenient().when(attendanceReportService.getLowAttendance(any(), any(), any()))
                    .thenReturn(low(true));
        }

        private XSSFWorkbook renderFormulaPack() throws IOException {
            return new XSSFWorkbook(new ByteArrayInputStream(
                    packService.export(selection(), java.time.LocalDate.parse(START),
                            java.time.LocalDate.parse(END)).bytes()));
        }
    }

    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("No duplicated calculation")
    class NoDuplicatedCalculation {

@Test
        @DisplayName("the pack loads each canonical report exactly once")
        void threeServiceCallsNoMore() {
            stubAll(true);
            packService.export(selection(),
                    java.time.LocalDate.parse(START), java.time.LocalDate.parse(END));

            // Exactly three calls fill all five sheets. The matrix and
            // low-attendance arrangements are handed the DTOs already loaded,
            // so a sheet never costs a second query - which is what makes the
            // count constant in student and subject count.
            verify(attendanceReportService, org.mockito.Mockito.times(1))
                    .getOverview(any(), any(), any());
            verify(attendanceReportService, org.mockito.Mockito.times(1))
                    .getMatrix(any(), any(), any(), any(), anyInt(), anyInt(),
                            anyString(), anyString());
            verify(attendanceReportService, org.mockito.Mockito.times(1))
                    .getLowAttendance(any(), any(), any());
        }

@Test
        @DisplayName("the pack owns no threshold of its own")
        void thresholdComesFromTheData() throws IOException {
            stubAll(true);
            try (XSSFWorkbook workbook = renderPack(true)) {
                List<String> meta = metaText(workbook.getSheet("Executive Summary"));
                assertTrue(meta.stream().anyMatch(l -> l.startsWith("Below 75.00%:")),
                        "the threshold is printed from the DTO, not re-declared");
            }
        }
    }
}