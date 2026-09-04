package com.dagacs.controller;

import com.dagacs.dto.AcademicSessionDTO;
import com.dagacs.dto.BatchDTO;
import com.dagacs.dto.SemesterDTO;
import com.dagacs.service.AcademicSessionService;
import com.dagacs.service.BatchService;
import com.dagacs.service.SemesterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/academic-sessions")
public class AcademicSessionController {

    private final AcademicSessionService academicSessionService;
    private final SemesterService semesterService;
    private final BatchService batchService;

    public AcademicSessionController(AcademicSessionService academicSessionService,
                                     SemesterService semesterService,
                                     BatchService batchService) {
        this.academicSessionService = academicSessionService;
        this.semesterService = semesterService;
        this.batchService = batchService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AcademicSessionDTO> createSession(@Valid @RequestBody AcademicSessionDTO sessionDTO) {
        AcademicSessionDTO created = academicSessionService.saveSession(sessionDTO);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AcademicSessionDTO>> getAllSessions() {
        List<AcademicSessionDTO> sessions = academicSessionService.getAllSessions();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AcademicSessionDTO> getSessionById(@PathVariable Long id) {
        AcademicSessionDTO session = academicSessionService.getSessionById(id);
        return ResponseEntity.ok(session);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AcademicSessionDTO> updateSession(@PathVariable Long id,
                                                            @Valid @RequestBody AcademicSessionDTO sessionDTO) {
        AcademicSessionDTO updated = academicSessionService.updateSession(id, sessionDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSession(@PathVariable Long id) {
        academicSessionService.deleteSession(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{sessionId}/semesters")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SemesterDTO>> getSemestersBySession(@PathVariable Long sessionId) {
        List<SemesterDTO> semesters = semesterService.getSemestersBySession(sessionId);
        return ResponseEntity.ok(semesters);
    }

    @GetMapping("/{sessionId}/batches")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BatchDTO>> getBatchesBySession(@PathVariable Long sessionId) {
        List<BatchDTO> batches = batchService.getBatchesBySession(sessionId);
        return ResponseEntity.ok(batches);
    }
}
