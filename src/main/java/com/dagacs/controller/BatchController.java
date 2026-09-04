package com.dagacs.controller;

import com.dagacs.dto.BatchDTO;
import com.dagacs.dto.SectionDTO;
import com.dagacs.service.BatchService;
import com.dagacs.service.SectionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/batches")
public class BatchController {

    private final BatchService batchService;
    private final SectionService sectionService;

    public BatchController(BatchService batchService, SectionService sectionService) {
        this.batchService = batchService;
        this.sectionService = sectionService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchDTO> createBatch(@Valid @RequestBody BatchDTO batchDTO) {
        BatchDTO created = batchService.saveBatch(batchDTO);
        return ResponseEntity.status(201).body(created);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<BatchDTO>> getAllBatches() {
        List<BatchDTO> batches = batchService.getAllBatches();
        return ResponseEntity.ok(batches);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchDTO> getBatchById(@PathVariable Long id) {
        BatchDTO batch = batchService.getBatchById(id);
        return ResponseEntity.ok(batch);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BatchDTO> updateBatch(@PathVariable Long id,
                                                @Valid @RequestBody BatchDTO batchDTO) {
        BatchDTO updated = batchService.updateBatch(id, batchDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteBatch(@PathVariable Long id) {
        batchService.deleteBatch(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{batchId}/sections")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SectionDTO>> getSectionsByBatch(@PathVariable Long batchId) {
        List<SectionDTO> sections = sectionService.getSectionsByBatch(batchId);
        return ResponseEntity.ok(sections);
    }
}
