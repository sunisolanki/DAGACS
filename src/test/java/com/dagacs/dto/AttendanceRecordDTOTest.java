package com.dagacs.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AttendanceRecordDTOTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private AttendanceRecordDTO validDTO() {
        return AttendanceRecordDTO.builder()
                .studentId(1L)
                .subjectId(1L)
                .sectionId(1L)
                .status("PRESENT")
                .lecturePeriod("1st")
                .date("2026-09-04")
                .isPresent(true)
                .build();
    }

    @Test
    void validDTO_noViolations() {
        Set<ConstraintViolation<AttendanceRecordDTO>> violations = validator.validate(validDTO());
        assertTrue(violations.isEmpty());
    }

    @Test
    void nullStudentId_violation() {
        AttendanceRecordDTO dto = validDTO();
        dto.setStudentId(null);

        Set<ConstraintViolation<AttendanceRecordDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("studentId")));
    }

    @Test
    void nullSubjectId_violation() {
        AttendanceRecordDTO dto = validDTO();
        dto.setSubjectId(null);

        Set<ConstraintViolation<AttendanceRecordDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("subjectId")));
    }

    @Test
    void nullSectionId_allowed_noViolation() {
        // Phase 2: section may be absent when the record targets a zero-section batch.
        AttendanceRecordDTO dto = validDTO();
        dto.setSectionId(null);

        Set<ConstraintViolation<AttendanceRecordDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());
    }

    @Test
    void nullBatchId_allowed_noViolation() {
        // Phase 2: batch is optional on the record DTO; XOR is enforced at the service boundary.
        AttendanceRecordDTO dto = validDTO();
        dto.setBatchId(null);

        Set<ConstraintViolation<AttendanceRecordDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());
    }

    @Test
    void blankStatus_violation() {
        AttendanceRecordDTO dto = validDTO();
        dto.setStatus(" ");

        Set<ConstraintViolation<AttendanceRecordDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("status")));
    }

    @Test
    void blankDate_violation() {
        AttendanceRecordDTO dto = validDTO();
        dto.setDate(" ");

        Set<ConstraintViolation<AttendanceRecordDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("date")));
    }

    @Test
    void nullIsPresent_violation() {
        AttendanceRecordDTO dto = validDTO();
        dto.setIsPresent(null);

        Set<ConstraintViolation<AttendanceRecordDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("isPresent")));
    }
}
