package com.dagacs.controller;

import com.dagacs.dto.StudentLoginRequestDTO;
import com.dagacs.dto.StudentManagementDTO;
import com.dagacs.dto.StudentManagementRequestDTO;
import com.dagacs.dto.StudentPasswordUpdateRequestDTO;
import com.dagacs.dto.StudentStatusDTO;
import com.dagacs.service.StudentManagementService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin student master-management API (M5.2).
 * <p>
 * System-wide admin management of the student master data. Mirrors the existing
 * master-data controllers: the whole surface is ADMIN-only via the existing
 * {@code /api/admin/**} security rule plus per-method {@code @PreAuthorize}.
 * </p>
 */
@RestController
@RequestMapping("/api/admin/students")
public class StudentManagementController {

    private final StudentManagementService studentManagementService;

    public StudentManagementController(StudentManagementService studentManagementService) {
        this.studentManagementService = studentManagementService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentManagementDTO> createStudent(
            @Valid @RequestBody StudentManagementRequestDTO request) {
        StudentManagementDTO created = studentManagementService.createStudent(request);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<StudentManagementDTO>> getAllStudents() {
        return ResponseEntity.ok(studentManagementService.getAllStudents());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentManagementDTO> getStudentById(@PathVariable Long id) {
        return ResponseEntity.ok(studentManagementService.getStudentById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentManagementDTO> updateStudent(
            @PathVariable Long id, @Valid @RequestBody StudentManagementRequestDTO request) {
        return ResponseEntity.ok(studentManagementService.updateStudent(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentManagementDTO> updateStudentStatus(
            @PathVariable Long id, @Valid @RequestBody StudentStatusDTO statusDTO) {
        return ResponseEntity.ok(studentManagementService.setStudentStatus(id, statusDTO.getStatus()));
    }

    @PostMapping("/{id}/login")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentManagementDTO> provisionLogin(
            @PathVariable Long id, @Valid @RequestBody StudentLoginRequestDTO requestDTO) {
        return ResponseEntity.status(201)
                .body(studentManagementService.provisionLogin(id, requestDTO));
    }

    @PatchMapping("/{id}/login/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentManagementDTO> setLoginStatus(
            @PathVariable Long id, @Valid @RequestBody StudentStatusDTO requestDTO) {
        return ResponseEntity.ok(studentManagementService.setLoginStatus(id, requestDTO.getStatus()));
    }

    @PutMapping("/{id}/login/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentManagementDTO> setLoginPassword(
            @PathVariable Long id, @Valid @RequestBody StudentPasswordUpdateRequestDTO requestDTO) {
        return ResponseEntity.ok(studentManagementService.setLoginPassword(id, requestDTO.getPassword()));
    }
}