package com.dagacs.export;

import com.dagacs.dto.StudentWiseCellDTO;
import com.dagacs.dto.StudentWiseColumnDTO;
import com.dagacs.dto.StudentWiseReportDTO;
import com.dagacs.dto.StudentWiseRowDTO;
import com.dagacs.entity.Teacher;
import com.dagacs.security.AuthenticatedTeacherResolver;
import com.dagacs.security.AuthenticatedHodResolver;
import com.dagacs.service.TeacherStudentWiseReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Contract of the teacher student-wise XLSX register and PDF summary.
 *
 * <p>Asserts the user-visible register shape rather than POI internals: the
 * logical column order, the absence of Roll Number and of any separate status
 * column, {@code P}/{@code A} living inside the date columns, the shared
 * {@code Total Classes} denominator, and two-decimal percentages.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Teacher student-wise register export")
class TeacherStudentWiseReportExportTest {

    @Mock
    private AuthenticatedTeacherResolver teacherResolver;
    @Mock
    private AuthenticatedHodResolver hodResolver;
    @Mock
    private com.dagacs.repository.HodReportRepository hodReportRepository;
    @Mock
    private com.dagacs.service.HodAnalyticsService hodAnalyticsService;
    @Mock
    private com.dagacs.repository.TeacherReportRepository teacherReportRepository;
    @Mock
    private TeacherStudentWiseReportService studentWiseReportService;
    @Mock
    private ExcelReportGenerator excelGenerator;
    @Mock
    private PdfReportGenerator pdfGenerator;

    private ReportExportService service;

    @BeforeEach
    void setUp() {
        service = new ReportExportService(
                hodResolver,
                hodReportRepository,
                hodAnalyticsService,
                teacherResolver,
                teacherReportRepository,
                studentWiseReportService,
                excelGenerator,
                pdfGenerator);
    }

    private StudentWiseReportDTO report() {
        StudentWiseColumnDTO c1 = StudentWiseColumnDTO.builder()
                .sessionId(100L).date("2026-08-12").lecturePeriod("LP1").build();
        StudentWiseColumnDTO c2 = StudentWiseColumnDTO.builder()
                .sessionId(101L).date("2026-08-14").lecturePeriod("LP1").build();
        StudentWiseColumnDTO c3 = StudentWiseColumnDTO.builder()
                .sessionId(102L).date("2026-08-18").lecturePeriod("LP1").build();

        // Rahul: present, absent, present  -> 2 of 3 = 66.67%
        StudentWiseRowDTO rahul = StudentWiseRowDTO.builder()
                .studentId(1L)
                .enrollmentNumber("DAGACS001")
                .rollNumber("ROLL-001")
                .name("Rahul Sharma")
                .presentCount(2L)
                .totalRecordedCount(3L)
                .percentage(200.0 / 3)
                .cells(List.of(
                        StudentWiseCellDTO.builder().sessionId(100L).status("PRESENT").isPresent(true).build(),
                        StudentWiseCellDTO.builder().sessionId(101L).status("ABSENT").isPresent(false).build(),
                        StudentWiseCellDTO.builder().sessionId(102L).status("PRESENT").isPresent(true).build()))
                .build();

        // Priya: present, present, absent -> 2 of 3 = 66.67%
        StudentWiseRowDTO priya = StudentWiseRowDTO.builder()
                .studentId(2L)
                .enrollmentNumber("DAGACS002")
                .rollNumber("ROLL-002")
                .name("Priya Verma")
                .presentCount(2L)
                .totalRecordedCount(3L)
                .percentage(200.0 / 3)
                .cells(List.of(
                        StudentWiseCellDTO.builder().sessionId(100L).status("PRESENT").isPresent(true).build(),
                        StudentWiseCellDTO.builder().sessionId(101L).status("PRESENT").isPresent(true).build(),
                        StudentWiseCellDTO.builder().sessionId(102L).status("ABSENT").isPresent(false).build()))
                .build();

        return StudentWiseReportDTO.builder()
                .subjectId(1L)
                .subjectName("DBMS")
                .sectionId(2L)
                .sectionName("CSE-A")
                .batchId(null)
                .batchCode(null)
                .columns(List.of(c1, c2, c3))
                .rows(List.of(rahul, priya))
                .build();
    }

