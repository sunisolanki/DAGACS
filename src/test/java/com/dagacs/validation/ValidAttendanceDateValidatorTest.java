package com.dagacs.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidAttendanceDateValidatorTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
    }

    static final class DateHolder {
        @ValidAttendanceDate
        private String date;

        DateHolder(String date) {
            this.date = date;
        }
    }

    @Test
    void valid_iso_dates_pass() {
        assertPasses("2026-01-01");
        assertPasses("2026-12-31");
        assertPasses("2026-06-05");
        assertPasses(null);
        assertPasses("");
    }

    @Test
    void invalid_formats_fail() {
        assertFails("01-01-2026");
        assertFails("2026/01/01");
        assertFails("2026-1-1");
        assertFails("2026-13-01");
        assertFails("2026-00-01");
        assertFails("2026-01-32");
        assertFails("2026-01-00");
        assertFails("2026-0101-01");
        assertFails("not-a-date");
    }

    @Test
    void calendar_valid_dates_pass() {
        assertPasses("2024-02-29");
        assertPasses("2025-02-28");
        assertPasses("2024-04-30");
        assertPasses("2024-12-31");
        assertPasses("2024-01-31");
        assertPasses("2024-06-30");
        assertPasses("2024-09-30");
        assertPasses("2024-11-30");
    }

    @Test
    void impossible_calendar_dates_fail() {
        assertFails("2024-02-30");
        assertFails("2024-02-31");
        assertFails("2023-02-29");
        assertFails("2024-04-31");
        assertFails("2024-06-31");
        assertFails("2024-09-31");
        assertFails("2024-11-31");
    }

    private void assertPasses(String date) {
        DateHolder holder = new DateHolder(date);
        Set<ConstraintViolation<DateHolder>> violations = validator.validate(holder);
        assertTrue(violations.isEmpty(), "Expected no violations for: " + date);
    }

    private void assertFails(String date) {
        DateHolder holder = new DateHolder(date);
        Set<ConstraintViolation<DateHolder>> violations = validator.validate(holder);
        assertFalse(violations.isEmpty(), "Expected violations for: " + date);
        assertEquals("Date must be a valid YYYY-MM-DD date",
                violations.iterator().next().getMessage());
    }
}
