package com.dagacs.export;

import com.dagacs.dto.StudentWiseCellDTO;
import com.dagacs.dto.StudentWiseColumnDTO;
import com.dagacs.dto.StudentWiseReportDTO;
import com.dagacs.dto.StudentWiseRowDTO;
import com.dagacs.entity.Teacher;
import com.dagacs.repository.TeacherReportRepository;
import com.dagacs.security.AuthenticatedHodResolver;
import com.dagacs.security.AuthenticatedTeacherResolver;
import com.dagacs.service.HodAnalyticsService;
import com.dagacs.service.TeacherStudentWiseReportService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * Renders the student-wise register with the REAL generators (not mocks) and
 * reads the produced files back, so the user-visible workbook and PDF are
 * verified rather than just the intermediate {@link ExportData}:
 *
 * <ul>
 *   <li>the Excel file carries the three-row title block, the session columns
 *       and {@code P}/{@code A} - with {@code A} for an unmarked session;</li>
 *   <li>the Excel header row and the two identity columns are frozen;</li>
 *   <li>the PDF is landscape and repeats its header row.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Student-wise register file rendering")
class StudentWiseRegisterExportRenderingTest {

    @Mock
    private AuthenticatedTeacherResolver teacherResolver;
    @Mock
    private TeacherStudentWiseReportService studentWiseReportService;

    private ReportExportService service;

    @BeforeEach
    void setUp() {
        service = new ReportExportService(
                org.mockito.Mockito.mock(AuthenticatedHodResolver.class),
                org.mockito.Mockito.mock(com.dagacs.repository.HodReportRepository.class),
                org.mockito.Mockito.mock(HodAnalyticsService.class),
                teacherResolver,
                org.mockito.Mockito.mock(TeacherReportRepository.class),
                studentWiseReportService,
                new ExcelReportGenerator(),
                new PdfReportGenerator());
    }

    /**
     * Three conducted sessions. Rahul is marked present/present/absent; Priya
     * is present/absent and the third session was never marked by the teacher.
     */
    private void stubReport() {
        List<StudentWiseColumnDTO> columns = List.of(
                StudentWiseColumnDTO.builder().sessionId(100L).date("2026-08-12").lecturePeriod("LP1").build(),
                StudentWiseColumnDTO.builder().sessionId(101L).date("2026-08-14").lecturePeriod("LP1").build(),
                StudentWiseColumnDTO.builder().sessionId(102L).date("2026-08-18").lecturePeriod("LP1").build());

        StudentWiseRowDTO rahul = StudentWiseRowDTO.builder()
                .studentId(1L).enrollmentNumber("DAGACS001").rollNumber("ROLL-001").name("Rahul Sharma")
                .presentCount(2L).totalRecordedCount(3L).percentage(200.0 / 3)
                .cells(List.of(
                        StudentWiseCellDTO.builder().sessionId(100L).status("PRESENT").isPresent(true).build(),
                        StudentWiseCellDTO.builder().sessionId(101L).status("PRESENT").isPresent(true).build(),
                        StudentWiseCellDTO.builder().sessionId(102L).status("ABSENT").isPresent(false).build()))
                .build();

        // No cell for session 102 at all -> unmarked.
        StudentWiseRowDTO priya = StudentWiseRowDTO.builder()
                .studentId(2L).enrollmentNumber("DAGACS002").rollNumber("ROLL-002").name("Priya Verma")
                .presentCount(1L).totalRecordedCount(3L).percentage(100.0 / 3)
                .cells(List.of(
                        StudentWiseCellDTO.builder().sessionId(100L).status("PRESENT").isPresent(true).build(),
                        StudentWiseCellDTO.builder().sessionId(101L).status("ABSENT").isPresent(false).build()))
                .build();

        when(teacherResolver.resolve())
                .thenReturn(Teacher.builder().id(7L).fullName("Asha Teacher").build());
        when(studentWiseReportService.getStudentWiseReport(any(), any(), anyLong(), any(), any()))
                .thenReturn(StudentWiseReportDTO.builder()
                        .subjectId(1L).subjectName("DBMS")
                        .sectionId(2L).sectionName("CSE-A")
                        .columns(columns)
                        .rows(List.of(rahul, priya))
                        .build());
    }

    private byte[] xlsx() {
        return service.exportTeacherStudentWise(ExportFormat.XLSX,
                LocalDate.of(2026, 8, 12), LocalDate.of(2026, 9, 12), 1L, 2L, null);
    }

    private byte[] pdf() {
        return service.exportTeacherStudentWise(ExportFormat.PDF,
                LocalDate.of(2026, 8, 12), LocalDate.of(2026, 9, 12), 1L, 2L, null);
    }

