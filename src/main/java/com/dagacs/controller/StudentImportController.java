package com.dagacs.controller;

import com.dagacs.dto.StudentImportResult;
import com.dagacs.service.StudentImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * M9.10 Admin bulk student import endpoint (ADMIN-only).
 * <p>
 * Sits under {@code /api/admin/**} so the existing security rule in
 * {@code SecurityConfig} applies, plus an explicit per-method
 * {@code @PreAuthorize("hasRole('ADMIN')")} mirroring the sibling admin
 * controllers. The JWT/role model is untouched.
 * </p>
 * <p>
 * Response semantics follow existing DAGACS HTTP conventions: 200 on a fully
 * imported file, 400 for validation/file errors, 409 when any rejected row is a
 * duplicate/conflict, 401 unauthenticated, 403 non-ADMIN. The body is the
 * SOW-mandated import summary; when rows are rejected {@code importedRows} is
 * 0 (all-or-nothing).
 * </p>
 */
@RestController
@RequestMapping("/api/admin/students/import")
public class StudentImportController {

    private final StudentImportService studentImportService;

    public StudentImportController(StudentImportService studentImportService) {
        this.studentImportService = studentImportService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentImportResult> importStudents(
            @RequestPart("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        StudentImportResult result = studentImportService.importStudents(
                filename == null || filename.isBlank() ? "upload.xlsx" : filename,
                file.getBytes());

        HttpStatus status = HttpStatus.OK;
        if (result.getRejectedRows() > 0) {
            boolean conflict = result.getErrors().stream()
                    .anyMatch(error -> error.getStatus() == 409);
            status = conflict ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(result);
    }
}