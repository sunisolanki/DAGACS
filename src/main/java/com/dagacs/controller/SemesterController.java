package com.dagacs.controller;

import com.dagacs.dto.SemesterDTO;
import com.dagacs.service.SemesterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/semesters")
public class SemesterController {

    private final SemesterService semesterService;

    public SemesterController(SemesterService semesterService) {
        this.semesterService = semesterService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SemesterDTO> createSemester(@Valid @RequestBody SemesterDTO semesterDTO) {
        SemesterDTO created = semesterService.saveSemester(semesterDTO);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SemesterDTO>> getAllSemesters() {
        List<SemesterDTO> semesters = semesterService.getAllSemesters();
        return ResponseEntity.ok(semesters);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SemesterDTO> getSemesterById(@PathVariable Long id) {
        SemesterDTO semester = semesterService.getSemesterById(id);
        return ResponseEntity.ok(semester);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SemesterDTO> updateSemester(@PathVariable Long id,
                                                      @Valid @RequestBody SemesterDTO semesterDTO) {
        SemesterDTO updated = semesterService.updateSemester(id, semesterDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSemester(@PathVariable Long id) {
        semesterService.deleteSemester(id);
        return ResponseEntity.noContent().build();
    }
}
