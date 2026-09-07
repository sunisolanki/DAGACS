package com.dagacs.controller;

import com.dagacs.dto.TeacherSubjectReportDTO;
import com.dagacs.exception.InvalidDateRangeException;
import com.dagacs.service.TeacherReportService;
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
 * Read-only M7.1 teacher subject-wise attendance report. Self-scope is derived
 * from the JWT (see {@link com.dagacs.security.AuthenticatedTeacherResolver});
 * only the authenticated teacher's own recorded classes, within their own
 * teaching assignments, are reported. subjectId/sectionId are returned only as
 * neutral labels and are never accepted as request parameters.
 */
@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasRole('TEACHER')")
public class TeacherReportController {

    private final TeacherReportService teacherReportService;

    public TeacherReportController(TeacherReportService teacherReportService) {
        this.teacherReportService = teacherReportService;
    }

    @GetMapping("/attendance/report")
    public ResponseEntity<List<TeacherSubjectReportDTO>> getSubjectReport(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        validateDateRange(startDate, endDate);
        return ResponseEntity.ok(teacherReportService.getSubjectReport(startDate, endDate));
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("startDate must not be after endDate");
        }
    }
}