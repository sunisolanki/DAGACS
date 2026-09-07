package com.dagacs.controller;

import com.dagacs.dto.AttendanceRecordDTO;
import com.dagacs.service.StudentAttendanceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only student attendance API. The authenticated student is resolved from the JWT
 * security context; no studentId is accepted from the client, so a student can never read
 * another student's attendance.
 */
@RestController
@RequestMapping("/api/student/attendance")
@PreAuthorize("hasRole('STUDENT')")
public class StudentAttendanceController {

    private final StudentAttendanceService studentAttendanceService;

    public StudentAttendanceController(StudentAttendanceService studentAttendanceService) {
        this.studentAttendanceService = studentAttendanceService;
    }

    @GetMapping("/my")
    public ResponseEntity<List<AttendanceRecordDTO>> getMyAttendance() {
        return ResponseEntity.ok(studentAttendanceService.getMyAttendance());
    }
}
