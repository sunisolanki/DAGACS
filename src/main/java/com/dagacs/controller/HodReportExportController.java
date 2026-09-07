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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * M7.2 HOD report export endpoints. Department scope is always derived from the
 * JWT (see {@link com.dagacs.security.AuthenticatedHodResolver}); the only
 * accepted parameters are the whitelisted report type and an optional inclusive
 * date range. The {@code reportType} whitelist intentionally excludes
 * {@code semester} (not derivable) — exactly like the frozen M6.2
 * {@code type=semester -> 400} behavior. Exports are generated in memory and
 * streamed back as an attachment; no filesystem path is exposed.
 */
@RestController
@RequestMapping("/api/hod")
@PreAuthorize("hasRole('HOD')")
public class HodReportExportController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String PDF_MEDIA_TYPE = "application/pdf";

    private final ReportExportService exportService;

    public HodReportExportController(ReportExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/reports/{reportType}/export.xlsx")
    public ResponseEntity<byte[]> exportXlsx(@PathVariable("reportType") String reportType,
                                             @RequestParam(name = "startDate", required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                             @RequestParam(name = "endDate", required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        byte[] bytes = exportService.exportHod(reportType, ExportFormat.XLSX, startDate, endDate);
        return buildResponse(bytes, XLSX_MEDIA_TYPE,
                ReportFileNames.forHod(reportType, startDate, endDate, "xlsx"));
    }

    @GetMapping("/reports/{reportType}/export.pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable("reportType") String reportType,
                                            @RequestParam(name = "startDate", required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                            @RequestParam(name = "endDate", required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        byte[] bytes = exportService.exportHod(reportType, ExportFormat.PDF, startDate, endDate);
        return buildResponse(bytes, PDF_MEDIA_TYPE,
                ReportFileNames.forHod(reportType, startDate, endDate, "pdf"));
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