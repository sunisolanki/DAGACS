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
 * Bean Validation acceptance tests for the M5.2 create/update payload.
 * <p>
 * Approved scope: only rollNumber, name, programId, batchId and sectionId are
 * mandatory. All other fields are optional (null accepted) while their
 * length/format constraints (when supplied) still apply.
 * </p>
 */
class StudentManagementRequestDTOValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private StudentManagementRequestDTO onlyMandatoryFields() {
        return StudentManagementRequestDTO.builder()
                .rollNumber("2201CE001")
                .name("Rahul Kumar")
                .programId(1L)
                .batchId(1L)
                .sectionId(1L)
                .build();
    }

    private Set<ConstraintViolation<StudentManagementRequestDTO>> validate(StudentManagementRequestDTO dto) {
        return validator.validate(dto);
    }

    private void assertSingleViolationOn(StudentManagementRequestDTO dto, String property) {
        Set<ConstraintViolation<StudentManagementRequestDTO>> violations = validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(property)));
    }

    @Test
    void onlyMandatoryFields_noViolations() {
        Set<ConstraintViolation<StudentManagementRequestDTO>> violations =
                validate(onlyMandatoryFields());
        assertTrue(violations.isEmpty());
    }

    @Test
    void nullOptionalFields_accepted() {
        StudentManagementRequestDTO dto = StudentManagementRequestDTO.builder()
                .rollNumber("2201CE001")
                .email(null)
                .name("Rahul Kumar")
                .gender(null)
                .fatherName(null)
                .motherName(null)
                .photoUrl(null)
                .enrollmentNumber(null)
                .age(null)
                .admissionDate(null)
                .status(null)
                .programId(1L)
                .batchId(1L)
                .sectionId(1L)
                .build();
        assertTrue(validate(dto).isEmpty());
    }

    @Test
    void blankOptionalFields_accepted() {
        // Blank optional string fields are not mandatory; @NotBlank was removed.
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setGender("  ");
        dto.setFatherName("  ");
        dto.setMotherName("  ");
        dto.setEnrollmentNumber("  ");
        dto.setAdmissionDate("  ");
        assertTrue(validate(dto).isEmpty());
    }

    @Test
    void missingRollNumber_violation() {
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setRollNumber(null);
        assertSingleViolationOn(dto, "rollNumber");
    }

    @Test
    void blankRollNumber_violation() {
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setRollNumber("   ");
        assertSingleViolationOn(dto, "rollNumber");
    }

    @Test
    void missingName_violation() {
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setName(null);
        assertSingleViolationOn(dto, "name");
    }

    @Test
    void missingProgramId_violation() {
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setProgramId(null);
        assertSingleViolationOn(dto, "programId");
    }

    @Test
    void missingBatchId_violation() {
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setBatchId(null);
        assertSingleViolationOn(dto, "batchId");
    }

    @Test
    void missingSectionId_violation() {
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setSectionId(null);
        assertSingleViolationOn(dto, "sectionId");
    }

    @Test
    void invalidEmail_whenSupplied_violation() {
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setEmail("not-an-email");
        assertSingleViolationOn(dto, "email");
    }

    @Test
    void oversizedRollNumber_violation() {
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setRollNumber("R".repeat(51));
        assertSingleViolationOn(dto, "rollNumber");
    }

    @Test
    void oversizedName_violation() {
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setName("N".repeat(101));
        assertSingleViolationOn(dto, "name");
    }

    @Test
    void oversizedGender_violation() {
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setGender("G".repeat(11));
        assertSingleViolationOn(dto, "gender");
    }

    @Test
    void oversizedFatherName_violation() {
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setFatherName("F".repeat(101));
        assertSingleViolationOn(dto, "fatherName");
    }

    @Test
    void oversizedMotherName_violation() {
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setMotherName("M".repeat(101));
        assertSingleViolationOn(dto, "motherName");
    }

    @Test
    void oversizedEnrollmentNumber_violation() {
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setEnrollmentNumber("E".repeat(51));
        assertSingleViolationOn(dto, "enrollmentNumber");
    }

    @Test
    void oversizedAdmissionDate_violation() {
        StudentManagementRequestDTO dto = onlyMandatoryFields();
        dto.setAdmissionDate("A".repeat(21));
        assertSingleViolationOn(dto, "admissionDate");
    }
}