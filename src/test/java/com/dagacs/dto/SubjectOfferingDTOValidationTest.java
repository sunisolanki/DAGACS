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
 * M9.2 acceptance tests for the SubjectOffering request contract: exactly two
 * relational identities (subjectId, semesterId), both mandatory. Academic
 * context ids are deliberately absent from the request.
 */
class SubjectOfferingDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private SubjectOfferingRequestDTO validRequest() {
        return SubjectOfferingRequestDTO.builder()
                .subjectId(1L)
                .semesterId(5L)
                .build();
    }

    private boolean hasViolationOn(SubjectOfferingRequestDTO dto, String property) {
        Set<ConstraintViolation<SubjectOfferingRequestDTO>> violations = validator.validate(dto);
        return violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(property));
    }

    @Test
    void validRequest_noViolations() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @Test
    void missingSubjectId_violation() {
        SubjectOfferingRequestDTO dto = validRequest();
        dto.setSubjectId(null);
        assertFalse(validator.validate(dto).isEmpty());
        assertTrue(hasViolationOn(dto, "subjectId"));
    }

    @Test
    void missingSemesterId_violation() {
        SubjectOfferingRequestDTO dto = validRequest();
        dto.setSemesterId(null);
        assertFalse(validator.validate(dto).isEmpty());
        assertTrue(hasViolationOn(dto, "semesterId"));
    }

    @Test
    void requestCarriesNoAcademicContextFields() {
        assertFalse(validRequest().toString().contains("programId"));
        assertFalse(validRequest().toString().contains("academicSessionId"));
        assertFalse(validRequest().toString().contains("departmentId"));
        assertTrue(validRequest().getSubjectId() != null);
        assertTrue(validRequest().getSemesterId() != null);
    }
}