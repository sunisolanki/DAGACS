package com.dagacs.controller;

import com.dagacs.dto.AttendancePercentageDTO;
import com.dagacs.dto.CalendarAttendanceDTO;
import com.dagacs.dto.SubjectAttendanceDTO;
import com.dagacs.service.AttendanceCalculationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Read-only attendance calculation API for students.
 *
 * All calculations are derived from the authenticated student's own
 * AttendanceRecord data via the existing JWT identity.
 * No studentId is accepted from the client.
 */
@RestController
@RequestMapping("/api/student/attendance/calculation")
@PreAuthorize("hasRole('STUDENT')")
public class AttendanceCalculationController {

    private final AttendanceCalculationService calculationService;

    public AttendanceCalculationController(AttendanceCalculationService calculationService) {
        this.calculationService = calculationService;
    }

    @GetMapping("/subject/{subjectId}")
    public ResponseEntity<AttendancePercentageDTO> getSubjectCalculation(
            @PathVariable Long subjectId,
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(calculationService.getSubjectCalculation(subjectId, startDate, endDate));
    }

    @GetMapping
    public ResponseEntity<AttendancePercentageDTO> getOverallCalculation(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(calculationService.getOverallCalculation(startDate, endDate));
    }

    @GetMapping("/subjects")
    public ResponseEntity<List<SubjectAttendanceDTO>> getSubjectSummaries(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(calculationService.getSubjectSummaries(startDate, endDate));
    }

    @GetMapping("/calendar")
    public ResponseEntity<List<CalendarAttendanceDTO>> getCalendarSummary(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(calculationService.getCalendarSummary(startDate, endDate));
    }
}
