package com.dagacs.controller;

import com.dagacs.export.ExportFormat;
import com.dagacs.export.HodAttendanceMatrixExportService;
import com.dagacs.export.HodAttendanceReportExportService;
import com.dagacs.service.HodAttendanceReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 4A: the wrong-report export bug, proven at the server seam.
 *
 * <p>Phase 4A exists because an export could be produced for a report the reader
 * was not looking at. On the client the dispatch was a single call that every
 * academic-context report shared, so choosing "Attendance Overview" and clicking
 * Excel silently produced the Attendance Matrix. This class is the server-side
 * half of the same guarantee: <b>each of the ten Phase 4A paths must reach its
 * own export service method, and only its own.</b></p>
 *
 * <p>The assertion that matters is not "the overview endpoint returns 200" - it
 * is {@code verifyNoInteractions} on the <i>other</i> four services. A controller
 * that quietly delegated the overview to the matrix service would still return a
 * perfectly valid 200 with a real file, and would be indistinguishable from
 * correct behaviour to any status-code assertion. Only an explicit
 * "this service was never touched" check catches it.</p>
 *
 * <p>The data services are mocked on purpose. This class is about
 * <b>routing</b>, not arithmetic: the canonical attendance figures are proven
 * exhaustively by {@code HodAttendanceConductedSessionIntegrationTest}, and the
 * export arrangement by {@code HodAttendanceReportExportTest}. Mocking here keeps
 * the routing proof independent of both.</p>
 *
 * <p>Authorization is deliberately <i>not</i> what this class is about - it is
 * proven with real JWTs and real fixtures by
 * {@code HodAttendanceExportSecurityIntegrationTest}. Here a mock HOD principal
 * is enough, because the whole point is to observe which bean the controller
 * calls; if the role check were removed, this class would still pass, and the
 * security class would still fail.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "hod@dagacs.local", roles = "HOD")
class HodAttendanceReportControllerDispatchTest {

    private static final String QUERY =
            "?academicSessionId=1&programId=2&semesterId=3&sectionId=4";
    private static final String RANGE = "&startDate=2026-01-01&endDate=2026-01-31";
    private static final LocalDate START = LocalDate.of(2026, 1, 1);
    private static final LocalDate END = LocalDate.of(2026, 1, 31);

    @Autowired private MockMvc mockMvc;

    @MockBean private HodAttendanceReportService attendanceReportService;
    @MockBean private HodAttendanceMatrixExportService matrixExportService;
    @MockBean private HodAttendanceReportExportService reportExportService;

    /** A real xlsx container signature, so the attachment path is exercised too. */
    private static byte[] xlsx() {
        return new byte[]{'P', 'K', 3, 4, 0, 0};
    }

    private static byte[] pdf() {
        return new byte[]{'%', 'P', 'D', 'F', 0, 0};
    }

    private void stubAll() {
        when(matrixExportService.export(any(), any(), any(), any(), any()))
                .thenReturn(xlsx());
        when(reportExportService.exportOverview(any(), any(), any(), any()))
                .thenReturn(new HodAttendanceReportExportService.ExportedFile(
                        xlsx(), "overview.xlsx"));
        when(reportExportService.exportStudent(any(), any(), anyLong(), any(), any(), any()))
                .thenReturn(new HodAttendanceReportExportService.ExportedFile(
                        xlsx(), "student.xlsx"));
        when(reportExportService.exportSubject(any(), any(), anyLong(), any(), any()))
                .thenReturn(new HodAttendanceReportExportService.ExportedFile(
                        xlsx(), "subject.xlsx"));
        when(reportExportService.exportLow(any(), any(), any(), any()))
                .thenReturn(new HodAttendanceReportExportService.ExportedFile(
                        xlsx(), "low.xlsx"));
    }

    /** Asserts the four report services were reached exactly once, as specified. */
    private void verifyOnlyMatrixCalled() {
        verify(matrixExportService).export(eq(ExportFormat.XLSX), any(), any(), any(), any());
        verify(reportExportService, never()).exportOverview(any(), any(), any(), any());
        verify(reportExportService, never()).exportStudent(any(), any(), anyLong(), any(), any(), any());
        verify(reportExportService, never()).exportSubject(any(), any(), anyLong(), any(), any());
        verify(reportExportService, never()).exportLow(any(), any(), any(), any());
    }

