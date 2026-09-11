package com.dagacs.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/student")
public class StudentController {

    private final boolean diagnosticsEnabled;

    public StudentController(@Value("${app.diagnostics.enabled:true}") boolean diagnosticsEnabled) {
        this.diagnosticsEnabled = diagnosticsEnabled;
    }

    @GetMapping("/test")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, Object>> test() {
        if (!diagnosticsEnabled) {
            return ResponseEntity.notFound().build();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String role = "anonymous";
        if (auth != null && auth.getAuthorities() != null) {
            role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        }
        return ResponseEntity.ok(Map.of(
                "message", "Student access confirmed",
                "user", auth.getPrincipal(),
                "role", role
        ));
    }
}