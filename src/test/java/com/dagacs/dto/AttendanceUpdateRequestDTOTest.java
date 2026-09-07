package com.dagacs.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AttendanceUpdateRequestDTOTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validDTO_noViolations() {
        AttendanceUpdateRequestDTO dto = AttendanceUpdateRequestDTO.builder()
                .newStatus("PRESENT").reason("Doctor certificate").build();
        Set<ConstraintViolation<AttendanceUpdateRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());
    }

    @Test
    void blankNewStatus_violation() {
        AttendanceUpdateRequestDTO dto = AttendanceUpdateRequestDTO.builder().newStatus(" ").build();
        Set<ConstraintViolation<AttendanceUpdateRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("newStatus")));
    }

    @Test
    void invalidNewStatus_violation() {
        AttendanceUpdateRequestDTO dto = AttendanceUpdateRequestDTO.builder().newStatus("LATE").build();
        Set<ConstraintViolation<AttendanceUpdateRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("newStatus")));
    }

    @Test
    void reasonTooLong_violation() {
        AttendanceUpdateRequestDTO dto = AttendanceUpdateRequestDTO.builder()
                .newStatus("PRESENT")
                .reason("x".repeat(501))
                .build();
        Set<ConstraintViolation<AttendanceUpdateRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("reason")));
    }
}
