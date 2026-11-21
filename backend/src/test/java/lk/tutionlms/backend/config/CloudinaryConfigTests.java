package lk.tutionlms.backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

class CloudinaryConfigTests {
    private final CloudinaryConfig config = new CloudinaryConfig();
    private static final String CLOUD = "synthetic-test-cloud";
    private static final String KEY = "synthetic-test-api-key";
    private static final String SECRET = "synthetic-test-api-secret";

    @Test void usesOnlyExplicitlyConfiguredValuesAndTrimsQuotedEnvironmentValues() {
        var cloudinary = config.cloudinary("  \"" + CLOUD + "\"  ", " " + KEY + " ", "\"" + SECRET + "\"");
        assertEquals(CLOUD, cloudinary.config.cloudName);
        assertEquals(KEY, cloudinary.config.apiKey);
        assertEquals(SECRET, cloudinary.config.apiSecret);
    }

    @Test void missingCloudNameCannotFallBackToCommittedCredentials() {
        assertMissing("CLOUDINARY_CLOUD_NAME", () -> config.cloudinary(null, KEY, SECRET));
    }

    @Test void missingApiKeyCannotFallBackToCommittedCredentials() {
        assertMissing("CLOUDINARY_API_KEY", () -> config.cloudinary(CLOUD, " ", SECRET));
    }

    @Test void missingOrQuotedBlankApiSecretCannotFallBackToCommittedCredentials() {
        assertMissing("CLOUDINARY_API_SECRET", () -> config.cloudinary(CLOUD, KEY, ""));
        assertMissing("CLOUDINARY_API_SECRET", () -> config.cloudinary(CLOUD, KEY, " \"  \" "));
    }

    @Test void applicationContextRejectsAbsentConfigurationWithoutReadingEnvironmentSecrets() {
        new ApplicationContextRunner().withUserConfiguration(CloudinaryConfig.class)
                .withPropertyValues("cloudinary.cloud-name=", "cloudinary.api-key=", "cloudinary.api-secret=")
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    Throwable cause = context.getStartupFailure();
                    while (cause.getCause() != null) cause = cause.getCause();
                    assertEquals(IllegalStateException.class, cause.getClass());
                    assertTrue(cause.getMessage().contains("CLOUDINARY_CLOUD_NAME"));
                });
    }

    private void assertMissing(String setting, org.junit.jupiter.api.function.Executable action) {
        var error = assertThrows(IllegalStateException.class, action);
        assertTrue(error.getMessage().contains(setting));
        assertFalse(error.getMessage().contains(SECRET));
        assertFalse(error.getMessage().contains(KEY));
    }
}
