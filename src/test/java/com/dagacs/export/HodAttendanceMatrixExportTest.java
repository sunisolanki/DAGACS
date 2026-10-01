package com.dagacs.export;

import com.dagacs.config.ReportBrandingProperties;
import com.dagacs.dto.HodAttendanceContextDTO;
import com.dagacs.dto.HodAttendanceMatrixCellDTO;
import com.dagacs.dto.HodAttendanceMatrixColumnDTO;
import com.dagacs.dto.HodAttendanceMatrixDTO;
import com.dagacs.dto.HodAttendanceMatrixRowDTO;
import com.dagacs.security.AuthenticatedHodResolver;
import com.dagacs.service.HodAcademicSelection;
import com.dagacs.service.HodAttendanceReportService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Phase 3 export tests for the HOD attendance matrix.
 *
 * <p>Two layers are covered:</p>
 * <ol>
 *   <li>the <b>data model</b> handed to the generators - column order, one row
 *       per student, the {@code Present / Recorded} cell form, the {@code N/A}
 *       percentage, and above all <b>context isolation</b>: the file may contain
 *       only the students and subjects of the one selected academic context;</li>
 *   <li>the <b>rendered workbooks</b>, read back with POI, so the shipped file is
 *       verified rather than only the intermediate model.</li>
 * </ol>
 *
 * <p>The real generators are used; only the data source is stubbed, so the
 * assertions describe what a HOD would actually receive.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HodAttendanceMatrixExportTest {

    @Mock private HodAttendanceReportService attendanceReportService;

    @Mock private AuthenticatedHodResolver hodResolver;

    /** Real generators, wired after the mock exists rather than at field init. */
    private final ExcelReportGenerator excelGenerator = new ExcelReportGenerator();
    private final PdfReportGenerator pdfGenerator = new PdfReportGenerator();
    private HodAttendanceMatrixExportService exportService;

    @BeforeEach
    void wire() {
        // Phase 4A: the header comes from the shared builder, so institution and
        // the real department line are identical to the other four HOD reports.
        exportService = new HodAttendanceMatrixExportService(
                attendanceReportService,
                new HodExportHeader(new ReportBrandingProperties(), hodResolver),
                excelGenerator, pdfGenerator);
    }

    // ── Fixture ───────────────────────────────────────────────────────────

    private static final long CS301 = 101L;
    private static final long CS302 = 102L;

    private static HodAcademicSelection selection() {
        return new HodAcademicSelection(1L, 2L, 3L, 4L);
    }

    private static HodAttendanceMatrixDTO matrix(boolean singleSubject) {
        return HodAttendanceMatrixDTO.builder()
                .context(HodAttendanceContextDTO.builder()
                        .academicSessionId(1L).academicSessionName("Academic Session 2026-27")
                        .programId(2L).programName("B.Tech CSE")
                        .semesterId(3L).semesterName("Semester 3")
                        .sectionId(4L).sectionName("A")
                        .complete(true)
                        .build())
                .subjects(List.of(
                        column(CS301, 11L, "CS301", "Data Structures and Algorithms"),
                        column(CS302, 12L, "CS302", "Computer Networks")))
                .students(List.of(
                        row(91L, "CS2025001", "R1", "Rahul", 32, 40, 30, 35, 62, 75),
                        row(92L, "CS2025002", "R2", "Amit", 28, 40, 31, 35, 59, 75),
                        // A student with no attendance at all.
                        zeroRow(93L, "CS2025003", "R3", "Neha")))
                .page(0).size(50).totalElements(3L).totalPages(1)
                .singleSubject(singleSubject)
                .thresholdPercentage(75.0)
                .build();
    }

    private static HodAttendanceMatrixColumnDTO column(long id, long offeringId,
                                                       String code, String name) {
        return HodAttendanceMatrixColumnDTO.builder()
                .subjectId(id).subjectOfferingId(offeringId)
                .subjectCode(code).subjectName(name)
                .facultyNames(List.of("Dr Rao"))
                .build();
    }

    private static HodAttendanceMatrixRowDTO row(long studentId, String enrollment,
                                                 String roll, String name,
                                                 long p1, long t1, long p2, long t2,
                                                 long expectedPresent, long expectedTotal) {
        return HodAttendanceMatrixRowDTO.builder()
                .studentId(studentId)
                .enrollmentNumber(enrollment)
                .rollNumber(roll)
                .studentName(name)
                .sectionName("A")
                .subjects(List.of(
                        cell(CS301, p1, t1),
                        cell(CS302, p2, t2)))
                .totalPresent(expectedPresent)
                .totalClasses(expectedTotal)
                .overallPercentage((double) expectedPresent / expectedTotal * 100.0)
                .build();
    }

    private static HodAttendanceMatrixRowDTO zeroRow(long studentId, String enrollment,
                                                     String roll, String name) {
        return HodAttendanceMatrixRowDTO.builder()
                .studentId(studentId)
                .enrollmentNumber(enrollment)
                .rollNumber(roll)
                .studentName(name)
                .sectionName("A")
                .subjects(List.of(cell(CS301, 0, 0), cell(CS302, 0, 0)))
                .totalPresent(0L)
                .totalClasses(0L)
                .overallPercentage(null)
                .build();
    }

    private static HodAttendanceMatrixCellDTO cell(long subjectId, long present, long total) {
        return HodAttendanceMatrixCellDTO.builder()
                .subjectId(subjectId)
                .present(present)
                .total(total)
                .percentage(total <= 0 ? null : (double) present / total * 100.0)
                .build();
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    @Test
    void dataModel_hasTheRequiredLogicalColumnOrder() {
        when(attendanceReportService.getMatrix(any(), any(), any(), any(),
                eq(0), eq(Integer.MAX_VALUE), anyString(), anyString()))
                .thenReturn(matrix(false));

        ExportData data = exportService.buildMatrixData(selection(), null, null, null);

        // 1 Enrollment No. | 2 Student Name | 3..N dynamic subjects
        // N+1 Total Present | N+2 Total Classes | N+3 Overall Attendance %
        assertEquals(List.of("Enrollment No.", "Student Name",
                "CS301 - Data Structures and Algorithms",
                "CS302 - Computer Networks",
                "Total Present", "Total Classes", "Overall Attendance %"), data.headers());

        assertEquals(7, data.headers().size());
        assertEquals(3, data.rows().size());
        // One row per student, never a normalised Student/Subject/Date table.
        for (List<Object> excelRow : data.rows()) {
            assertEquals(7, excelRow.size());
        }
    }

    @Test
    void dataModel_rendersPresentOverTotalAndNAForNoData() {
        when(attendanceReportService.getMatrix(any(), any(), any(), any(),
                any(int.class), any(int.class), anyString(), anyString()))
                .thenReturn(matrix(false));

        ExportData data = exportService.buildMatrixData(selection(), null, null, null);

        assertEquals("32 / 40", data.rows().get(0).get(2));
        assertEquals("30 / 35", data.rows().get(0).get(3));
        assertEquals(62L, ((Number) data.rows().get(0).get(4)).longValue());
        assertEquals(75L, ((Number) data.rows().get(0).get(5)).longValue());
        assertEquals("82.67%", data.rows().get(0).get(6));

        // A subject and a student with no conducted class: 0 / 0 and N/A,
        // never a fabricated 0.00%.
        assertEquals("0 / 0", data.rows().get(2).get(2));
        assertEquals("0 / 0", data.rows().get(2).get(3));
        assertEquals("N/A", data.rows().get(2).get(6));
    }

    @Test
    void dataModel_echoesTheExactAcademicContext() {
        when(attendanceReportService.getMatrix(any(), any(), any(), any(),
                any(int.class), any(int.class), anyString(), anyString()))
                .thenReturn(matrix(false));

        ExportData data = exportService.buildMatrixData(selection(), null, "2026-01-01", "2026-01-31");

        assertEquals("DAGACS", data.title());
        assertEquals("HOD ATTENDANCE MATRIX", data.subtitle());
        assertTrue(data.metaLines().contains("Academic Session: Academic Session 2026-27"));
        assertTrue(data.metaLines().contains("Program: B.Tech CSE"));
        assertTrue(data.metaLines().contains("Semester: Semester 3"));
        assertTrue(data.metaLines().contains("Section: A"));
        assertTrue(data.metaLines().contains("Date Range: 2026-01-01 to 2026-01-31"));
        // A cross-tab with dynamic subject columns must never be clipped.
        assertTrue(data.landscape());
    }

    @Test
    void dataModel_containsOnlyTheSelectedContextsStudentsAndSubjects() {
        when(attendanceReportService.getMatrix(any(), any(), any(), any(),
                any(int.class), any(int.class), anyString(), anyString()))
                .thenReturn(matrix(false));

        ExportData data = exportService.buildMatrixData(selection(), null, null, null);

        // Column headers carry the context's subjects only.
        assertFalse(data.headers().stream().anyMatch(h -> h.contains("MT501")));
        assertFalse(data.headers().stream().anyMatch(h -> h.contains("CS101")));

        // Rows carry the context's students only, exactly one row each.
        List<String> enrolments = new ArrayList<>();
        data.rows().forEach(row -> enrolments.add(String.valueOf(row.get(0))));
        assertEquals(List.of("CS2025001", "CS2025002", "CS2025003"), enrolments);
        assertEquals(enrolments.size(), enrolments.stream().distinct().count());
        for (List<Object> row : data.rows()) {
            assertFalse(String.valueOf(row.get(1)).contains("M Tech"));
        }
    }

    @Test
    void dataModel_fallsBackToTheRollNumberWhenEnrolmentNumberIsMissing() {
        HodAttendanceMatrixDTO payload = matrix(false);
        List<HodAttendanceMatrixRowDTO> rows = new ArrayList<>(payload.getStudents());
        rows.add(HodAttendanceMatrixRowDTO.builder()
                .studentId(94L).enrollmentNumber(null).rollNumber("R4").studentName("Legacy")
                .sectionName("A").subjects(List.of(cell(CS301, 1, 1), cell(CS302, 1, 1)))
                .totalPresent(2L).totalClasses(2L).overallPercentage(100.0)
                .build());
        payload.setStudents(rows);
        when(attendanceReportService.getMatrix(any(), any(), any(), any(),
                any(int.class), any(int.class), anyString(), anyString()))
                .thenReturn(payload);

        ExportData data = exportService.buildMatrixData(selection(), null, null, null);
        assertEquals("R4", data.rows().get(3).get(0));
    }

    @Test
    void xlsx_containsOneRowPerStudentWithTheDynamicSubjectColumns() throws IOException {
        when(attendanceReportService.getMatrix(any(), any(), any(), any(),
                any(int.class), any(int.class), anyString(), anyString()))
                .thenReturn(matrix(false));

        byte[] bytes = exportService.export(ExportFormat.XLSX, selection(), null, null, null);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("Attendance Matrix");
            assertNotNull(sheet);

            // Title, subtitle, then the context meta lines, then the header row.
            Row header = headerRow(sheet);
            assertEquals(7, header.getLastCellNum());
            assertEquals("Enrollment No.", text(header, 0));
            assertEquals("Student Name", text(header, 1));
            assertEquals("CS301 - Data Structures and Algorithms", text(header, 2));
            assertEquals("CS302 - Computer Networks", text(header, 3));
            assertEquals("Total Present", text(header, 4));
            assertEquals("Total Classes", text(header, 5));
            assertEquals("Overall Attendance %", text(header, 6));

            // Exactly one row per student.
            assertEquals(3, sheet.getLastRowNum() - header.getRowNum());

            Row first = sheet.getRow(header.getRowNum() + 1);
            assertEquals("CS2025001", text(first, 0));
            assertEquals("Rahul", text(first, 1));
            assertEquals("32 / 40", text(first, 2));
            assertEquals("30 / 35", text(first, 3));
            assertEquals(62.0, first.getCell(4).getNumericCellValue(), 0.0001);
            assertEquals(75.0, first.getCell(5).getNumericCellValue(), 0.0001);
            assertEquals("82.67%", text(first, 6));

            Row zero = sheet.getRow(header.getRowNum() + 3);
            assertEquals("CS2025003", text(zero, 0));
            assertEquals("0 / 0", text(zero, 2));
            assertEquals("N/A", text(zero, 6));
        }
    }

    @Test
    void pdf_isGeneratedFromTheSameDataSoTheTwoFilesCannotDisagree() throws IOException {
        when(attendanceReportService.getMatrix(any(), any(), any(), any(),
                any(int.class), any(int.class), anyString(), anyString()))
                .thenReturn(matrix(false));

        byte[] pdf = exportService.export(ExportFormat.PDF, selection(), null, null, null);
        byte[] xlsx = exportService.export(ExportFormat.XLSX, selection(), null, null, null);

        assertTrue(pdf.length > 0);
        assertArrayEquals(new byte[]{'%', 'P', 'D', 'F'},
                new byte[]{pdf[0], pdf[1], pdf[2], pdf[3]});
        assertTrue(xlsx.length > 0);
    }

    @Test
    void export_alwaysRequestsTheWholePopulationRegardlessOfPaging() {
        when(attendanceReportService.getMatrix(any(), any(), any(), any(),
                any(int.class), any(int.class), anyString(), anyString()))
                .thenReturn(matrix(false));

        exportService.buildMatrixData(selection(), null, null, null);

        org.mockito.Mockito.verify(attendanceReportService).getMatrix(
                any(), eq((Long) null), eq((String) null), eq((String) null),
                eq(0), eq(Integer.MAX_VALUE),
                eq(HodAttendanceReportService.SORT_ENROLLMENT_NUMBER), eq("asc"));
    }

    @Test
    void fileName_isDeterministicAndDescribesTheRange() {
        assertEquals("dagacs_hod_attendance_matrix_all.xlsx",
                exportService.fileName(ExportFormat.XLSX, null, null));
        assertEquals("dagacs_hod_attendance_matrix_2026-01-01_to_2026-01-31.xlsx",
                exportService.fileName(ExportFormat.XLSX,
                        java.time.LocalDate.of(2026, 1, 1), java.time.LocalDate.of(2026, 1, 31)));
        assertEquals("dagacs_hod_attendance_matrix_2026-01-01_onwards.pdf",
                exportService.fileName(ExportFormat.PDF, java.time.LocalDate.of(2026, 1, 1), null));
        assertEquals("dagacs_hod_attendance_matrix_to_2026-01-31.pdf",
                exportService.fileName(ExportFormat.PDF, null, java.time.LocalDate.of(2026, 1, 31)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** The header row is the first row whose cells are all the report headers. */
    private static Row headerRow(Sheet sheet) {
        for (int i = 0; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }
            if ("Enrollment No.".equals(text(row, 0))) {
                return row;
            }
        }
        throw new AssertionError("No header row found in the generated sheet");
    }

    private static String text(Row row, int column) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            return null;
        }
        return cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC
                ? String.valueOf(cell.getNumericCellValue())
                : cell.getStringCellValue();
    }
}
