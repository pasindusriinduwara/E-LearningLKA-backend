package lk.tutionlms.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.http.SessionCreationPolicy;
import lk.tutionlms.backend.security.JwtAuthenticationFilter;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authenticationProvider;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationProvider authenticationProvider) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationProvider = authenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. CORS සක්‍රීය කිරීම (මෙය අනිවාර්යයි)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. CSRF disable කිරීම (JWT භාවිතා කරන නිසා)
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authenticationProvider(authenticationProvider)

                // ... (ඔබගේ අනෙකුත් Security configurations එලෙසම තබන්න)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 3. CORS Configuration Bean එක සෑදීම
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Frontend එකේ URL එකට පමණක් අවසර දීම
        configuration.setAllowedOrigins(List.of("http://localhost:3000"));

        // ඉඩදෙන HTTP Methods
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // ඉඩදෙන Headers
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));

        // Credentials (Cookies/Tokens) වලට ඉඩ දීම
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // පද්ධතියේ ඇති සියලුම Endpoints ("/**") සඳහා මෙම නීති අදාළ කිරීම
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
