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
            // The register opts into the frozen header / identity columns.
            org.mockito.Mockito.verify(excelGenerator).generate(captor.capture(), eq(true));
        } else {
            org.mockito.Mockito.verify(pdfGenerator).generate(captor.capture());
        }
        return captor.getValue();
    }

    @Test
    @DisplayName("register columns are Enrollment, Name, session dates, Total Present, Total Classes, Percentage")
    void register_columnOrder() {
        stubReport();
        ExportData data = capture(ExportFormat.XLSX);

        List<String> headers = data.headers();
        assertEquals("Enrollment No.", headers.get(0));
        assertEquals("Student Name", headers.get(1));
        // One column per CONDUCTED session date, in chronological order.
        assertEquals("12-Aug-2026 (LP1)", headers.get(2));
        assertEquals("14-Aug-2026 (LP1)", headers.get(3));
        assertEquals("18-Aug-2026 (LP1)", headers.get(4));
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
    @DisplayName("the header block carries DAGACS, the report name, subject, section, batch, teacher and range")
    void register_metadata() {
        stubReport();
        ExportData data = capture(ExportFormat.XLSX);

        assertEquals("DAGACS", data.title());
        assertEquals("STUDENT ATTENDANCE REPORT", data.subtitle());

        List<String> meta = data.metaLines();
        assertEquals(5, meta.size(), meta.toString());
        assertEquals("Subject: DBMS", meta.get(0));
        assertEquals("Section: CSE-A", meta.get(1));
        // Section-scoped report: no batch, rendered explicitly as a dash.
        assertEquals("Batch: -", meta.get(2));
        assertEquals("Teacher: Asha Teacher", meta.get(3));
        assertTrue(meta.get(4).contains("2026-08-12"), meta.get(4));
        assertTrue(meta.get(4).contains("2026-09-12"), meta.get(4));
    }

    @Test
    @DisplayName("there is exactly one Present / Total Classes / Percentage trio, never duplicated")
    void register_noDuplicateTrailingColumns() {
        stubReport();
        ExportData data = capture(ExportFormat.XLSX);

        assertEquals(1, count(data.headers(), "Present"));
        assertEquals(1, count(data.headers(), "Total Classes"));
        assertEquals(1, count(data.headers(), "Percentage"));
        // The old buggy shape was "... | Present | Total Classes | Percentage
        // | Total Present | Total Classes | Percentage".
        assertEquals(8, data.headers().size(),
                "identity + 3 sessions + 3 totals: " + data.headers());
    }

    private static long count(List<String> values, String target) {
        return values.stream().filter(target::equals).count();
    }

    @Test
    @DisplayName("a legend explains P and A, including that unmarked counts as absent")
    void register_legend() {
        stubReport();
        ExportData data = capture(ExportFormat.XLSX);

        List<Object> legend = data.rows().stream()
                .filter(r -> !r.isEmpty() && "Legend".equals(String.valueOf(r.get(0))))
                .findFirst()
                .orElseThrow(() -> new AssertionError("expected a P = Present / A = Absent legend row"));
        assertTrue(String.valueOf(legend.get(1)).contains("P = Present"));
        assertTrue(String.valueOf(legend.get(1)).contains("A = Absent"));
        assertTrue(String.valueOf(legend.get(1)).contains("Unmarked/Missing = A"));
    }

    @Test
    @DisplayName("the PDF is the same date-wise register, not a summary without date columns")
    void pdf_isDateWiseRegister() {
        stubReport();
        ExportData data = capture(ExportFormat.PDF);

        // The PDF keeps the per-session date columns, so it shows the same
        // attendance detail as the web report and the spreadsheet.
        assertEquals(List.of("Enrollment No.", "Student Name",
                "12-Aug-2026 (LP1)", "14-Aug-2026 (LP1)", "18-Aug-2026 (LP1)",
                "Present", "Total Classes", "Percentage"), data.headers());
        List<Object> row = data.rows().get(0);
        assertEquals("DAGACS001", row.get(0));
        assertEquals("Rahul Sharma", row.get(1));
        assertEquals("P", row.get(2));
        assertEquals("A", row.get(3));
        assertEquals("P", row.get(4));
        assertEquals(2L, row.get(5));
        assertEquals(3L, row.get(6));
        assertEquals("66.67%", row.get(7));
    }

    @Test
    @DisplayName("Excel and PDF agree on every cell for the same filters")
    void excelAndPdf_agree() {
        stubReport();
        ExportData xlsx = capture(ExportFormat.XLSX);
        ExportData pdf = capture(ExportFormat.PDF);

        assertEquals(xlsx.headers(), pdf.headers());
        assertEquals(xlsx.rows(), pdf.rows());
    }

    @Test
    @DisplayName("an unmarked or missing attendance mark renders as A, never blank")
    void unmarked_isAbsent() {
        StudentWiseColumnDTO c1 = StudentWiseColumnDTO.builder()
                .sessionId(100L).date("2026-08-12").lecturePeriod("LP1").build();
        StudentWiseColumnDTO c2 = StudentWiseColumnDTO.builder()
                .sessionId(101L).date("2026-08-14").lecturePeriod("LP1").build();
        StudentWiseColumnDTO c3 = StudentWiseColumnDTO.builder()
                .sessionId(102L).date("2026-08-18").lecturePeriod("LP1").build();

        // Session 100 present, 101 explicitly absent, 102 has no record at all
        // (the teacher never marked it). Only one present of three conducted
        // classes -> 33.33%.
        StudentWiseRowDTO row = StudentWiseRowDTO.builder()
                .studentId(1L)
                .enrollmentNumber("DAGACS001")
                .name("Rahul Sharma")
                .presentCount(1L)
                .totalRecordedCount(3L)
                .percentage(100.0 / 3)
                .cells(List.of(
                        StudentWiseCellDTO.builder().sessionId(100L).status("PRESENT").isPresent(true).build(),
                        StudentWiseCellDTO.builder().sessionId(101L).status("ABSENT").isPresent(false).build()))
                .build();

        when(teacherResolver.resolve())
                .thenReturn(Teacher.builder().id(7L).fullName("Asha Teacher").build());
        when(studentWiseReportService.getStudentWiseReport(any(), any(), anyLong(), any(), any()))
                .thenReturn(StudentWiseReportDTO.builder()
                        .subjectId(1L).subjectName("DBMS").sectionId(2L).sectionName("CSE-A")
                        .columns(List.of(c1, c2, c3))
                        .rows(List.of(row))
                        .build());

        ExportData data = capture(ExportFormat.XLSX);
        List<Object> exported = data.rows().get(0);
        assertEquals("P", exported.get(2), "marked present");
        assertEquals("A", exported.get(3), "marked absent");
        assertEquals("A", exported.get(4), "unmarked must render as A, not blank");
        // The unmarked session still counts towards the shared denominator.
        assertEquals(3L, exported.get(6));
        assertEquals("33.33%", exported.get(7));

        for (Object cell : exported.subList(2, 5)) {
            assertFalse(String.valueOf(cell).isBlank(),
                    "an unmarked session must never render as an empty cell");
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
