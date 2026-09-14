package com.dagacs.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M9.3 acceptance tests for the teacher-assignment request contract: the
 * relational identities teacherId and subjectOfferingId are mandatory;
 * exactly one of sectionId | batchId is required (XOR enforced at the
 * service boundary, not by bean validation). No subjectId, semesterId,
 * academicSessionId, programId, departmentId or studentId may appear in the
 * request payload.
 */
class TeacherAssignmentDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private TeacherAssignmentRequestDTO validRequest() {
        return TeacherAssignmentRequestDTO.builder()
                .teacherId(1L)
                .subjectOfferingId(2L)
                .sectionId(3L)
                .build();
    }

    private boolean hasViolationOn(TeacherAssignmentRequestDTO dto, String property) {
        Set<ConstraintViolation<TeacherAssignmentRequestDTO>> violations = validator.validate(dto);
        return violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(property));
    }

    @Test
    void validRequest_noViolations() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @Test
    void missingTeacherId_violation() {
        TeacherAssignmentRequestDTO dto = validRequest();
        dto.setTeacherId(null);
        assertFalse(validator.validate(dto).isEmpty());
        assertTrue(hasViolationOn(dto, "teacherId"));
    }

    @Test
    void missingSubjectOfferingId_violation() {
        TeacherAssignmentRequestDTO dto = validRequest();
        dto.setSubjectOfferingId(null);
        assertFalse(validator.validate(dto).isEmpty());
        assertTrue(hasViolationOn(dto, "subjectOfferingId"));
    }

    @Test
    void missingSectionId_allowed_noViolation() {
        // Phase 2: section is optional on the request DTO; the XOR of
        // {sectionId, batchId} is enforced at the service boundary.
        TeacherAssignmentRequestDTO dto = validRequest();
        dto.setSectionId(null);
        dto.setBatchId(4L);
        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void requestCarriesOnlyAllowedRelationalIdentities() {
        assertTrue(validRequest().getTeacherId() != null);
        assertTrue(validRequest().getSubjectOfferingId() != null);
        assertTrue(validRequest().getSectionId() != null);
        assertFalse(validRequest().toString().contains("subjectId"));
        assertFalse(validRequest().toString().contains("semesterId"));
        assertFalse(validRequest().toString().contains("academicSessionId"));
        assertFalse(validRequest().toString().contains("programId"));
        assertFalse(validRequest().toString().contains("departmentId"));
        assertFalse(validRequest().toString().contains("studentId"));
    }
}