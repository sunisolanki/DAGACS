package com.dagacs.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * M9.6 M: conservative, instance-local, ephemeral login rate limiter.
 *
 * <p>Scope: only {@code POST /api/auth/login}. Counters are keyed by the login
 * email AND by the source IP, in fixed windows (defaults 5/email, 20/IP per 15
 * minutes). Only FAILED logins (HTTP 401) count; a successful login clears the
 * email counter so a legitimate lockout heals on a correct password. Exceeded
 * thresholds short-circuit with HTTP 429 plus a {@code Retry-After} header and
 * a JSON envelope matching the application error format.
 *
 * <p>Deliberately NOT persisted, NOT distributed, and NOT exported: counters
 * live in a {@link ConcurrentHashMap} per instance and are lost on restart. The
 * {@code X-Forwarded-For} header is ignored unless the app explicitly runs
 * behind a trusted proxy ({@code use-forwarded-header=true}), preventing spoofed
 * IP bypass/abuse.
 *
 * <p>Unknown-email logins still return the generic 401 from the authentication
 * flow; this filter only observes the resulting status and never discloses
 * whether an account exists. It also never inspects state-sensitive endpoints
 * and has no logout/identity behaviour, so it cannot trigger client logout.
 *
 * <p>The request body is buffered once so the email key can be derived, then
 * replayed through a replayable wrapper so the controller's {@code @Body} read
 * still sees the full JSON payload (login bodies are tiny).
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String EMAIL_KEY_PREFIX = "email:";
    private static final String IP_KEY_PREFIX = "ip:";
    private static final int MAX_BUCKETS = 10_000;

    private final LoginRateLimitProperties properties;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, Bucket> emailFailures = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> ipFailures = new ConcurrentHashMap<>();

    public LoginRateLimitFilter(LoginRateLimitProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        return !"POST".equalsIgnoreCase(request.getMethod())
                || !LOGIN_PATH.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        byte[] rawBody = readRawBody(request);
        String email = parseIdentifier(rawBody);
        String clientIp = clientIp(request);

        if (isBlocked(email, clientIp, response)) {
            return;
        }

        filterChain.doFilter(new ReplayableRequestWrapper(request, rawBody), response);

        int status = response.getStatus();
        if (status == HttpServletResponse.SC_OK) {
            if (email != null) {
                emailFailures.remove(emailKey(email));
            }
        } else if (status == HttpServletResponse.SC_UNAUTHORIZED) {
            if (email != null) {
                increment(emailFailures, emailKey(email));
            }
            increment(ipFailures, ipKey(clientIp));
        }
        evictIfOversized();
    }

    private boolean isBlocked(String email, String clientIp, HttpServletResponse response) throws IOException {
        long emailRemaining = remainingMillis(emailKey(email), emailFailures, properties.getMaxAttemptsPerEmail());
        long ipRemaining = remainingMillis(ipKey(clientIp), ipFailures, properties.getMaxAttemptsPerIp());
        if (emailRemaining <= 0 && ipRemaining <= 0) {
            return false;
        }
        long retryAfterSeconds = Math.max(1, TimeUnit.MILLISECONDS.toSeconds(Math.max(emailRemaining, ipRemaining)));
        writeTooManyRequests(response, retryAfterSeconds);
        return true;
    }

    private long remainingMillis(String key, ConcurrentHashMap<String, Bucket> buckets, int maxAttempts) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long elapsed = now - bucket.windowStartMillis;
        if (elapsed >= windowMillis()) {
            buckets.remove(key, bucket);
            return 0;
        }
        if (bucket.value() < maxAttempts) {
            return 0;
        }
        return windowMillis() - elapsed;
    }

    private int increment(ConcurrentHashMap<String, Bucket> buckets, String key) {
        long now = System.currentTimeMillis();
        Bucket merged = buckets.compute(key, (k, bucket) -> {
            if (bucket == null || now - bucket.windowStartMillis >= windowMillis()) {
                return new Bucket(now);
            }
            bucket.increment();
            return bucket;
        });
        return merged.value();
    }

    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds) throws IOException {
        response.setStatus(429);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "error", "Too Many Requests",
                "message", "Too many login attempts. Please try again later.",
                "retryAfterSeconds", retryAfterSeconds)));
    }

    private byte[] readRawBody(HttpServletRequest request) {
        try (InputStream in = request.getInputStream()) {
            return in.readAllBytes();
        } catch (IOException ignored) {
            return new byte[0];
        }
    }

    private String parseIdentifier(byte[] body) {
        if (body == null || body.length == 0) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            String identifier = node.path("identifier").asText(null);
            if (identifier == null || identifier.isBlank()) {
                identifier = node.path("email").asText(null);
            }
            if (identifier == null || identifier.isBlank()) {
                return null;
            }
            return identifier.trim();
        } catch (IOException ignored) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        if (properties.isUseForwardedHeader()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }

    private void evictIfOversized() {
        if (emailFailures.size() + ipFailures.size() > MAX_BUCKETS) {
            evictStale(emailFailures);
            evictStale(ipFailures);
            if (emailFailures.size() + ipFailures.size() > MAX_BUCKETS) {
                emailFailures.clear();
                ipFailures.clear();
            }
        }
    }

    private void evictStale(ConcurrentHashMap<String, Bucket> buckets) {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> now - entry.getValue().windowStartMillis >= windowMillis());
    }

    private long windowMillis() {
        return TimeUnit.MINUTES.toMillis(properties.getWindowMinutes());
    }

    private String emailKey(String email) {
        return EMAIL_KEY_PREFIX + (email == null ? "<missing>" : email.toLowerCase(Locale.ROOT));
    }

    private String ipKey(String ip) {
        return IP_KEY_PREFIX + ip;
    }

    /** Fixed-window counter; the window anchor is fixed at bucket creation. */
    private static final class Bucket {
        private final long windowStartMillis;
        private final AtomicInteger count = new AtomicInteger(1);

        Bucket(long windowStartMillis) {
            this.windowStartMillis = windowStartMillis;
        }

        int increment() {
            return count.incrementAndGet();
        }

        int value() {
            return count.get();
        }
    }

    /** Wraps a buffered body so every {@code getInputStream()} read yields it again. */
    private static final class ReplayableRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        ReplayableRequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body == null ? new byte[0] : body;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new ReplayableServletInputStream(body);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }

    private static final class ReplayableServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        ReplayableServletInputStream(byte[] body) {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException("Non-blocking reads are not supported");
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, len);
        }
    }
}