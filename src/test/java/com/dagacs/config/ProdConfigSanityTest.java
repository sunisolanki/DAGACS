package com.dagacs.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M8 F-01 sanity guard: the production profile must never silently fall back to
 * the development secrets/passwords in application.yml. Every secret, DB
 * credential, seed password, and CORS origin in application-prod.yml is a
 * defaultless ${ENV_VAR} placeholder, so a missing variable fails startup.
 */
class ProdConfigSanityTest {

    private static final String DEV_JWT_SECRET =
            "dagacs-dev-secret-change-me-in-production-32bytes-min";

    private String prodYaml() throws IOException {
        return new String(
                new ClassPathResource("application-prod.yml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
    }

    @Test
    void prodProfile_containsNoDevJwtSecret() throws IOException {
        assertFalse(prodYaml().contains(DEV_JWT_SECRET),
                "application-prod.yml must not contain the development JWT secret");
    }

    @Test
    void prodProfile_containsNoDevSeedPasswords() throws IOException {
        String yaml = prodYaml();
        assertFalse(yaml.contains("Admin@123"));
        assertFalse(yaml.contains("Hod@123"));
        assertFalse(yaml.contains("Teacher@123"));
        assertFalse(yaml.contains("Student@123"));
    }

    @Test
    void prodProfile_mandatoryKeysAreDefaultlessPlaceholders() throws IOException {
        String yaml = prodYaml();
        String[] mandatory = {
                "url: ${DB_URL}",
                "username: ${DB_USERNAME}",
                "password: ${DB_PASSWORD}",
                "secret: ${JWT_SECRET}",
                "admin-password: ${DAGACS_ADMIN_PASSWORD}",
                "hod-password: ${DAGACS_HOD_PASSWORD}",
                "teacher-password: ${DAGACS_TEACHER_PASSWORD}",
                "student-password: ${DAGACS_STUDENT_PASSWORD}",
                "allowed-origins: ${CORS_ALLOWED_ORIGINS}"
        };
        for (String entry : mandatory) {
            assertTrue(yaml.contains(entry), "missing mandatory production entry: " + entry);
        }
    }

    @Test
    void prodProfile_noPlaceholderCarriesADefault() throws IOException {
        for (String line : prodYaml().split("\\r?\\n")) {
            String trimmed = line.trim();
            int start = trimmed.indexOf("${");
            if (start < 0) {
                continue;
            }
            String placeholder = trimmed.substring(start);
            assertFalse(placeholder.contains(":"),
                    "production placeholder must have no default: " + placeholder);
        }
    }

    @Test
    void prodProfile_corsOriginHasNoDevLocalhostFallback() throws IOException {
        String yaml = prodYaml();
        assertFalse(yaml.contains("localhost"),
                "application-prod.yml must not inherit the development localhost CORS origin");
    }
}