package com.dagacs.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * M9.6 M: login rate-limiting configuration bound to
 * {@code app.security.login-rate-limit.*}. Env placeholders in application.yml
 * carry development-safe defaults so production can tune them without new
 * secrets.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.security.login-rate-limit")
public class LoginRateLimitProperties {

    private boolean enabled = true;
    private int maxAttemptsPerEmail = 5;
    private int maxAttemptsPerIp = 20;
    private long windowMinutes = 15;
    private boolean useForwardedHeader = false;
}