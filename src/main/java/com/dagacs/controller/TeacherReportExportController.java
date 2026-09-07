package com.dagacs.controller;

import com.dagacs.exception.InvalidDateRangeException;
import com.dagacs.export.ExportFormat;
import com.dagacs.export.ReportExportService;
import com.dagacs.export.ReportFileNames;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * M7.2 teacher subject-wise attendance export. Self-scope is derived from the
 * JWT (see {@link com.dagacs.security.AuthenticatedTeacherResolver}); the export
 * contains ALL matching rows of the teacher's own subject-wise report for the
 * selected date range (full-data retrieval — never a paginated subset; see
 * {@link ReportExportService}). No client-supplied teacherId/departmentId/
 * sectionId/studentId is accepted.
 */
@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherReportExportController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PDF_MEDIA_TYPE = "application/pdf";

    private final ReportExportService exportService;

    public TeacherReportExportController(ReportExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/attendance/report/export.xlsx")
    public ResponseEntity<byte[]> exportXlsx(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        byte[] bytes = exportService.exportTeacher(ExportFormat.XLSX, startDate, endDate);
        return buildResponse(bytes, XLSX_MEDIA_TYPE,
                ReportFileNames.forTeacher(startDate, endDate, "xlsx"));
    }

    @GetMapping("/attendance/report/export.pdf")
    public ResponseEntity<byte[]> exportPdf(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        byte[] bytes = exportService.exportTeacher(ExportFormat.PDF, startDate, endDate);
        return buildResponse(bytes, PDF_MEDIA_TYPE,
                ReportFileNames.forTeacher(startDate, endDate, "pdf"));
    }

    private static ResponseEntity<byte[]> buildResponse(byte[] body, String mediaType, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(body);
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("startDate must not be after endDate");
        }
    }
}