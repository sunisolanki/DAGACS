package com.dagacs.controller;

import com.dagacs.dto.SubjectDTO;
import com.dagacs.service.SubjectService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubjectDTO> createSubject(@Valid @RequestBody SubjectDTO subjectDTO) {
        SubjectDTO created = subjectService.saveSubject(subjectDTO);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SubjectDTO>> getAllSubjects() {
        List<SubjectDTO> subjects = subjectService.getAllSubjects();
        return ResponseEntity.ok(subjects);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubjectDTO> getSubjectById(@PathVariable Long id) {
        SubjectDTO subject = subjectService.getSubjectById(id);
        return ResponseEntity.ok(subject);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SubjectDTO> updateSubject(@PathVariable Long id,
                                                    @Valid @RequestBody SubjectDTO subjectDTO) {
        SubjectDTO updated = subjectService.updateSubject(id, subjectDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteSubject(@PathVariable Long id) {
        subjectService.deleteSubject(id);
        return ResponseEntity.noContent().build();
    }
}