    private void verifyOnlyOverviewCalled(ExportFormat format) {
        verify(reportExportService).exportOverview(eq(format), any(), any(), any());
        verify(matrixExportService, never()).export(any(), any(), any(), any(), any());
        verify(reportExportService, never()).exportStudent(any(), any(), anyLong(), any(), any(), any());
        verify(reportExportService, never()).exportSubject(any(), any(), anyLong(), any(), any());
        verify(reportExportService, never()).exportLow(any(), any(), any(), any());
    }

    private void verifyOnlyStudentCalled(ExportFormat format) {
        verify(reportExportService).exportStudent(eq(format), any(), eq(91L), any(), any(), any());
        verify(matrixExportService, never()).export(any(), any(), any(), any(), any());
        verify(reportExportService, never()).exportOverview(any(), any(), any(), any());
        verify(reportExportService, never()).exportSubject(any(), any(), anyLong(), any(), any());
        verify(reportExportService, never()).exportLow(any(), any(), any(), any());
    }

    private void verifyOnlySubjectCalled(ExportFormat format) {
        verify(reportExportService).exportSubject(eq(format), any(), eq(101L), any(), any());
        verify(matrixExportService, never()).export(any(), any(), any(), any(), any());
        verify(reportExportService, never()).exportOverview(any(), any(), any(), any());
        verify(reportExportService, never()).exportStudent(any(), any(), anyLong(), any(), any(), any());
        verify(reportExportService, never()).exportLow(any(), any(), any(), any());
    }

    private void verifyOnlyLowCalled(ExportFormat format) {
        verify(reportExportService).exportLow(eq(format), any(), any(), any());
        verify(matrixExportService, never()).export(any(), any(), any(), any(), any());
        verify(reportExportService, never()).exportOverview(any(), any(), any(), any());
        verify(reportExportService, never()).exportStudent(any(), any(), anyLong(), any(), any(), any());
        verify(reportExportService, never()).exportSubject(any(), any(), anyLong(), any(), any());
    }

    private MvcResult call(String path) throws Exception {
        return mockMvc.perform(get(path)).andExpect(status().isOk()).andReturn();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Attendance Overview
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the overview export reaches only the overview exporter")
    void overviewXlsxIsNotTheMatrix() throws Exception {
        stubAll();

        MvcResult result = call("/api/hod/attendance/overview/export.xlsx" + QUERY + RANGE);

        verifyOnlyOverviewCalled(ExportFormat.XLSX);
        assertArrayEquals(xlsx(), result.getResponse().getContentAsByteArray());
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                result.getResponse().getContentType());
    }

