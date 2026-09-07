package com.dagacs.controller;

import com.dagacs.dto.HodAuditLogEntryDTO;
import com.dagacs.dto.HodDashboardDTO;
import com.dagacs.dto.HodLowAttendanceDTO;
import com.dagacs.dto.HodRollupDTO;
import com.dagacs.dto.HodSectionAttendanceDTO;
import com.dagacs.dto.HodStudentAttendanceDTO;
import com.dagacs.dto.HodSubjectAttendanceDTO;
import com.dagacs.exception.AuthException;
import com.dagacs.exception.InvalidDateRangeException;
import com.dagacs.service.HodAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only M6.2 HOD analytics API. All identity/scope is derived from the JWT
 * (see {@link com.dagacs.security.AuthenticatedHodResolver}); the only accepted
 * request parameters are optional date ranges and, for rollups, the
 * monthly|quarterly type. Semester rollup is intentionally rejected because it
 * is NOT DERIVABLE from the current schema (M6.2 plan, section 10).
 */
@RestController
@RequestMapping("/api/hod")
@PreAuthorize("hasRole('HOD')")
public class HodAnalyticsController {

    private static final String TYPE_MONTHLY = "monthly";
    private static final String TYPE_QUARTERLY = "quarterly";

    private final HodAnalyticsService hodAnalyticsService;

    public HodAnalyticsController(HodAnalyticsService hodAnalyticsService) {
        this.hodAnalyticsService = hodAnalyticsService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<HodDashboardDTO> getDashboard(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(hodAnalyticsService.getDashboard(startDate, endDate));
    }

    @GetMapping("/sections")
    public ResponseEntity<List<HodSectionAttendanceDTO>> getSections(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(hodAnalyticsService.getSectionAttendance(startDate, endDate));
    }

    @GetMapping("/subjects")
    public ResponseEntity<List<HodSubjectAttendanceDTO>> getSubjects(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(hodAnalyticsService.getSubjectAttendance(startDate, endDate));
    }

    @GetMapping("/students")
    public ResponseEntity<List<HodStudentAttendanceDTO>> getStudents(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(hodAnalyticsService.getStudentAttendance(startDate, endDate));
    }

    @GetMapping("/low-attendance")
    public ResponseEntity<List<HodLowAttendanceDTO>> getLowAttendance(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(hodAnalyticsService.getLowAttendance(startDate, endDate));
    }

    @GetMapping("/rollups")
    public ResponseEntity<List<HodRollupDTO>> getRollups(
            @RequestParam("type") String type,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        if (!TYPE_MONTHLY.equalsIgnoreCase(type) && !TYPE_QUARTERLY.equalsIgnoreCase(type)) {
            throw new AuthException("type must be either monthly or quarterly", 400);
        }
        return ResponseEntity.ok(hodAnalyticsService.getRollups(type, startDate, endDate));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<HodAuditLogEntryDTO>> getAuditLogs(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(hodAnalyticsService.getAuditLogs(startDate, endDate));
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("startDate must not be after endDate");
        }
    }
}