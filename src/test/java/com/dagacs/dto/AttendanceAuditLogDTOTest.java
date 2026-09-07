package com.dagacs.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AttendanceAuditLogDTOTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private AttendanceAuditLogDTO validDTO() {
        return AttendanceAuditLogDTO.builder()
                .attendanceId(1L)
                .studentId(1L)
                .rollNo("2201CE001")
                .studentName("Rahul Kumar")
                .subject("DBMS")
                .subjectName("Database Management Systems")
                .section("CS-A")
                .sectionName("CS-A")
                .date("2026-09-04")
                .previousStatus("ABSENT")
                .newStatus("PRESENT")
                .updatedBy("teacher@dagacs.local")
                .reason("Doctor certificate provided")
                .build();
    }

    @Test
    void validDTO_noViolations() {
        Set<ConstraintViolation<AttendanceAuditLogDTO>> violations = validator.validate(validDTO());
        assertTrue(violations.isEmpty());
    }

    @Test
    void nullAttendanceId_violation() {
        AttendanceAuditLogDTO dto = validDTO();
        dto.setAttendanceId(null);

        Set<ConstraintViolation<AttendanceAuditLogDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("attendanceId")));
    }

    @Test
    void nullStudentId_violation() {
        AttendanceAuditLogDTO dto = validDTO();
        dto.setStudentId(null);

        Set<ConstraintViolation<AttendanceAuditLogDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("studentId")));
    }

    @Test
    void blankRollNo_violation() {
        AttendanceAuditLogDTO dto = validDTO();
        dto.setRollNo(" ");

        Set<ConstraintViolation<AttendanceAuditLogDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("rollNo")));
    }

    @Test
    void blankStudentName_violation() {
        AttendanceAuditLogDTO dto = validDTO();
        dto.setStudentName(" ");

        Set<ConstraintViolation<AttendanceAuditLogDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("studentName")));
    }

    @Test
    void blankSubjectName_violation() {
        AttendanceAuditLogDTO dto = validDTO();
        dto.setSubjectName(" ");

        Set<ConstraintViolation<AttendanceAuditLogDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("subjectName")));
    }

    @Test
    void blankSectionName_violation() {
        AttendanceAuditLogDTO dto = validDTO();
        dto.setSectionName(" ");

        Set<ConstraintViolation<AttendanceAuditLogDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("sectionName")));
    }

    @Test
    void nullReason_noViolation() {
        AttendanceAuditLogDTO dto = validDTO();
        dto.setReason(null);

        Set<ConstraintViolation<AttendanceAuditLogDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty());
    }
}
