package com.dagacs.controller;

import com.dagacs.dto.TeacherCreateRequestDTO;
import com.dagacs.dto.TeacherLoginRequestDTO;
import com.dagacs.dto.TeacherLoginStatusDTO;
import com.dagacs.dto.TeacherManagementDTO;
import com.dagacs.dto.TeacherPasswordUpdateRequestDTO;
import com.dagacs.dto.TeacherUpdateRequestDTO;
import com.dagacs.service.TeacherManagementService;
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
 * Admin teacher-management surface (M9.5.1).
 * <p>
 * Uses the distinct base {@code /api/admin/teacher-management/teachers} to avoid
 * overlapping the frozen {@code GET /api/admin/teachers} read-only projection of
 * {@code AdminTeacherAssignmentController}. All operations are ADMIN-only
 * (enforced both by the {@code /api/admin/**} rule and {@code @PreAuthorize}).
 * </p>
 */
@RestController
@RequestMapping("/api/admin/teacher-management/teachers")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTeacherManagementController {

    private final TeacherManagementService teacherManagementService;

    public AdminTeacherManagementController(TeacherManagementService teacherManagementService) {
        this.teacherManagementService = teacherManagementService;
    }

    @GetMapping
    public ResponseEntity<List<TeacherManagementDTO>> listTeachers() {
        return ResponseEntity.ok(teacherManagementService.listTeachers());
    }

    @PostMapping
    public ResponseEntity<TeacherManagementDTO> createTeacher(
            @Valid @RequestBody TeacherCreateRequestDTO requestDTO) {
        return ResponseEntity.status(201).body(teacherManagementService.createTeacher(requestDTO));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TeacherManagementDTO> getTeacher(@PathVariable Long id) {
        return ResponseEntity.ok(teacherManagementService.getTeacher(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TeacherManagementDTO> updateTeacher(
            @PathVariable Long id,
            @Valid @RequestBody TeacherUpdateRequestDTO requestDTO) {
        return ResponseEntity.ok(teacherManagementService.updateTeacher(id, requestDTO));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<TeacherManagementDTO> setTeacherStatus(
            @PathVariable Long id,
            @Valid @RequestBody TeacherLoginStatusDTO requestDTO) {
        return ResponseEntity.ok(teacherManagementService.setTeacherStatus(id, requestDTO.getStatus()));
    }

    @PostMapping("/{id}/login")
    public ResponseEntity<TeacherManagementDTO> provisionLogin(
            @PathVariable Long id,
            @Valid @RequestBody TeacherLoginRequestDTO requestDTO) {
        return ResponseEntity.status(201)
                .body(teacherManagementService.provisionLogin(id, requestDTO));
    }

    @PatchMapping("/{id}/login/status")
    public ResponseEntity<TeacherManagementDTO> setLoginStatus(
            @PathVariable Long id,
            @Valid @RequestBody TeacherLoginStatusDTO requestDTO) {
        return ResponseEntity.ok(teacherManagementService.setLoginStatus(id, requestDTO.getStatus()));
    }

    @PutMapping("/{id}/login/password")
    public ResponseEntity<TeacherManagementDTO> setLoginPassword(
            @PathVariable Long id,
            @Valid @RequestBody TeacherPasswordUpdateRequestDTO requestDTO) {
        return ResponseEntity.ok(teacherManagementService.setLoginPassword(id, requestDTO.getPassword()));
    }
}