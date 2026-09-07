package com.dagacs.controller;

import com.dagacs.dto.AttendanceMarkRequestDTO;
import com.dagacs.dto.AttendanceRecordDTO;
import com.dagacs.dto.AttendanceSessionCreateRequestDTO;
import com.dagacs.dto.AttendanceSessionDTO;
import com.dagacs.dto.AttendanceSessionUpdateRequestDTO;
import com.dagacs.dto.AttendanceUpdateRequestDTO;
import com.dagacs.dto.StudentDTO;
import com.dagacs.service.AttendanceService;
import com.dagacs.service.AttendanceSessionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teacher/attendance")
@PreAuthorize("hasRole('TEACHER')")
public class AttendanceController {

    private final AttendanceSessionService sessionService;
    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceSessionService sessionService,
                                AttendanceService attendanceService) {
        this.sessionService = sessionService;
        this.attendanceService = attendanceService;
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
}