    private void stubReport() {
        when(teacherResolver.resolve())
                .thenReturn(Teacher.builder().id(7L).fullName("Asha Teacher").build());
        when(studentWiseReportService.getStudentWiseReport(
                any(), any(), anyLong(), any(), any())).thenReturn(report());
    }

    private ExportData capture(ExportFormat format) {
        service.exportTeacherStudentWise(format,
                LocalDate.of(2026, 8, 12), LocalDate.of(2026, 9, 12), 1L, 2L, null);
        var captor = org.mockito.ArgumentCaptor.forClass(ExportData.class);
        if (format == ExportFormat.XLSX) {
            org.mockito.Mockito.verify(excelGenerator).generate(captor.capture());
        } else {
            org.mockito.Mockito.verify(pdfGenerator).generate(captor.capture());
        }
        return captor.getValue();
    }

    @Test
    @DisplayName("register columns are Enrollment, Name, session dates, Present, Total Classes, Percentage")
    void register_columnOrder() {
        stubReport();
        ExportData data = capture(ExportFormat.XLSX);

        List<String> headers = data.headers();
        assertEquals("Enrollment No.", headers.get(0));
        assertEquals("Student Name", headers.get(1));
        // One column per CONDUCTED session date, in chronological order.
        assertEquals("2026-08-12 | LP1", headers.get(2));
        assertEquals("2026-08-14 | LP1", headers.get(3));
        assertEquals("2026-08-18 | LP1", headers.get(4));
        // Final three columns.
        assertEquals("Present", headers.get(5));
        assertEquals("Total Classes", headers.get(6));
        assertEquals("Percentage", headers.get(7));
        assertEquals(8, headers.size());
    }

    @Test
    @DisplayName("Roll Number is never exported")
    void register_rollNumberAbsent() {
        stubReport();
        ExportData data = capture(ExportFormat.XLSX);

        // NB: "Enrollment" contains the substring "roll", so match the label
        // rather than a bare substring.
        assertFalse(data.headers().stream()
                .anyMatch(h -> h.toLowerCase().contains("roll number")
                        || h.toLowerCase().contains("roll no")));
        for (List<Object> row : data.rows()) {
            for (Object cell : row) {
                assertFalse(String.valueOf(cell).contains("ROLL-"),
                        "roll number leaked into the register");
            }
        }
    }

    @Test
    @DisplayName("there is no separate P/A column; P and A are values inside the date columns")
    void register_noSeparateStatusColumn() {
        stubReport();
        ExportData data = capture(ExportFormat.XLSX);

        assertFalse(data.headers().stream()
                .anyMatch(h -> h.equalsIgnoreCase("P/A")
                        || h.equalsIgnoreCase("Status")
                        || h.equalsIgnoreCase("Attendance")));

        List<Object> first = data.rows().get(0);
        assertEquals("DAGACS001", first.get(0));
        assertEquals("Rahul Sharma", first.get(1));
        assertEquals("P", first.get(2));
        assertEquals("A", first.get(3));
        assertEquals("P", first.get(4));
    }

    @Test
    @DisplayName("Present, shared Total Classes denominator and two-decimal percentage")
    void register_totalsAndPercentage() {
        stubReport();
        ExportData data = capture(ExportFormat.XLSX);

        for (int i = 0; i < 2; i++) {
            List<Object> row = data.rows().get(i);
            assertEquals(2L, row.get(5), "present count");
            assertEquals(3L, row.get(6), "total classes is the shared denominator");
            assertEquals("66.67%", row.get(7), "percentage must show two decimals");
        }
    }

