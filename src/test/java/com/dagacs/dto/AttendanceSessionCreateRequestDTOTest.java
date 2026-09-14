package com.dagacs.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AttendanceSessionCreateRequestDTOTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private AttendanceSessionCreateRequestDTO validDTO() {
        return AttendanceSessionCreateRequestDTO.builder()
                .subjectId(1L).sectionId(1L).lecturePeriod("1st").date("2026-09-04").build();
    }

    @Test
    void validDTO_noViolations() {
        Set<ConstraintViolation<AttendanceSessionCreateRequestDTO>> violations = validator.validate(validDTO());
        assertTrue(violations.isEmpty());
    }

    @Test
    void nullSubjectId_violation() {
        AttendanceSessionCreateRequestDTO dto = validDTO();
        dto.setSubjectId(null);

        Set<ConstraintViolation<AttendanceSessionCreateRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("subjectId")));
    }

    @Test
    void nullSectionId_allowed_noViolation() {
        // Phase 2: section may be absent when the session targets a zero-section batch.
        AttendanceSessionCreateRequestDTO dto = validDTO();
        dto.setSectionId(null);

        Set<ConstraintViolation<AttendanceSessionCreateRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());
    }

    @Test
    void nullBatchId_allowed_noViolation() {
        // Phase 2: batch is optional on the request DTO; XOR is enforced at the service boundary.
        AttendanceSessionCreateRequestDTO dto = validDTO();
        dto.setBatchId(null);

        Set<ConstraintViolation<AttendanceSessionCreateRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());
    }

    @Test
    void blankLecturePeriod_violation() {
        AttendanceSessionCreateRequestDTO dto = validDTO();
        dto.setLecturePeriod(" ");

        Set<ConstraintViolation<AttendanceSessionCreateRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("lecturePeriod")));
    }

    @Test
    void blankDate_violation() {
        AttendanceSessionCreateRequestDTO dto = validDTO();
        dto.setDate(" ");

        Set<ConstraintViolation<AttendanceSessionCreateRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("date")));
    }
}
