package lk.tutionlms.backend.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import lk.tutionlms.backend.common.GlobalExceptionHandler;
import lk.tutionlms.backend.identity.auth.AuthController;
import lk.tutionlms.backend.identity.auth.AuthenticationService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthRateLimiterTests {
    private static class MutableClock extends Clock {
        private Instant time = Instant.parse("2026-01-01T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return time; }
        void advance(long seconds) { time = time.plusSeconds(seconds); }
    }

    @Test
    void accountLimitAppliesAcrossIpsCaseAndWhitespaceAndExpires() {
        MutableClock clock = new MutableClock();
        AuthRateLimiter limiter = new AuthRateLimiter(100, 2, 60, 900, 10, 3, 600, 100, clock);
        limiter.check(false, "192.0.2.1", "Student@Example.test");
        limiter.check(false, "192.0.2.2", " student@example.test ");
        AuthRateLimitException blocked = assertThrows(AuthRateLimitException.class,
                () -> limiter.check(false, "192.0.2.3", "STUDENT@example.test"));
        assertEquals(900, blocked.getRetryAfterSeconds());
        clock.advance(899);
        assertThrows(AuthRateLimitException.class, () -> limiter.check(false, "192.0.2.4", "student@example.test"));
        clock.advance(1);
        assertDoesNotThrow(() -> limiter.check(false, "192.0.2.4", "student@example.test"));
    }

    @Test
    void boundedMemoryFailsClosedUntilExpiredEntriesCanBeReclaimed() {
        MutableClock clock = new MutableClock();
        AuthRateLimiter limiter = new AuthRateLimiter(100, 10, 60, 60, 10, 3, 60, 2, clock);
        limiter.check(false, "192.0.2.1", "one@example.test");
        assertThrows(AuthRateLimitException.class, () -> limiter.check(false, "192.0.2.2", "two@example.test"));
        clock.advance(60);
        assertDoesNotThrow(() -> limiter.check(false, "192.0.2.2", "two@example.test"));
    }

    @Test
    void loginReturns429AndRetryAfterEvenWithForgedForwardedIpsWithoutLoggingCredentials() throws Exception {
        AuthenticationService auth = mock(AuthenticationService.class);
        when(auth.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));
        AuthRateLimiter limiter = new AuthRateLimiter(2, 100, 60, 900, 10, 3, 600, 100);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth, limiter))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.start(); root.addAppender(capture);
        try {
            for (int i = 0; i < 3; i++) {
                var result = mvc.perform(post("/auth/login")
                        .with(request -> { request.setRemoteAddr("192.0.2.10"); return request; })
                        .header("X-Forwarded-For", "203.0.113." + i)
                        .contentType("application/json")
                        .content("{\"email\":\"private-account-" + i + "@example.test\",\"password\":\"DO-NOT-LOG-secret\"}"));
                if (i < 2) result.andExpect(status().isUnauthorized());
                else result.andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"))
                        .andExpect(jsonPath("$.message").value("Too many authentication attempts. Please try again later."));
            }
            verify(auth, times(2)).authenticate(any());
            assertTrue(capture.list.stream().noneMatch(event -> event.getFormattedMessage().contains("DO-NOT-LOG-secret")
                    || event.getFormattedMessage().contains("private-account-")));
        } finally {
            root.detachAppender(capture); capture.stop();
        }
    }

    @Test
    void registrationHasAnIndependentLimitAndDoesNotCallServiceWhenBlocked() throws Exception {
        AuthenticationService auth = mock(AuthenticationService.class);
        AuthRateLimiter limiter = new AuthRateLimiter(100, 10, 60, 900, 100, 1, 600, 100);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(auth, limiter))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String registration = "{\"email\":\"new@example.test\",\"password\":\"registration-secret\"}";
        mvc.perform(post("/auth/register").contentType("application/json").content(registration)).andExpect(status().isOk());
        mvc.perform(post("/auth/register").contentType("application/json").content(registration))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        verify(auth, times(1)).register(any());
    }
}
