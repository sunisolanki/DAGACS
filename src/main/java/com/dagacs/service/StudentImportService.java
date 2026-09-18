package com.dagacs.service;

import com.dagacs.dto.StudentImportResult;
import com.dagacs.dto.StudentManagementDTO;
import com.dagacs.dto.StudentManagementRequestDTO;
import com.dagacs.entity.AcademicSession;
import com.dagacs.entity.Batch;
import com.dagacs.entity.Program;
import com.dagacs.entity.Section;
import com.dagacs.exception.AuthException;
import com.dagacs.studentimport.StudentImportParser;
import com.dagacs.studentimport.StudentImportParser.StudentImportRow;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class StudentImportService {

    private final StudentImportParser parser;
    private final StudentManagementService studentManagementService;
    private final Validator validator;

    @Autowired
    public StudentImportService(StudentImportParser parser,
                                     StudentManagementService studentManagementService,
                                     Validator validator) {
        this.parser = parser;
        this.studentManagementService = studentManagementService;
        this.validator = validator;
    }

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
                if (hasErrorFor(errors, rowNumber, field)
                        || isImportResolvedIdField(field)) {
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
        Map<String, String> importTempPasswords = new HashMap<>();
        if (errors.isEmpty()) {
            for (StudentManagementRequestDTO dto : validRows) {
                StudentManagementDTO created = studentManagementService.createStudent(dto);
                importedRows++;
                collectTemporaryPassword(created, importTempPasswords);
            }
        }

        boolean conflict = errors.stream().anyMatch(e -> e.getStatus() == 409);
        String message = errors.isEmpty()
                ? "Imported " + importedRows + " of " + totalRows + " students."
                : "Import failed: " + errors.size() + " row(s) rejected. "
                        + "No students were imported.";

        String credentialDownloadId = null;
        if (errors.isEmpty() && !importTempPasswords.isEmpty()) {
            credentialDownloadId = generateCredentialArtifact(validRows, importTempPasswords);
        }

        return StudentImportResult.builder()
                .totalRows(totalRows)
                .importedRows(importedRows)
                .rejectedRows(errors.size())
                .message(message)
                .errors(errors)
                .credentialDownloadId(credentialDownloadId)
                .build();
    }

    private void collectTemporaryPassword(StudentManagementDTO created,
                                           Map<String, String> tempPasswords) {
        String rollNumber = created.getRollNumber();
        String tempPassword = created.getTemporaryPassword();
        if (rollNumber != null && tempPassword != null) {
            tempPasswords.put(rollNumber, tempPassword);
        }
    }

    private String generateCredentialArtifact(List<StudentManagementRequestDTO> validRows,
                                                Map<String, String> tempPasswords) {
        String downloadId = UUID.randomUUID().toString();
        try {
            byte[] xlsxBytes = buildXlsx(validRows, tempPasswords);
            CredentialArtifact artifact = new CredentialArtifact(downloadId, xlsxBytes);
            CredentialArtifactStore.put(downloadId, artifact);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    CredentialArtifactStore.put(downloadId, artifact);
                }
            });
        } catch (Exception e) {
        }
        return downloadId;
    }

    private byte[] buildXlsx(List<StudentManagementRequestDTO> rows,
                               Map<String, String> tempPasswords) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Credential Downloads");

        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        String[] headers = {"Roll Number", "Email", "Temporary Password"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        for (int i = 0; i < rows.size(); i++) {
            StudentManagementRequestDTO dto = rows.get(i);
            Row row = sheet.createRow(i + 1);
            row.createCell(0).setCellValue(dto.getRollNumber() != null ? dto.getRollNumber() : "");
            row.createCell(1).setCellValue(dto.getEmail() != null ? dto.getEmail() : "");
            String tempPass = tempPasswords.get(dto.getRollNumber());
            if (tempPass == null) {
                tempPass = "";
            }
            row.createCell(2).setCellValue(tempPass);
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        workbook.close();
        return outputStream.toByteArray();
    }

    private StudentManagementRequestDTO buildDto(Map<String, String> row,
                                                        List<StudentImportResult.RowError> errors,
                                                        int rowNumber) {
        String academicSessionName = trim(row.get("academicSession"));
        String programName = trim(row.get("program"));
        String batchName = trim(row.get("batch"));
        String sectionName = trim(row.get("section"));

        String roll = trim(row.get("rollNumber"));
        String email = trim(row.get("email"));
        if (email == null || email.isBlank()) {
            email = roll != null ? roll.toLowerCase(java.util.Locale.ROOT) + "@dagacs.local" : null;
        }

        StudentManagementRequestDTO dto = StudentManagementRequestDTO.builder()
                .rollNumber(roll)
                .email(email)
                .name(trim(row.get("name")))
                .gender(trim(row.get("gender")))
                .fatherName(trim(row.get("fatherName")))
                .motherName(trim(row.get("motherName")))
                .photoUrl(trim(row.get("photoUrl")))
                .enrollmentNumber(trim(row.get("enrollmentNumber")))
                .age(parseOptionalInteger(row.get("age"), errors, rowNumber))
                .admissionDate(trim(row.get("admissionDate")))
                .status(trim(row.get("status")))
                .academicSession(academicSessionName)
                .program(programName)
                .batch(batchName)
                .section(sectionName)
                .academicSessionId(parseOptionalId(row.get("academicSessionId"), errors, rowNumber, "academicSessionId"))
                .programId(parseOptionalId(row.get("programId"), errors, rowNumber, "programId"))
                .batchId(parseOptionalId(row.get("batchId"), errors, rowNumber, "batchId"))
                .sectionId(parseOptionalId(row.get("sectionId"), errors, rowNumber, "sectionId"))
                .build();

        return dto;
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

    private Long parseOptionalId(String raw,
                                 List<StudentImportResult.RowError> errors,
                                 int rowNumber, String field) {
        String value = trim(raw);
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            errors.add(StudentImportResult.RowError.builder()
                    .rowNumber(rowNumber).field(field).status(400)
                    .message(field + " must be a number").build());
            return null;
        }
    }

    private boolean hasErrorFor(List<StudentImportResult.RowError> errors,
                                    int rowNumber, String field) {
        return errors.stream().anyMatch(e ->
                e.getRowNumber() == rowNumber && e.getField().equals(field));
    }

    private boolean isImportResolvedIdField(String field) {
        return "programId".equals(field)
                || "batchId".equals(field)
                || "sectionId".equals(field)
                || "academicSessionId".equals(field);
    }

    private String fieldForMessage(String message) {
        if (message == null) {
            return "row";
        }
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("roll number")) return "rollNumber";
        if (lower.contains("email")) return "email";
        if (lower.contains("program")) return "programId";
        if (lower.contains("academic session")) return "academicSession";
        if (lower.contains("batch")) return "batch";
        if (lower.contains("section")) return "section";
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
