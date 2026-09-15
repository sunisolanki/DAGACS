package com.dagacs.controller;

import com.dagacs.dto.AttendanceMarkRequestDTO;
import com.dagacs.dto.AttendanceRecordDTO;
import com.dagacs.dto.AttendanceSessionCreateRequestDTO;
import com.dagacs.dto.AttendanceSessionDTO;
import com.dagacs.dto.AttendanceSessionUpdateRequestDTO;
import com.dagacs.dto.AttendanceUpdateRequestDTO;
import com.dagacs.dto.StudentDTO;
import com.dagacs.dto.StudentWiseReportDTO;
import com.dagacs.exception.InvalidDateRangeException;
import com.dagacs.service.AttendanceService;
import com.dagacs.service.AttendanceSessionService;
import com.dagacs.service.TeacherStudentWiseReportService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/teacher/attendance")
@PreAuthorize("hasAnyRole('TEACHER', 'HOD')")
public class AttendanceController {

    private final AttendanceSessionService sessionService;
    private final AttendanceService attendanceService;
    private final TeacherStudentWiseReportService studentWiseReportService;

    public AttendanceController(AttendanceSessionService sessionService,
                                AttendanceService attendanceService,
                                TeacherStudentWiseReportService studentWiseReportService) {
        this.sessionService = sessionService;
        this.attendanceService = attendanceService;
        this.studentWiseReportService = studentWiseReportService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<AttendanceSessionDTO> createSession(
            @Valid @RequestBody AttendanceSessionCreateRequestDTO dto) {
        AttendanceSessionDTO created = sessionService.createSession(dto);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<AttendanceSessionDTO>> listSessions() {
        return ResponseEntity.ok(sessionService.listTeacherSessions());
    }

    @GetMapping("/sessions/{id}/students")
    public ResponseEntity<List<StudentDTO>> getStudentsForSession(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.getStudentsForSession(id));
    }

    @PutMapping("/sessions/{id}")
    public ResponseEntity<AttendanceSessionDTO> updateSession(
            @PathVariable Long id,
            @Valid @RequestBody AttendanceSessionUpdateRequestDTO dto) {
        return ResponseEntity.ok(sessionService.updateSession(id, dto));
    }

    @PostMapping("/mark")
    public ResponseEntity<List<AttendanceRecordDTO>> markAttendance(
            @Valid @RequestBody AttendanceMarkRequestDTO dto) {
        List<AttendanceRecordDTO> records = attendanceService.markAttendance(dto);
        return ResponseEntity.status(201).body(records);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AttendanceRecordDTO> updateAttendance(
            @PathVariable Long id,
            @Valid @RequestBody AttendanceUpdateRequestDTO dto) {
        return ResponseEntity.ok(attendanceService.updateAttendance(id, dto));
    }

    @GetMapping("/sessions/{id}/records")
    public ResponseEntity<List<AttendanceRecordDTO>> getAttendanceBySession(@PathVariable Long id) {
        return ResponseEntity.ok(attendanceService.getAttendanceBySession(id));
    }

    /**
     * Student-wise attendance matrix. Locked context contract (M10B):
     * subjectId is required and exactly one of sectionId/batchId must select the
     * teaching target; both/neither/missing context is rejected, nonexistent IDs
     * are 404, and a context outside the authenticated teacher's assignments is
     * 403. Date range validation is optional and follows the project convention.
     */
    @GetMapping("/student-wise")
    public ResponseEntity<StudentWiseReportDTO> getStudentWiseReport(
            @RequestParam(name = "startDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(name = "endDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(name = "subjectId") Long subjectId,
            @RequestParam(name = "sectionId", required = false) Long sectionId,
            @RequestParam(name = "batchId", required = false) Long batchId) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidDateRangeException("startDate must not be after endDate");
        }
        return ResponseEntity.ok(studentWiseReportService.getStudentWiseReport(
                startDate, endDate, subjectId, sectionId, batchId));
    }
}
