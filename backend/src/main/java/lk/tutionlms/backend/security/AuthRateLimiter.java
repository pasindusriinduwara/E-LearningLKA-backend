package lk.tutionlms.backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

/** Single-instance protection. The reverse proxy should enforce additional shared limits in a cluster. */
@Component
public class AuthRateLimiter {
    private record Window(long expiresAt, int requests) {}
    private final Map<String, Window> windows = new HashMap<>();
    private final Clock clock;
    private final int loginIpLimit;
    private final int loginAccountLimit;
    private final long loginWindowMillis;
    private final long loginAccountWindowMillis;
    private final int registerIpLimit;
    private final int registerAccountLimit;
    private final long registerWindowMillis;
    private final int maxEntries;

    @Autowired
    public AuthRateLimiter(
            @Value("${application.security.auth-rate-limit.login-ip-limit:100}") int loginIpLimit,
            @Value("${application.security.auth-rate-limit.login-account-limit:10}") int loginAccountLimit,
            @Value("${application.security.auth-rate-limit.login-window-seconds:60}") long loginWindowSeconds,
            @Value("${application.security.auth-rate-limit.login-account-window-seconds:900}") long loginAccountWindowSeconds,
            @Value("${application.security.auth-rate-limit.register-ip-limit:10}") int registerIpLimit,
            @Value("${application.security.auth-rate-limit.register-account-limit:3}") int registerAccountLimit,
            @Value("${application.security.auth-rate-limit.register-window-seconds:600}") long registerWindowSeconds,
            @Value("${application.security.auth-rate-limit.max-entries:10000}") int maxEntries) {
        this(loginIpLimit, loginAccountLimit, loginWindowSeconds, loginAccountWindowSeconds, registerIpLimit, registerAccountLimit,
                registerWindowSeconds, maxEntries, Clock.systemUTC());
    }

    AuthRateLimiter(int loginIpLimit, int loginAccountLimit, long loginWindowSeconds, long loginAccountWindowSeconds,
                    int registerIpLimit, int registerAccountLimit, long registerWindowSeconds,
                    int maxEntries, Clock clock) {
        if (loginIpLimit < 1 || loginAccountLimit < 1 || loginWindowSeconds < 1
                || registerIpLimit < 1 || registerAccountLimit < 1 || registerWindowSeconds < 1
                || maxEntries < 2 || loginWindowSeconds > 86400 || registerWindowSeconds > 86400
                || loginAccountWindowSeconds < 1 || loginAccountWindowSeconds > 86400) {
            throw new IllegalArgumentException("Authentication rate limits must be positive; windows cannot exceed one day.");
        }
        this.loginIpLimit = loginIpLimit;
        this.loginAccountLimit = loginAccountLimit;
        this.loginWindowMillis = loginWindowSeconds * 1000;
        this.loginAccountWindowMillis = loginAccountWindowSeconds * 1000;
        this.registerIpLimit = registerIpLimit;
        this.registerAccountLimit = registerAccountLimit;
        this.registerWindowMillis = registerWindowSeconds * 1000;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    public synchronized void check(boolean registration, String remoteAddress, String email) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        String scope = registration ? "register:" : "login:";
        long duration = registration ? registerWindowMillis : loginWindowMillis;
        // Never store or log raw credentials. Account limits apply across different source addresses.
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        if (normalizedEmail.length() > 320) throw new IllegalArgumentException("Invalid email address.");
        String ipKey = scope + "ip:" + digest(remoteAddress == null ? "unknown" : remoteAddress);
        String accountKey = scope + "account:" + digest(normalizedEmail);
        consume(ipKey, registration ? registerIpLimit : loginIpLimit, now, duration);
        consume(accountKey, registration ? registerAccountLimit : loginAccountLimit, now,
                registration ? registerWindowMillis : loginAccountWindowMillis);
    }

    private void consume(String key, int limit, long now, long duration) {
        Window existing = windows.get(key);
        if (existing == null) {
            if (windows.size() >= maxEntries) {
                long earliest = windows.values().stream().mapToLong(Window::expiresAt).min().orElse(now + duration);
                throw new AuthRateLimitException(secondsUntil(earliest, now));
            }
            windows.put(key, new Window(now + duration, 1));
        } else {
            if (existing.requests() >= limit) {
                throw new AuthRateLimitException(secondsUntil(existing.expiresAt(), now));
            }
            windows.put(key, new Window(existing.expiresAt(), existing.requests() + 1));
        }
    }

    private static long secondsUntil(long until, long now) {
        return Math.max(1, (until - now + 999) / 1000);
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