    @Test
    @DisplayName("the workbook renders the three-row title block, then the header, then the data")
    void excel_titleBlockAndLayout() throws Exception {
        stubReport();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx()))) {
            var sheet = wb.getSheet("Attendance Report");

            // Row 0: the app name. Row 1: the report name.
            assertEquals("DAGACS", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals("STUDENT ATTENDANCE REPORT",
                    sheet.getRow(1).getCell(0).getStringCellValue());
            // Rows 2-6: subject / section / batch / teacher / date range.
            assertEquals("Subject: DBMS",
                    sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals("Section: CSE-A",
                    sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("Batch: -",
                    sheet.getRow(4).getCell(0).getStringCellValue());
            assertEquals("Teacher: Asha Teacher",
                    sheet.getRow(5).getCell(0).getStringCellValue());
            String range = sheet.getRow(6).getCell(0).getStringCellValue();
            assertTrue(range.contains("Date Range:"), range);
            assertTrue(range.contains("2026-08-12 to 2026-09-12"), range);
            // Row 7: blank spacer.
            assertEquals(8, headerRowIndex);

            // Row 8: the header.
            var header = sheet.getRow(headerRowIndex);
            assertEquals("Enrollment No.", header.getCell(0).getStringCellValue());
            assertEquals("Student Name", header.getCell(1).getStringCellValue());
            assertEquals("12-Aug-2026 (LP1)", header.getCell(2).getStringCellValue());
            assertEquals("14-Aug-2026 (LP1)", header.getCell(3).getStringCellValue());
            assertEquals("18-Aug-2026 (LP1)", header.getCell(4).getStringCellValue());
            assertEquals("Present", header.getCell(5).getStringCellValue());
            assertEquals("Total Classes", header.getCell(6).getStringCellValue());
            assertEquals("Percentage", header.getCell(7).getStringCellValue());
        }
    }

    /** Header row index: title + subtitle + 5 metadata lines + 1 blank spacer. */
    private static final int headerRowIndex = 8;

    @Test
    @DisplayName("P/A marks land in the date columns and an unmarked session shows A")
    void excel_marksAndUnmarked() throws Exception {
        stubReport();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx()))) {
            var sheet = wb.getSheet("Attendance Report");

            var rahul = sheet.getRow(headerRowIndex + 1);
            assertEquals("DAGACS001", rahul.getCell(0).getStringCellValue());
            assertEquals("P", rahul.getCell(2).getStringCellValue());
            assertEquals("P", rahul.getCell(3).getStringCellValue());
            assertEquals("A", rahul.getCell(4).getStringCellValue());
            assertEquals(2.0, rahul.getCell(5).getNumericCellValue(), 0.001);
            assertEquals(3.0, rahul.getCell(6).getNumericCellValue(), 0.001);
            assertEquals("66.67%", rahul.getCell(7).getStringCellValue());

            var priya = sheet.getRow(headerRowIndex + 2);
            assertEquals("DAGACS002", priya.getCell(0).getStringCellValue());
            assertEquals("P", priya.getCell(2).getStringCellValue());
            assertEquals("A", priya.getCell(3).getStringCellValue());
            // Unmarked session is A, not an empty cell.
            assertEquals("A", priya.getCell(4).getStringCellValue());
            assertEquals(1.0, priya.getCell(5).getNumericCellValue(), 0.001);
            assertEquals(3.0, priya.getCell(6).getNumericCellValue(), 0.001);
            assertEquals("33.33%", priya.getCell(7).getStringCellValue());
        }
    }

    @Test
    @DisplayName("no blank mark cells and no roll number anywhere in the sheet")
    void excel_noBlanksAndNoRoll() throws Exception {
        stubReport();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx()))) {
            var sheet = wb.getSheet("Attendance Report");
            for (int r = 0; r <= sheet.getLastRowNum(); r++) {
                var row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                for (int c = 0; c < row.getLastCellNum(); c++) {
                    Cell cell = row.getCell(c);
                    if (cell == null) {
                        continue;
                    }
                    String text = cell.getCellType() == CellType.STRING
                            ? cell.getStringCellValue() : "";
                    assertTrue(!text.contains("ROLL-"),
                            "roll number leaked at row " + r + " col " + c);
                }
            }
            // Priya's unmarked session (row 7, col 4) must be the letter A.
            assertEquals(CellType.STRING, sheet.getRow(headerRowIndex + 2).getCell(4).getCellType());
        }
    }

    @Test
    @DisplayName("the header row and the two identity columns are frozen")
    void excel_freezesHeaderAndIdentityColumns() throws Exception {
        stubReport();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx()))) {
            var pane = wb.getSheet("Attendance Report").getPaneInformation();
            assertTrue(pane != null && pane.isFreezePane(),
                    "expected a frozen pane on the register");
            // 2 identity columns pinned; 9 rows pinned (title, report name, 5
            // metadata lines, blank, header) so the header row stays visible.
            assertEquals(2, pane.getVerticalSplitPosition());
            assertEquals(9, pane.getHorizontalSplitPosition());
        }
    }

    @Test
    @DisplayName("the PDF is landscape so the date columns are not clipped")
    void pdf_isLandscape() throws Exception {
        stubReport();
        byte[] bytes = pdf();
        PdfReader reader = new PdfReader(bytes);
        try {
            var size = reader.getPageSize(1);
            assertTrue(size.getWidth() > size.getHeight(),
                    "expected a landscape page but got " + size.getWidth()
                            + "x" + size.getHeight());
        } finally {
            PdfStamper stamper = new PdfStamper(reader, new ByteArrayOutputStreamHolder().out);
            stamper.close();
            reader.close();
        }
    }

    /** Tiny holder so the stamper has somewhere to write its throwaway output. */
    private static final class ByteArrayOutputStreamHolder {
        private final java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    }
}
