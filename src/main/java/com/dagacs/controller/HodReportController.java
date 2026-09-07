package com.dagacs.controller;

import com.dagacs.dto.HodCoverageReportDTO;
import com.dagacs.dto.HodDailyLectureReportDTO;
import com.dagacs.exception.InvalidDateRangeException;
import com.dagacs.service.HodReportService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Read-only M7.1 HOD report feeds. Department scope is derived from the JWT
 * (see {@link com.dagacs.security.AuthenticatedHodResolver}); the only accepted
 * request parameters are optional inclusive date ranges and pagination. Both
 * feeds are record-backed: only sessions/subject-sections with actual
 * AttendanceRecord rows are reported (no timetable, no expected-lecture
 * semantics — see the approved M7.1 plan correction, section 1).
 */
@RestController
@RequestMapping("/api/hod")
@PreAuthorize("hasRole('HOD')")
public class HodReportController {

    private final HodReportService hodReportService;

    public HodReportController(HodReportService hodReportService) {
        this.hodReportService = hodReportService;
    }

    @GetMapping("/reports/daily-lecture")
    public ResponseEntity<Page<HodDailyLectureReportDTO>> getDailyLecture(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(hodReportService.getDailyLecture(page, size, startDate, endDate));
    }

    @GetMapping("/coverage")
    public ResponseEntity<Page<HodCoverageReportDTO>> getCoverage(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(hodReportService.getCoverage(page, size, startDate, endDate));
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("startDate must not be after endDate");
        }
    }
}