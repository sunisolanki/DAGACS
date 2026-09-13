package com.dagacs.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthRequest {

    /**
     * The login identifier (roll number or email). New M10A clients send this.
     * Presence is validated in {@link com.dagacs.service.AuthService} so legacy
     * M1-M9.18 clients sending only {@code email} keep working.
     */
    private String identifier;

    /**
     * Backward-compatible alias for the M1-M9.18 login payload which sent
     * {@code email}. Resolution prefers {@code identifier}; this field is used
     * only when the former is blank.
     */
    private String email;

    @NotBlank(message = "Password is required")
    private String password;
}