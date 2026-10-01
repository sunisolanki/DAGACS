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
import com.dagacs.service.HodAcademicSelection;
import com.dagacs.service.HodAnalyticsService;
import com.dagacs.service.HodHierarchyService;
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
 * (see {@link com.dagacs.security.AuthenticatedHodResolver}).
 *
 * <p><b>M12 (HOD academic context).</b> The four list endpoints additionally
 * accept an optional academic context ({@code academicSessionId},
 * {@code programId}, {@code semesterId}, {@code sectionId}). Every supplied id
 * is proven to belong to the authenticated HOD's department before use and an
 * out-of-department id is rejected with 403; the department itself is never
 * accepted as a parameter. When no context is supplied the original frozen
 * department-wide query runs unchanged, so existing behaviour and callers are
 * fully preserved.</p>
 *
 * <p>Date ranges are optional and inclusive. Rollups remain monthly/quarterly
 * only. Semester rollup is intentionally rejected because it is NOT DERIVABLE
 * from the current schema (M6.2 plan, section 10).</p>
 */
@RestController
@RequestMapping("/api/hod")
@PreAuthorize("hasRole('HOD')")
public class HodAnalyticsController {

    private static final String TYPE_MONTHLY = "monthly";
    private static final String TYPE_QUARTERLY = "quarterly";

    private final HodAnalyticsService hodAnalyticsService;
    private final HodHierarchyService hodHierarchyService;

    public HodAnalyticsController(HodAnalyticsService hodAnalyticsService,
                                  HodHierarchyService hodHierarchyService) {
        this.hodAnalyticsService = hodAnalyticsService;
        this.hodHierarchyService = hodHierarchyService;
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
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId) {
        validateDateRange(startDate, endDate);
        HodAcademicSelection selection = selection(academicSessionId, programId, semesterId, sectionId);
        if (selection.isPresent()) {
            return ResponseEntity.ok(hodHierarchyService.getSectionAttendance(
                    selection, toCanonicalString(startDate), toCanonicalString(endDate)));
        }
        return ResponseEntity.ok(hodAnalyticsService.getSectionAttendance(startDate, endDate));
    }

    @GetMapping("/subjects")
    public ResponseEntity<List<HodSubjectAttendanceDTO>> getSubjects(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId) {
        validateDateRange(startDate, endDate);
        HodAcademicSelection selection = selection(academicSessionId, programId, semesterId, sectionId);
        if (selection.isPresent()) {
            return ResponseEntity.ok(hodHierarchyService.getSubjectAttendance(
                    selection, toCanonicalString(startDate), toCanonicalString(endDate)));
        }
        return ResponseEntity.ok(hodAnalyticsService.getSubjectAttendance(startDate, endDate));
    }

    @GetMapping("/students")
    public ResponseEntity<List<HodStudentAttendanceDTO>> getStudents(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId) {
        validateDateRange(startDate, endDate);
        HodAcademicSelection selection = selection(academicSessionId, programId, semesterId, sectionId);
        if (selection.isPresent()) {
            return ResponseEntity.ok(hodHierarchyService.getStudentAttendance(
                    selection, toCanonicalString(startDate), toCanonicalString(endDate)));
        }
        return ResponseEntity.ok(hodAnalyticsService.getStudentAttendance(startDate, endDate));
    }

    @GetMapping("/low-attendance")
    public ResponseEntity<List<HodLowAttendanceDTO>> getLowAttendance(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "academicSessionId", required = false) Long academicSessionId,
            @RequestParam(name = "programId", required = false) Long programId,
            @RequestParam(name = "semesterId", required = false) Long semesterId,
            @RequestParam(name = "sectionId", required = false) Long sectionId) {
        validateDateRange(startDate, endDate);
        HodAcademicSelection selection = selection(academicSessionId, programId, semesterId, sectionId);
        if (selection.isPresent()) {
            return ResponseEntity.ok(hodHierarchyService.getLowAttendance(
                    selection, toCanonicalString(startDate), toCanonicalString(endDate)));
        }
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

    private static HodAcademicSelection selection(Long academicSessionId,
                                                 Long programId,
                                                 Long semesterId,
                                                 Long sectionId) {
        return new HodAcademicSelection(academicSessionId, programId, semesterId, sectionId);
    }

    private static String toCanonicalString(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("startDate must not be after endDate");
        }
    }
}