    @Test
    @DisplayName("the overview PDF reaches only the overview exporter")
    void overviewPdfIsNotTheMatrix() throws Exception {
        when(reportExportService.exportOverview(any(), any(), any(), any()))
                .thenReturn(new HodAttendanceReportExportService.ExportedFile(
                        pdf(), "overview.pdf"));

        MvcResult result = call("/api/hod/attendance/overview/export.pdf" + QUERY + RANGE);

        verifyOnlyOverviewCalled(ExportFormat.PDF);
        assertArrayEquals(pdf(), result.getResponse().getContentAsByteArray());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Attendance Matrix
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the matrix export reaches only the matrix exporter")
    void matrixXlsxIsNotAnyOther() throws Exception {
        stubAll();

        call("/api/hod/attendance/matrix/export.xlsx" + QUERY + RANGE);

        verifyOnlyMatrixCalled();
    }

    @Test
    @DisplayName("the matrix PDF reaches only the matrix exporter")
    void matrixPdfIsNotAnyOther() throws Exception {
        when(matrixExportService.export(any(), any(), any(), any(), any()))
                .thenReturn(pdf());

        call("/api/hod/attendance/matrix/export.pdf" + QUERY + RANGE);

        verify(matrixExportService).export(eq(ExportFormat.PDF), any(), any(), any(), any());
        verify(reportExportService, never()).exportOverview(any(), any(), any(), any());
        verify(reportExportService, never()).exportStudent(any(), any(), anyLong(), any(), any(), any());
        verify(reportExportService, never()).exportSubject(any(), any(), anyLong(), any(), any());
        verify(reportExportService, never()).exportLow(any(), any(), any(), any());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Student Attendance
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the student export reaches only the student exporter")
    void studentXlsxIsNotAnyOther() throws Exception {
        stubAll();

        MvcResult result =
                call("/api/hod/attendance/student/91/export.xlsx" + QUERY + RANGE);

        verifyOnlyStudentCalled(ExportFormat.XLSX);
        assertEquals("student.xlsx", filenameOf(result));
    }

    @Test
    @DisplayName("the student PDF reaches only the student exporter")
    void studentPdfIsNotAnyOther() throws Exception {
        stubAll();

        call("/api/hod/attendance/student/91/export.pdf" + QUERY + RANGE);

        verifyOnlyStudentCalled(ExportFormat.PDF);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Subject Attendance
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the subject export reaches only the subject exporter")
    void subjectXlsxIsNotAnyOther() throws Exception {
        stubAll();

        MvcResult result =
                call("/api/hod/attendance/subject/101/export.xlsx" + QUERY + RANGE);

        verifyOnlySubjectCalled(ExportFormat.XLSX);
        assertEquals("subject.xlsx", filenameOf(result));
    }

    @Test
    @DisplayName("the subject PDF reaches only the subject exporter")
    void subjectPdfIsNotAnyOther() throws Exception {
        stubAll();

        call("/api/hod/attendance/subject/101/export.pdf" + QUERY + RANGE);

        verifyOnlySubjectCalled(ExportFormat.PDF);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Low Attendance
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the low-attendance export reaches only the low exporter")
    void lowXlsxIsNotTheMatrix() throws Exception {
        stubAll();

        MvcResult result = call("/api/hod/attendance/low/export.xlsx" + QUERY + RANGE);

        verifyOnlyLowCalled(ExportFormat.XLSX);
        assertEquals("low.xlsx", filenameOf(result));
    }

    @Test
    @DisplayName("the low-attendance PDF reaches only the low exporter")
    void lowPdfIsNotTheMatrix() throws Exception {
        stubAll();

        call("/api/hod/attendance/low/export.pdf" + QUERY + RANGE);

        verifyOnlyLowCalled(ExportFormat.PDF);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // The context travels with the request
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("the applied context and range reach the exporter verbatim")
    void contextIsForwardedUnchanged() throws Exception {
        stubAll();

        call("/api/hod/attendance/overview/export.xlsx" + QUERY + RANGE);

        // The selection is forwarded as-is: the exporter calls the same canonical
        // service method the on-screen report uses, so it must see the same
        // ids and the same range or the file could describe another context.
        verify(reportExportService).exportOverview(eq(ExportFormat.XLSX),
                eq(new com.dagacs.service.HodAcademicSelection(1L, 2L, 3L, 4L)),
                eq(START), eq(END));
    }

    @Test
    @DisplayName("a subjectId is forwarded to the matrix exporter only")
    void subjectIdIsForwarded() throws Exception {
        stubAll();

        call("/api/hod/attendance/matrix/export.xlsx" + QUERY + "&subjectId=101" + RANGE);

        verify(matrixExportService).export(eq(ExportFormat.XLSX), any(), eq(101L),
                eq("2026-01-01"), eq("2026-01-31"));
    }

    @Test
    @DisplayName("every path is an attachment with a file name")
    void everyPathIsAnAttachment() throws Exception {
        stubAll();
        when(matrixExportService.fileName(any(), any(), any())).thenReturn("matrix.xlsx");

        String[] paths = {
                "/api/hod/attendance/matrix/export.xlsx" + QUERY,
                "/api/hod/attendance/matrix/export.pdf" + QUERY,
                "/api/hod/attendance/overview/export.xlsx" + QUERY,
                "/api/hod/attendance/overview/export.pdf" + QUERY,
                "/api/hod/attendance/student/91/export.xlsx" + QUERY,
                "/api/hod/attendance/student/91/export.pdf" + QUERY,
                "/api/hod/attendance/subject/101/export.xlsx" + QUERY,
                "/api/hod/attendance/subject/101/export.pdf" + QUERY,
                "/api/hod/attendance/low/export.xlsx" + QUERY,
                "/api/hod/attendance/low/export.pdf" + QUERY,
        };

        for (String path : paths) {
            MvcResult result = call(path);
            String disposition = result.getResponse().getHeader("Content-Disposition");
            assertTrue(disposition != null && disposition.contains("attachment"),
                    "Every export is an attachment: " + path);
            assertTrue(disposition.contains("filename="),
                    "Every export carries a file name: " + path);
        }
    }

    private static String filenameOf(MvcResult result) {
        String disposition = result.getResponse().getHeader("Content-Disposition");
        assertTrue(disposition != null, "the response must carry a file name");
        int at = disposition.indexOf("filename=");
        return disposition.substring(at + "filename=".length()).replace("\"", "");
    }
}
