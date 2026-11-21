package lk.tutionlms.backend.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        return new Cloudinary(Map.of(
                "cloud_name", requiredValue(cloudName, "CLOUDINARY_CLOUD_NAME"),
                "api_key", requiredValue(apiKey, "CLOUDINARY_API_KEY"),
                "api_secret", requiredValue(apiSecret, "CLOUDINARY_API_SECRET")));
    }

    private String requiredValue(String value, String setting) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        if (normalized.isEmpty()) {
            throw new IllegalStateException("Cloudinary configuration is missing. Set " + setting
                    + " before starting the backend.");
        }
        return normalized;
    }
}
