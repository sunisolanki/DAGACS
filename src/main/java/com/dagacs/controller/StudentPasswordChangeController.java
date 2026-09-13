package com.dagacs.controller;

import com.dagacs.dto.StudentChangePasswordRequestDTO;
import com.dagacs.security.JwtTokenProvider;
import com.dagacs.service.StudentPasswordChangeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentPasswordChangeController {

    private final StudentPasswordChangeService studentPasswordChangeService;
    private final JwtTokenProvider jwtTokenProvider;

    public StudentPasswordChangeController(StudentPasswordChangeService studentPasswordChangeService,
                                           JwtTokenProvider jwtTokenProvider) {
        this.studentPasswordChangeService = studentPasswordChangeService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PutMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody StudentChangePasswordRequestDTO request,
            HttpServletRequest httpRequest) {
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).build();
        }
        String token = authHeader.substring(7);
        String email = jwtTokenProvider.getSubject(token);
        studentPasswordChangeService.changePassword(email, request.getCurrentPassword(),
                request.getNewPassword(), request.getConfirmPassword());
        return ResponseEntity.ok().build();
    }
}
