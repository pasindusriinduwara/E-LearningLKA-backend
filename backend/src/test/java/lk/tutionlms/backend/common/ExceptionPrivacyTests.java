package lk.tutionlms.backend.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionPrivacyTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private Level originalLevel;

    @BeforeEach
    void suppressExpectedServerErrorLogs() { originalLevel = logger.getLevel(); logger.setLevel(Level.OFF); }

    @AfterEach
    void restoreLogging() { logger.setLevel(originalLevel); }

    @Test
    void unexpectedErrorsHaveAGenericMessageAndACorrelationId() {
        var response = handler.handleGenericException(new RuntimeException("jdbc:private-server secret-password"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().toString().contains("private-server"));
        assertFalse(response.getBody().toString().contains("secret-password"));
        assertEquals(response.getBody().get("errorId"), response.getHeaders().getFirst("X-Error-Id"));
    }

    @Test
    void responseStatusServerErrorsCannotExposeTheirReason() {
        var response = handler.handleResponseStatusException(
                new ResponseStatusException(HttpStatus.BAD_GATEWAY, "upstream internal credentials"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().toString().contains("internal credentials"));
        assertNotNull(response.getBody().get("errorId"));
    }
}
