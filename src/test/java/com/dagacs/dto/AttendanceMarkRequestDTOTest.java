package com.dagacs.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AttendanceMarkRequestDTOTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private AttendanceMarkItemDTO presentItem() {
        return AttendanceMarkItemDTO.builder().studentId(1L).status("PRESENT").build();
    }

    @Test
    void validDTO_noViolations() {
        AttendanceMarkRequestDTO dto = AttendanceMarkRequestDTO.builder()
                .sessionId(1L)
                .items(java.util.List.of(presentItem()))
                .build();
        Set<ConstraintViolation<AttendanceMarkRequestDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());
    }

    @Test
    void nullSessionId_violation() {
        AttendanceMarkRequestDTO dto = AttendanceMarkRequestDTO.builder()
                .sessionId(null)
                .items(java.util.List.of(presentItem()))
                .build();
        Set<ConstraintViolation<AttendanceMarkRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("sessionId")));
    }

    @Test
    void emptyItems_violation() {
        AttendanceMarkRequestDTO dto = AttendanceMarkRequestDTO.builder()
                .sessionId(1L)
                .items(java.util.List.of())
                .build();
        Set<ConstraintViolation<AttendanceMarkRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("items")));
    }

    @Test
    void nullStudentIdInItem_violation() {
        AttendanceMarkRequestDTO dto = AttendanceMarkRequestDTO.builder()
                .sessionId(1L)
                .items(java.util.List.of(AttendanceMarkItemDTO.builder().status("PRESENT").build()))
                .build();
        Set<ConstraintViolation<AttendanceMarkRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void nullItem_violation() {
        AttendanceMarkRequestDTO dto = AttendanceMarkRequestDTO.builder()
                .sessionId(1L)
                .items(java.util.Arrays.asList(presentItem(), null))
                .build();
        Set<ConstraintViolation<AttendanceMarkRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().startsWith("items")));
    }

    @Test
    void invalidStatusInItem_violation() {
        AttendanceMarkRequestDTO dto = AttendanceMarkRequestDTO.builder()
                .sessionId(1L)
                .items(java.util.List.of(AttendanceMarkItemDTO.builder().studentId(1L).status("LATE").build()))
                .build();
        Set<ConstraintViolation<AttendanceMarkRequestDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().endsWith("status")));
    }
}
