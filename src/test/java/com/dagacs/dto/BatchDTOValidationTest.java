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
 * Bean Validation acceptance tests for the M9.1 Batch (admission cohort) payload.
 * <p>
 * batchCode, name, year, academicSessionId and maxCapacity are mandatory.
 * year is the cohort's admission year and is bounded to 2000-2100 (matches the
 * Flutter form guard). program is derived server-side and is NOT part of the
 * request contract.
 * </p>
 */
class BatchDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private BatchDTO validBatchDTO() {
        return BatchDTO.builder()
                .batchCode("BT25CS")
                .name("Batch 2025")
                .year(2025)
                .academicSessionId(1L)
                .maxCapacity(60)
                .build();
    }

    private Set<ConstraintViolation<BatchDTO>> validate(BatchDTO dto) {
        return validator.validate(dto);
    }

    private void assertSingleViolationOn(BatchDTO dto, String property) {
        Set<ConstraintViolation<BatchDTO>> violations = validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(property)));
    }

    @Test
    void validBatchDTO_noViolations() {
        assertTrue(validate(validBatchDTO()).isEmpty());
    }

    @Test
    void missingBatchCode_violation() {
        BatchDTO dto = validBatchDTO();
        dto.setBatchCode(null);
        assertSingleViolationOn(dto, "batchCode");
    }

    @Test
    void blankBatchCode_violation() {
        BatchDTO dto = validBatchDTO();
        dto.setBatchCode("   ");
        assertSingleViolationOn(dto, "batchCode");
    }

    @Test
    void missingName_violation() {
        BatchDTO dto = validBatchDTO();
        dto.setName(null);
        assertSingleViolationOn(dto, "name");
    }

    @Test
    void missingYear_violation() {
        BatchDTO dto = validBatchDTO();
        dto.setYear(null);
        assertSingleViolationOn(dto, "year");
    }

    @Test
    void yearBeforeMinimum_violation() {
        BatchDTO dto = validBatchDTO();
        dto.setYear(1999);
        assertSingleViolationOn(dto, "year");
    }

    @Test
    void yearAfterMaximum_violation() {
        BatchDTO dto = validBatchDTO();
        dto.setYear(2101);
        assertSingleViolationOn(dto, "year");
    }

    @Test
    void boundaryYears_accepted() {
        BatchDTO lower = validBatchDTO();
        lower.setYear(2000);
        assertTrue(validate(lower).isEmpty());
        BatchDTO upper = validBatchDTO();
        upper.setYear(2100);
        assertTrue(validate(upper).isEmpty());
    }

    @Test
    void missingAcademicSessionId_violation() {
        BatchDTO dto = validBatchDTO();
        dto.setAcademicSessionId(null);
        assertSingleViolationOn(dto, "academicSessionId");
    }

    @Test
    void missingMaxCapacity_violation() {
        BatchDTO dto = validBatchDTO();
        dto.setMaxCapacity(null);
        assertSingleViolationOn(dto, "maxCapacity");
    }

    @Test
    void oversizedBatchCode_violation() {
        BatchDTO dto = validBatchDTO();
        dto.setBatchCode("B".repeat(21));
        assertSingleViolationOn(dto, "batchCode");
    }

    @Test
    void oversizedName_violation() {
        BatchDTO dto = validBatchDTO();
        dto.setName("N".repeat(101));
        assertSingleViolationOn(dto, "name");
    }
}