package com.dagacs.controller;

import com.dagacs.dto.HodDesignationRequestDTO;
import com.dagacs.dto.HodIdentityDTO;
import com.dagacs.service.HodDesignationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ADMIN-only HOD designation API (M6.1).
 * <p>
 * Provides the minimal mechanism to designate or clear a teacher as HOD of a
 * department. It does not manage teacher accounts, passwords, or roles, and
 * does not touch the HOD attendance behavior (which stays read-only).
 * </p>
 */
@RestController
@RequestMapping("/api/admin/teachers")
public class HodDesignationController {

    private final HodDesignationService hodDesignationService;

    public HodDesignationController(HodDesignationService hodDesignationService) {
        this.hodDesignationService = hodDesignationService;
    }

    @PatchMapping("/{teacherId}/hod")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HodIdentityDTO> setHod(
            @PathVariable Long teacherId,
            @Valid @RequestBody HodDesignationRequestDTO request) {
        return ResponseEntity.ok(hodDesignationService.setHod(teacherId, request));
    }
}