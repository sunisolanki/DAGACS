package com.dagacs.exception;

public class AuthException extends RuntimeException {
    private final int status;
    private final String code;

    public AuthException(String message, int status) {
        this(message, status, null);
    }

    public AuthException(String message, int status, String code) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int getStatus() {
        return status;
    }

    /**
     * Optional stable machine-readable error code (M9.5.4), absent for
     * pre-migration callers. When present it is serialized by
     * {@link GlobalExceptionHandler} as the {@code code} field.
     */
    public String getCode() {
        return code;
    }
}