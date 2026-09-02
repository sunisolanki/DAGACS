package com.dagacs.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/teacher")
public class TeacherController {

    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> test() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String role = "anonymous";
        if (auth != null && auth.getAuthorities() != null) {
            role = auth.getAuthorities().iterator().next().getAuthority().replace("ROLE_", "");
        }
        return ResponseEntity.ok(Map.of(
                "message", "Teacher access confirmed",
                "user", auth.getPrincipal(),
                "role", role
        ));
    }
}