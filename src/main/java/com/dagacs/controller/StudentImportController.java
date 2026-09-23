package com.dagacs.controller;

import com.dagacs.dto.StudentImportResult;
import com.dagacs.service.CredentialArtifact;
import com.dagacs.service.CredentialArtifactStore;
import com.dagacs.service.StudentImportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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
            @RequestPart("file") MultipartFile file,
            @RequestParam Long academicSessionId,
            @RequestParam Long programId,
            @RequestParam Long batchId,
            @RequestParam Long sectionId,
            @RequestParam Long semesterId) throws IOException {
        String filename = file.getOriginalFilename();
        StudentImportResult result = studentImportService.importStudents(
                filename == null || filename.isBlank() ? "upload.xlsx" : filename,
                file.getBytes(),
                academicSessionId, programId, batchId, sectionId, semesterId);

        HttpStatus status = HttpStatus.OK;
        if (result.getRejectedRows() > 0) {
            boolean conflict = result.getErrors().stream()
                    .anyMatch(error -> error.getStatus() == 409);
            status = conflict ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        }
        return ResponseEntity.status(status).body(result);
    }

    @PostMapping(path = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StudentImportResult> previewImport(
            @RequestPart("file") MultipartFile file,
            @RequestParam Long academicSessionId,
            @RequestParam Long programId,
            @RequestParam Long batchId,
            @RequestParam Long sectionId,
            @RequestParam Long semesterId) throws IOException {
        String filename = file.getOriginalFilename();
        StudentImportResult result = studentImportService.previewImport(
                filename == null || filename.isBlank() ? "upload.xlsx" : filename,
                file.getBytes(),
                academicSessionId, programId, batchId, sectionId, semesterId);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/credentials/{downloadId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<byte[]> downloadCredentials(@PathVariable String downloadId) {
        CredentialArtifact artifact = CredentialArtifactStore.get(downloadId);
        if (artifact == null) {
            return ResponseEntity.notFound().build();
        }
        if (!artifact.tryMarkDownloaded()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header(HttpHeaders.RETRY_AFTER, "1")
                    .build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(
                MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        headers.setContentDispositionFormData("attachment", "student_credentials.xlsx");
        return ResponseEntity.ok()
                .headers(headers)
                .body(artifact.getXlsxBytes());
    }
}
