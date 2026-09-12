package com.dagacs.service;

import com.dagacs.dto.StudentImportResult;
import com.dagacs.dto.StudentManagementRequestDTO;
import com.dagacs.exception.AuthException;
import com.dagacs.studentimport.StudentImportParser;
import com.dagacs.studentimport.StudentImportParser.StudentImportRow;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * M9.10 Student bulk Excel/CSV import (all-or-nothing).
 * <p>
 * Implements the locked M9.10 contract:
 * <ul>
 *   <li>PROFILE records only - no {@code User} rows, no passwords, no
 *       {@link AccountProvisioningService} involvement.</li>
 *   <li>Every row is mapped to the same {@link StudentManagementRequestDTO} and
 *       validated through the exact same path as single-student creation
 *       ({@link StudentManagementService#assertValidForCreate}), so the M9.9
 *       Program &rarr; Batch &rarr; Section consistency guards are inherited, never
 *       duplicated or bypassed.</li>
 *   <li>All-or-nothing: a file-level problem or any rejected row means nothing is
 *       persisted; every rejected row is reported with its physical file row
 *       number, field and message.</li>
 *   <li>Duplicate detection by roll number (and by email, case-insensitively,
 *       matching the login-linkage semantics) both inside the file and against
 *       the database.</li>
 * </ul>
 */
@Service
public class StudentImportService {

    private final StudentImportParser parser;
    private final StudentManagementService studentManagementService;
    private final Validator validator;

    public StudentImportService(StudentImportParser parser,
                                StudentManagementService studentManagementService,
                                Validator validator) {
        this.parser = parser;
        this.studentManagementService = studentManagementService;
        this.validator = validator;
    }

    /**
     * Parses, validates and (only when every row is valid) persists the uploaded
     * students within a single transaction. Returns a summary with the exact
     * SOW shape: {@code totalRows}/{@code importedRows}/{@code rejectedRows} and
     * row-wise errors ({@code rowNumber}, {@code field}, {@code message}).
     * On any failure {@code importedRows} is {@code 0} - nothing is partially
     * applied.
     */
    @Transactional
    public StudentImportResult importStudents(String filename, byte[] bytes) {
        List<StudentImportRow> rows = parser.parse(filename, bytes);
        if (rows.isEmpty()) {
            throw new AuthException(
                    "The file has no student rows (header-only or all rows blank)", 400);
        }

        List<StudentImportResult.RowError> errors = new ArrayList<>();
        List<StudentManagementRequestDTO> validRows = new ArrayList<>();
        Set<String> rollsInFile = new HashSet<>();
        Set<String> emailsInFile = new HashSet<>();

        for (StudentImportRow entry : rows) {
            int rowNumber = entry.rowNumber();
            Map<String, String> row = entry.values();
            int errorsBefore = errors.size();

            StudentManagementRequestDTO dto = buildDto(row, errors, rowNumber);

            for (ConstraintViolation<StudentManagementRequestDTO> violation
                    : validator.validate(dto)) {
                String field = violation.getPropertyPath().toString();
                if (hasErrorFor(errors, rowNumber, field)) {
                    continue;
                }
                errors.add(StudentImportResult.RowError.builder()
                        .rowNumber(rowNumber).field(field)
                        .message(violation.getMessage()).status(400).build());
            }

            String roll = trim(row.get("rollNumber"));
            String email = normalizeEmail(row.get("email"));
            if (roll != null && !rollsInFile.add(roll)) {
                errors.add(StudentImportResult.RowError.builder()
                        .rowNumber(rowNumber).field("rollNumber").status(409)
                        .message("Roll number already exists in this file: " + roll)
                        .build());
            }
            if (email != null && !emailsInFile.add(email)) {
                errors.add(StudentImportResult.RowError.builder()
                        .rowNumber(rowNumber).field("email").status(409)
                        .message("Email already exists in this file: " + email)
                        .build());
            }

            if (errors.size() > errorsBefore) {
                continue;
            }
            try {
                studentManagementService.assertValidForCreate(dto);
                validRows.add(dto);
            } catch (AuthException e) {
                int status = e.getStatus() == 409 ? 409 : 400;
                errors.add(StudentImportResult.RowError.builder()
                        .rowNumber(rowNumber).field(fieldForMessage(e.getMessage()))
                        .message(e.getMessage()).status(status).build());
            }
        }

        int totalRows = rows.size();
        int importedRows = 0;
        if (errors.isEmpty()) {
            for (StudentManagementRequestDTO dto : validRows) {
                studentManagementService.createStudent(dto);
                importedRows++;
            }
        }

        boolean conflict = errors.stream().anyMatch(e -> e.getStatus() == 409);
        String message = errors.isEmpty()
                ? "Imported " + importedRows + " of " + totalRows + " students."
                : "Import failed: " + errors.size() + " row(s) rejected. "
                        + "No students were imported.";
        return StudentImportResult.builder()
                .totalRows(totalRows)
                .importedRows(importedRows)
                .rejectedRows(errors.size())
                .message(message)
                .errors(errors)
                .build();
    }

    /**
     * Maps a raw row into the frozen create DTO. Only conversion-level problems
     * (unparseable numeric IDs / age) are recorded here; required-field and
     * format checks fall through to the DTO's Bean Validation (the same
     * annotations the single-create path trusts).
     */
    private StudentManagementRequestDTO buildDto(Map<String, String> row,
                                                  List<StudentImportResult.RowError> errors,
                                                  int rowNumber) {
        Long programId = parseLongId(row.get("programId"), "Program", "programId", errors, rowNumber);
        Long batchId = parseLongId(row.get("batchId"), "Batch", "batchId", errors, rowNumber);
        Long sectionId = parseLongId(row.get("sectionId"), "Section", "sectionId", errors, rowNumber);
        Integer age = parseOptionalInteger(row.get("age"), errors, rowNumber);
        return StudentManagementRequestDTO.builder()
                .rollNumber(trim(row.get("rollNumber")))
                .email(trim(row.get("email")))
                .name(trim(row.get("name")))
                .gender(trim(row.get("gender")))
                .fatherName(trim(row.get("fatherName")))
                .motherName(trim(row.get("motherName")))
                .photoUrl(trim(row.get("photoUrl")))
                .enrollmentNumber(trim(row.get("enrollmentNumber")))
                .age(age)
                .admissionDate(trim(row.get("admissionDate")))
                .status(trim(row.get("status")))
                .programId(programId)
                .batchId(batchId)
                .sectionId(sectionId)
                .build();
    }

    private Long parseLongId(String raw, String label, String field,
                             List<StudentImportResult.RowError> errors, int rowNumber) {
        String value = trim(raw);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            errors.add(StudentImportResult.RowError.builder()
                    .rowNumber(rowNumber).field(field).status(400)
                    .message(label + " ID must be a number").build());
            return null;
        }
    }

    private Integer parseOptionalInteger(String raw,
                                         List<StudentImportResult.RowError> errors,
                                         int rowNumber) {
        String value = trim(raw);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            errors.add(StudentImportResult.RowError.builder()
                    .rowNumber(rowNumber).field("age").status(400)
                    .message("Age must be a number").build());
            return null;
        }
    }

    private boolean hasErrorFor(List<StudentImportResult.RowError> errors,
                                int rowNumber, String field) {
        return errors.stream().anyMatch(e ->
                e.getRowNumber() == rowNumber && e.getField().equals(field));
    }

    /**
     * Best-effort field attribution for errors raised by the shared validation
     * path (which reports a message rather than a field). Falls back to the
     * generic {@code row} identifier.
     */
    private String fieldForMessage(String message) {
        if (message == null) {
            return "row";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("roll number")) return "rollNumber";
        if (lower.contains("email")) return "email";
        if (lower.contains("program")) return "programId";
        if (lower.contains("batch")) return "batchId";
        if (lower.contains("section")) return "sectionId";
        if (lower.contains("status")) return "status";
        return "row";
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String normalizeEmail(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }
}