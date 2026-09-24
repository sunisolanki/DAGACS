package com.dagacs.controller;

import com.dagacs.dto.StudentProfileDTO;
import com.dagacs.dto.StudentProfileRequestDTO;
import com.dagacs.service.StudentProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Student profile API (M5.1). The authenticated student is resolved
 * from the JWT security context; no studentId is accepted from the client, so a
 * student can never read or update another student's profile.
 */
@RestController
@RequestMapping("/api/student/profile")
@PreAuthorize("hasRole('STUDENT')")
public class StudentProfileController {

    private final StudentProfileService studentProfileService;

    public StudentProfileController(StudentProfileService studentProfileService) {
        this.studentProfileService = studentProfileService;
    }

    @GetMapping
    public ResponseEntity<StudentProfileDTO> getMyProfile() {
        return ResponseEntity.ok(studentProfileService.getMyProfile());
    }

    @PutMapping
    public ResponseEntity<StudentProfileDTO> updateMyProfile(
            @RequestBody @Valid StudentProfileRequestDTO request) {
        return ResponseEntity.ok(studentProfileService.updateMyProfile(request));
    }
}