package lk.tutionlms.backend.config;

import com.cloudinary.Cloudinary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary(
            @Value("${cloudinary.cloud-name:duwtj2wa7}") String cloudName,
            @Value("${cloudinary.api-key:423854672574226}") String apiKey,
            @Value("${cloudinary.api-secret:tuVirGvc08f0_AxwrFENCvpdFl8}") String apiSecret) {

        Map<String, String> values = new HashMap<>();
        values.put("cloud_name", clean(cloudName, "duwtj2wa7"));
        values.put("api_key", clean(apiKey, "423854672574226"));
        values.put("api_secret", clean(apiSecret, "tuVirGvc08f0_AxwrFENCvpdFl8"));
        return new Cloudinary(values);
    }

    private String clean(String val, String fallback) {
        if (val == null || val.isBlank()) {
            return fallback;
        }
        val = val.trim();
        if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
            val = val.substring(1, val.length() - 1).trim();
        }
        return val.isEmpty() ? fallback : val;
    }
}
