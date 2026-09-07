package com.dagacs.controller;

import com.dagacs.dto.HodIdentityDTO;
import com.dagacs.service.HodIdentityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HOD identity/scope endpoint (M6.1).
 * <p>
 * Returns only the minimum identity and department-scope information needed by
 * the authenticated HOD. The identity is resolved from the JWT; no client-supplied
 * identifier can alter the department scope. Analytics, dashboards, and lists are
 * intentionally out of scope for M6.1.
 * </p>
 */
@RestController
@RequestMapping("/api/hod")
@PreAuthorize("hasRole('HOD')")
public class HodIdentityController {

    private final HodIdentityService hodIdentityService;

    public HodIdentityController(HodIdentityService hodIdentityService) {
        this.hodIdentityService = hodIdentityService;
    }

    @GetMapping("/me")
    public ResponseEntity<HodIdentityDTO> getMyIdentity() {
        return ResponseEntity.ok(hodIdentityService.getMyIdentity());
    }
}