package com.dagacs.controller;

import com.dagacs.dto.TeacherAssignmentDTO;
import com.dagacs.dto.TeacherAssignmentRequestDTO;
import com.dagacs.dto.TeacherDTO;
import com.dagacs.service.TeacherAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminTeacherAssignmentController {

    private final TeacherAssignmentService teacherAssignmentService;

    public AdminTeacherAssignmentController(TeacherAssignmentService teacherAssignmentService) {
        this.teacherAssignmentService = teacherAssignmentService;
    }

    @GetMapping("/teacher-assignments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TeacherAssignmentDTO>> getAllTeacherAssignments() {
        return ResponseEntity.ok(teacherAssignmentService.getAllAssignments());
    }

    @PostMapping("/teacher-assignments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeacherAssignmentDTO> createTeacherAssignment(
            @Valid @RequestBody TeacherAssignmentRequestDTO requestDTO) {
        return ResponseEntity.status(201).body(teacherAssignmentService.createAssignment(requestDTO));
    }

    @PutMapping("/teacher-assignments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeacherAssignmentDTO> updateTeacherAssignment(
            @PathVariable Long id,
            @Valid @RequestBody TeacherAssignmentRequestDTO requestDTO) {
        return ResponseEntity.ok(teacherAssignmentService.updateAssignment(id, requestDTO));
    }

    @DeleteMapping("/teacher-assignments/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTeacherAssignment(@PathVariable Long id) {
        teacherAssignmentService.deleteAssignment(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/teachers")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TeacherDTO>> getAllTeachers() {
        return ResponseEntity.ok(teacherAssignmentService.listTeachers());
    }
}