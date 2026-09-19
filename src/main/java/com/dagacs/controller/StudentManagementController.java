package com.dagacs.controller;

import com.dagacs.dto.StudentFilterOptionsResponse;
import com.dagacs.dto.StudentManagementDTO;
import com.dagacs.dto.StudentManagementRequestDTO;
import com.dagacs.dto.StudentPageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<StudentPageResponse> searchStudents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Long academicSessionId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String loginStatus,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        StudentPageResponse response = studentManagementService.searchStudents(
                search, programId, academicSessionId, semesterId,
                batchId, sectionId, status, loginStatus,
                page != null ? page : 0,
                size != null ? size : 20);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/filter-options")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentFilterOptionsResponse> getFilterOptions(
            @RequestParam(required = false) Long academicSessionId,
            @RequestParam(required = false) Long programId,
            @RequestParam(required = false) Long semesterId,
            @RequestParam(required = false) Long batchId,
            @RequestParam(required = false) Long sectionId) {
        return ResponseEntity.ok(studentManagementService.getFilterOptions(
                academicSessionId, programId, semesterId, batchId, sectionId));
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
    public ResponseEntity<StudentManagementDTO> provisionLogin(@PathVariable Long id) {
        return ResponseEntity.status(201)
                .body(studentManagementService.provisionLogin(id));
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