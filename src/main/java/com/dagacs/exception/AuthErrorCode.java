package com.dagacs.exception;

/**
 * Stable machine-readable codes for identity-resolution 401 responses (M9.5.4).
 * <p>
 * These codes are additive and never appear on successful responses. They let
 * the client distinguish a *profile linkage* problem (the authenticated login
 * has no/disabled teacher/student/HOD profile) from an *expired or invalid JWT*,
 * so the UI can show an actionable message instead of silently forcing a logout.
 * </p>
 */
public final class AuthErrorCode {

    private AuthErrorCode() {
    }

    public static final String UNABLE_TO_RESOLVE_IDENTITY = "UNABLE_TO_RESOLVE_IDENTITY";

    public static final String TEACHER_PROFILE_NOT_LINKED = "TEACHER_PROFILE_NOT_LINKED";
    public static final String TEACHER_PROFILE_INACTIVE = "TEACHER_PROFILE_INACTIVE";

    public static final String STUDENT_PROFILE_NOT_LINKED = "STUDENT_PROFILE_NOT_LINKED";
    public static final String STUDENT_PROFILE_INACTIVE = "STUDENT_PROFILE_INACTIVE";

    public static final String HOD_PROFILE_NOT_LINKED = "HOD_PROFILE_NOT_LINKED";
    public static final String HOD_PROFILE_INACTIVE = "HOD_PROFILE_INACTIVE";
    public static final String HOD_NOT_DESIGNATED = "HOD_NOT_DESIGNATED";
    public static final String HOD_NO_DEPARTMENT = "HOD_NO_DEPARTMENT";

    public static final String STUDENT_PASSWORD_CHANGE_REQUIRED = "STUDENT_PASSWORD_CHANGE_REQUIRED";
}