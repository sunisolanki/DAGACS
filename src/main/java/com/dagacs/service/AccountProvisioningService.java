package com.dagacs.service;

import com.dagacs.entity.Role;
import com.dagacs.entity.User;
import com.dagacs.exception.AuthException;
import com.dagacs.repository.RoleRepository;
import com.dagacs.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Login-account provisioning service (M9.5.1 / M9.5.2).
 * <p>
 * Owns every {@link User} row that is created from the admin UI. Enforces the
 * locked D1/D3/D4 invariants:
 * <ul>
 *   <li>D1 — a login is linked purely by email; the admin surfaces (teachers,
 *       students) never carry a {@code user_id}. {@link #provisionLogin} stamps
 *       {@code users.email} with the exact profile email so
 *       {@code User.email == Teacher.email == Student.email} holds
 *       transactionally at the moment of creation.</li>
 *   <li>D3 — the admin supplies the initial password; it is BCrypt-encoded here
 *       and is never stored or logged in plaintext.</li>
 *   <li>D4 — login status is independent of the profile status. This service
 *       changes login status only when asked to; it never cascades.</li>
 * </ul>
 * The {@code users.email} unique constraint is the final safety net: a
 * concurrent double-provisioning surfaces as {@code 409 Conflict} via the global
 * handler.
 */
@Service
public class AccountProvisioningService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String STATUS_INACTIVE = "INACTIVE";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public AccountProvisioningService(UserRepository userRepository,
                                      RoleRepository roleRepository,
                                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates a login account for {@code email} with the given role and status.
     * Rejects collisions (any existing {@link User} with the same email) with 409,
     * blank/weak passwords with 400, and missing roles with 500.
     */
    @Transactional
    public User provisionLogin(String email, String fullName, String rawPassword,
                               String roleName, String status) {
        String normalizedEmail = normalizeRequired(email, "Email is required");
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new AuthException("An account already exists for email: " + email, 409);
        }
        if (rawPassword == null || rawPassword.trim().length() < 8) {
            throw new AuthException("Password must be at least 8 characters", 400);
        }
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new AuthException("Required role is not configured: " + roleName, 500));
        String normalizedStatus = normalizeStatus(status);
        String fullNameValue = defaultValue(fullName);

        LocalDateTime now = LocalDateTime.now();
        return userRepository.save(User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(rawPassword.trim()))
                .fullName(fullNameValue)
                .phone("")
                .status(normalizedStatus)
                .role(role)
                .avatarUrl("")
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    /**
     * Encodes and persists a new password for an existing login (reset password).
     * The encoded hash is written to {@code users.password}; the raw password is
     * never retained.
     */
    public void resetPassword(User user, String rawPassword) {
        if (rawPassword == null || rawPassword.trim().length() < 8) {
            throw new AuthException("Password must be at least 8 characters", 400);
        }
        user.setPassword(passwordEncoder.encode(rawPassword.trim()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    /**
     * Flips the login status only. The linked teacher/student profile status is
     * deliberately left untouched (D4 — no cascading).
     */
    public void changeStatus(User user, String rawStatus) {
        String normalizedStatus = normalizeStatus(rawStatus);
        user.setStatus(normalizedStatus);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    static String normalizeStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.trim().isEmpty()) {
            return STATUS_ACTIVE;
        }
        String status = rawStatus.trim().toUpperCase();
        if (!STATUS_ACTIVE.equals(status) && !STATUS_INACTIVE.equals(status)) {
            throw new AuthException("Status must be ACTIVE or INACTIVE", 400);
        }
        return status;
    }

    private static String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new AuthException(message, 400);
        }
        return value.trim().toLowerCase();
    }

    private static String defaultValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return value.trim();
    }
}