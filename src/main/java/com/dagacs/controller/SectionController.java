package com.dagacs.controller;

import com.dagacs.dto.SectionDTO;
import com.dagacs.service.SectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sections")
public class SectionController {

    private final SectionService sectionService;

    public SectionController(SectionService sectionService) {
        this.sectionService = sectionService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SectionDTO> createSection(@Valid @RequestBody SectionDTO sectionDTO) {
        SectionDTO created = sectionService.saveSection(sectionDTO);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SectionDTO>> getAllSections() {
        List<SectionDTO> sections = sectionService.getAllSections();
        return ResponseEntity.ok(sections);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SectionDTO> getSectionById(@PathVariable Long id) {
        SectionDTO section = sectionService.getSectionById(id);
        return ResponseEntity.ok(section);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SectionDTO> updateSection(@PathVariable Long id,
                                                    @Valid @RequestBody SectionDTO sectionDTO) {
        SectionDTO updated = sectionService.updateSection(id, sectionDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSection(@PathVariable Long id) {
        sectionService.deleteSection(id);
        return ResponseEntity.noContent().build();
    }
}
