package com.dagacs.controller;

import com.dagacs.dto.SubjectOfferingDTO;
import com.dagacs.dto.SubjectOfferingRequestDTO;
import com.dagacs.service.SubjectOfferingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/subject-offerings")
public class SubjectOfferingController {

    private final SubjectOfferingService subjectOfferingService;

    public SubjectOfferingController(SubjectOfferingService subjectOfferingService) {
        this.subjectOfferingService = subjectOfferingService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubjectOfferingDTO> createSubjectOffering(
            @Valid @RequestBody SubjectOfferingRequestDTO requestDTO) {
        SubjectOfferingDTO created = subjectOfferingService.saveSubjectOffering(requestDTO);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SubjectOfferingDTO>> getAllSubjectOfferings() {
        List<SubjectOfferingDTO> offerings = subjectOfferingService.getAllSubjectOfferings();
        return ResponseEntity.ok(offerings);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubjectOfferingDTO> getSubjectOfferingById(@PathVariable Long id) {
        SubjectOfferingDTO offering = subjectOfferingService.getSubjectOfferingById(id);
        return ResponseEntity.ok(offering);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubjectOfferingDTO> updateSubjectOffering(
            @PathVariable Long id,
            @Valid @RequestBody SubjectOfferingRequestDTO requestDTO) {
        SubjectOfferingDTO updated = subjectOfferingService.updateSubjectOffering(id, requestDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSubjectOffering(@PathVariable Long id) {
        subjectOfferingService.deleteSubjectOffering(id);
        return ResponseEntity.noContent().build();
    }
}