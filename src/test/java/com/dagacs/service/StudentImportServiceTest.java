package com.dagacs.service;

import com.dagacs.dto.StudentImportResult;
import com.dagacs.dto.StudentManagementDTO;
import com.dagacs.dto.StudentManagementRequestDTO;
import com.dagacs.exception.AuthException;
import com.dagacs.service.CredentialArtifact;
import com.dagacs.service.CredentialArtifactStore;
import com.dagacs.studentimport.StudentImportParser;
import com.dagacs.studentimport.StudentImportParser.StudentImportRow;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentImportServiceTest {

    @Mock
    private StudentImportParser parser;

    @Mock
    private StudentManagementService studentManagementService;

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    private StudentImportService service;

    @BeforeEach
    void setUp() {
        service = new StudentImportService(parser, studentManagementService, validator);
    }

    private Map<String, String> row(String roll, String email, String name) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("rollNumber", roll);
        map.put("email", email);
        map.put("name", name);
        map.put("gender", "");
        map.put("fatherName", "");
        map.put("motherName", "");
        map.put("photoUrl", "");
        map.put("enrollmentNumber", "");
        map.put("age", "");
        map.put("admissionDate", "");
        map.put("status", "");
        map.put("academicSession", "2026-27");
        map.put("program", "Computer Science");
        map.put("batch", "B1");
        map.put("section", "A");
        map.put("academicSessionId", "1");
        map.put("programId", "1");
        map.put("batchId", "1");
        map.put("sectionId", "1");
        return map;
    }

    @Test
    void allValidRows_areImportedWithFullSummary() throws Exception {
        when(parser.parse("students.xlsx", new byte[]{1}))
                .thenReturn(List.of(new StudentImportRow(2, row("R1", "a@dagacs.local", "Alice")),
                        new StudentImportRow(3, row("R2", "b@dagacs.local", "Bob"))));
        when(studentManagementService.createStudent(any())).thenAnswer(inv -> {
            StudentManagementRequestDTO dto = inv.getArgument(0);
            return StudentManagementDTO.builder()
                    .rollNumber(dto.getRollNumber())
                    .email(dto.getEmail())
                    .name(dto.getName())
                    .temporaryPassword("TempPass_" + dto.getRollNumber())
                    .build();
        });

        StudentImportResult result = service.importStudents("students.xlsx", new byte[]{1});

        assertEquals(2, result.getTotalRows());
        assertEquals(2, result.getImportedRows());
        assertEquals(0, result.getRejectedRows());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getCredentialDownloadId() != null
                && !result.getCredentialDownloadId().isEmpty());
        verify(studentManagementService, times(2)).createStudent(any());
        verify(studentManagementService, times(2)).assertValidForCreate(any());

        Map<String, String> artifactPasswords = parsePasswordColumn(
                result.getCredentialDownloadId());
        assertEquals("TempPass_R1", artifactPasswords.get("R1"));
        assertEquals("TempPass_R2", artifactPasswords.get("R2"));
    }

    private Map<String, String> parsePasswordColumn(String downloadId) throws Exception {
        CredentialArtifact artifact = CredentialArtifactStore.get(downloadId);
        assertTrue(artifact != null, "artifact should be in the store");
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(artifact.getXlsxBytes()))) {
            Map<String, String> passwords = new LinkedHashMap<>();
            for (Row row : workbook.getSheetAt(0)) {
                if (row.getRowNum() == 0) {
                    continue;
                }
                Cell roll = row.getCell(0);
                Cell password = row.getCell(2);
                if (roll != null && password != null) {
                    passwords.put(roll.getStringCellValue(), password.getStringCellValue());
                }
            }
            return passwords;
        }
    }

    @Test
    void inFileDuplicateRollNumber_rejectsWholeFile_nothingImported() {
        when(parser.parse("students.csv", new byte[]{1}))
                .thenReturn(List.of(new StudentImportRow(2, row("R1", "a@dagacs.local", "Alice")),
                        new StudentImportRow(3, row("R1", "b@dagacs.local", "Bob"))));

        StudentImportResult result = service.importStudents("students.csv", new byte[]{1});

        assertEquals(2, result.getTotalRows());
        assertEquals(0, result.getImportedRows());
        assertEquals(1, result.getRejectedRows());
        assertEquals(409, result.getErrors().get(0).getStatus());
        assertEquals("rollNumber", result.getErrors().get(0).getField());
        verify(studentManagementService, never()).createStudent(any());
    }

    @Test
    void inFileDuplicateEmail_caseInsensitive_rejectsRow() {
        when(parser.parse("students.csv", new byte[]{1}))
                .thenReturn(List.of(new StudentImportRow(2, row("R1", "a@dagacs.local", "Alice")),
                        new StudentImportRow(3, row("R2", "A@DAGACS.LOCAL", "Bob"))));

        StudentImportResult result = service.importStudents("students.csv", new byte[]{1});

        assertEquals(1, result.getRejectedRows());
        assertEquals(0, result.getImportedRows());
        assertEquals(409, result.getErrors().get(0).getStatus());
        assertEquals("email", result.getErrors().get(0).getField());
        verify(studentManagementService, never()).createStudent(any());
    }

    @Test
    void missingRequiredField_rejectsRow_andNeverPersistsAny() {
        Map<String, String> bad = new LinkedHashMap<>(row("R1", "", ""));
        bad.put("email", null);
        bad.put("name", "  ");
        when(parser.parse("students.csv", new byte[]{1}))
                .thenReturn(List.of(new StudentImportRow(2, bad),
                        new StudentImportRow(3, row("R2", "b@dagacs.local", "Bob"))));

        StudentImportResult result = service.importStudents("students.csv", new byte[]{1});

        assertEquals(2, result.getTotalRows());
        assertEquals(0, result.getImportedRows());
        assertEquals(1, result.getRejectedRows());
        assertTrue(result.getErrors().get(0).getMessage().contains("Name is required"));
        verify(studentManagementService, never()).createStudent(any());
        verify(studentManagementService, times(1)).assertValidForCreate(any());
    }

    @Test
    void nonNumericAcademicId_addsConversionError() {
        Map<String, String> bad = row("R1", "a@dagacs.local", "Alice");
        bad.put("programId", "not-a-number");
        when(parser.parse("students.csv", new byte[]{1}))
                .thenReturn(List.of(new StudentImportRow(2, bad)));

        StudentImportResult result = service.importStudents("students.csv", new byte[]{1});

        assertEquals(1, result.getRejectedRows());
        assertEquals(0, result.getImportedRows());
        assertEquals(400, result.getErrors().get(0).getStatus());
        assertEquals("programId", result.getErrors().get(0).getField());
        assertTrue(result.getErrors().get(0).getMessage().contains("must be a number"));
    }

    @Test
    void dbConflictFromValidationPath_recordsRowError409() {
        when(parser.parse("students.csv", new byte[]{1}))
                .thenReturn(List.of(new StudentImportRow(2, row("R1", "a@dagacs.local", "Alice"))));
        doThrow(new AuthException("Roll number already exists: R1", 409))
                .when(studentManagementService).assertValidForCreate(any());

        StudentImportResult result = service.importStudents("students.csv", new byte[]{1});

        assertEquals(1, result.getRejectedRows());
        assertEquals(0, result.getImportedRows());
        assertEquals(409, result.getErrors().get(0).getStatus());
        verify(studentManagementService, never()).createStudent(any());
    }

    @Test
    void academicMismatchFromValidationPath_recordsRowError400() {
        when(parser.parse("students.csv", new byte[]{1}))
                .thenReturn(List.of(new StudentImportRow(2, row("R1", "a@dagacs.local", "Alice"))));
        doThrow(new AuthException(
                "Student program does not match batch academic session program", 400))
                .when(studentManagementService).assertValidForCreate(any());

        StudentImportResult result = service.importStudents("students.csv", new byte[]{1});

        assertEquals(1, result.getRejectedRows());
        assertEquals(0, result.getImportedRows());
        assertEquals(400, result.getErrors().get(0).getStatus());
        assertEquals("programId", result.getErrors().get(0).getField());
    }

    @Test
    void invalidStatusFromValidationPath_recordsRowError() {
        when(parser.parse("students.csv", new byte[]{1}))
                .thenReturn(List.of(new StudentImportRow(2, row("R1", "a@dagacs.local", "Alice"))));
        doThrow(new AuthException("Status must be ACTIVE or INACTIVE", 400))
                .when(studentManagementService).assertValidForCreate(any());

        StudentImportResult result = service.importStudents("students.csv", new byte[]{1});

        assertEquals(400, result.getErrors().get(0).getStatus());
        assertEquals("status", result.getErrors().get(0).getField());
    }

    @Test
    void headerOnlyFile_throws400() {
        when(parser.parse("students.csv", new byte[]{1})).thenReturn(List.of());

        AuthException ex = assertThrows(AuthException.class,
                () -> service.importStudents("students.csv", new byte[]{1}));

        assertEquals(400, ex.getStatus());
        assertTrue(ex.getMessage().contains("no student rows"));
    }
}