    @Test
    @DisplayName("every register row maps one-to-one onto the columns")
    void register_rowShape() {
        stubReport();
        ExportData data = capture(ExportFormat.XLSX);
        // The padded legend row is present, so every row (including it) must
        // still line up with the header width.
        int width = data.headers().size();
        for (List<Object> row : data.rows()) {
            assertEquals(width, row.size());
        }
    }

    @Test
    @DisplayName("title/subtitle carry the report identity, subject, section, teacher and range")
    void register_metadata() {
        stubReport();
        ExportData data = capture(ExportFormat.XLSX);

        assertTrue(data.title().contains("DAGACS"));
        assertTrue(data.title().contains("Student Attendance Report"));
        assertTrue(data.subtitle().contains("Subject: DBMS"));
        assertTrue(data.subtitle().contains("Section: CSE-A"));
        assertTrue(data.subtitle().contains("Asha Teacher"));
        assertTrue(data.subtitle().contains("2026-08-12"));
        assertTrue(data.subtitle().contains("2026-09-12"));
    }

    @Test
    @DisplayName("a legend explains P and Absent")
    void register_legend() {
        stubReport();
        ExportData data = capture(ExportFormat.XLSX);

        boolean legend = data.rows().stream()
                .anyMatch(r -> !r.isEmpty() && String.valueOf(r.get(0)).equals("Legend"));
        assertTrue(legend, "expected a P = Present / A = Absent legend row");
    }

    @Test
    @DisplayName("PDF is a clean summary, not the wide per-session matrix")
    void pdf_isSummaryOnly() {
        stubReport();
        ExportData data = capture(ExportFormat.PDF);

        assertEquals(List.of("Enrollment No.", "Student Name", "Present", "Total Classes",
                "Percentage"), data.headers());

        List<Object> row = data.rows().get(0);
        assertEquals(5, row.size());
        assertEquals("DAGACS001", row.get(0));
        assertEquals("Rahul Sharma", row.get(1));
        assertEquals(2L, row.get(2));
        assertEquals(3L, row.get(3));
        assertEquals("66.67%", row.get(4));

        // No per-session date columns and no legend row in the PDF.
        assertFalse(data.headers().stream().anyMatch(h -> h.contains("LP1")));
        assertTrue(data.subtitle().contains("Subject: DBMS"));
    }

    @Test
    @DisplayName("Excel and PDF agree on every attendance number for the same filters")
    void excelAndPdf_agree() {
        stubReport();
        ExportData xlsx = capture(ExportFormat.XLSX);
        ExportData pdf = capture(ExportFormat.PDF);

        for (int i = 0; i < 2; i++) {
            List<Object> excelRow = xlsx.rows().get(i);
            List<Object> pdfRow = pdf.rows().get(i);
            assertEquals(excelRow.get(0), pdfRow.get(0), "enrollment");
            assertEquals(excelRow.get(1), pdfRow.get(1), "name");
            assertEquals(excelRow.get(5), pdfRow.get(2), "present");
            assertEquals(excelRow.get(6), pdfRow.get(3), "total classes");
            assertEquals(excelRow.get(7), pdfRow.get(4), "percentage");
        }
    }

    @Test
    @DisplayName("a null percentage (no conducted class) renders safely without dividing by zero")
    void nullPercentage_isSafe() {
        StudentWiseReportDTO empty = StudentWiseReportDTO.builder()
                .subjectId(1L).subjectName("DBMS").sectionId(2L).sectionName("CSE-A")
                .columns(List.of()).rows(List.of()).build();
        when(teacherResolver.resolve())
                .thenReturn(Teacher.builder().id(7L).fullName("Asha Teacher").build());
        when(studentWiseReportService.getStudentWiseReport(any(), any(), anyLong(), any(), any()))
                .thenReturn(empty);

        ExportData data = capture(ExportFormat.XLSX);
        assertTrue(data.rows().isEmpty(), "no students means no data rows");
    }
}
