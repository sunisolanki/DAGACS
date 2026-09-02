package com.dagacs.controller;

import com.dagacs.dto.ProgramDTO;
import com.dagacs.service.ProgramService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/programs")
public class ProgramController {

    private final ProgramService programService;

    public ProgramController(ProgramService programService) {
        this.programService = programService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProgramDTO> createProgram(@Valid @RequestBody ProgramDTO programDTO) {
        ProgramDTO created = programService.saveProgram(programDTO);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProgramDTO>> getAllPrograms() {
        List<ProgramDTO> programs = programService.getAllPrograms();
        return ResponseEntity.ok(programs);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProgramDTO> getProgramById(@PathVariable Long id) {
        ProgramDTO program = programService.getProgramById(id);
        return ResponseEntity.ok(program);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProgramDTO> updateProgram(@PathVariable Long id,
                                                    @Valid @RequestBody ProgramDTO programDTO) {
        ProgramDTO updated = programService.updateProgram(id, programDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProgram(@PathVariable Long id) {
        programService.deleteProgram(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/departments/{departmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ProgramDTO>> getProgramsByDepartment(@PathVariable Long departmentId) {
        List<ProgramDTO> programs = programService.getProgramsByDepartment(departmentId);
        return ResponseEntity.ok(programs);
    }
}