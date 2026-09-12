package lk.tutionlms.backend.config;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lk.tutionlms.backend.security.JwtAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${application.cors.allowed-origins:http://localhost:3000}")
    private String allowedOrigins;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final AuthenticationProvider authenticationProvider;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationProvider authenticationProvider) {

        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationProvider = authenticationProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/v1/batches").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/batches").hasRole("TEACHER")

                        .requestMatchers("/api/v1/teacher/**").hasRole("TEACHER")

                        .requestMatchers(HttpMethod.GET, "/api/v1/subjects").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/subjects").hasRole("TEACHER")

                        .requestMatchers("/api/v1/users/**").authenticated()
                        .requestMatchers("/api/v1/students/**").hasRole("STUDENT")

                        .requestMatchers(HttpMethod.POST, "/api/v1/enrollments/request")
                        .hasRole("STUDENT")

                        .requestMatchers(HttpMethod.GET, "/api/v1/enrollments/my-status")
                        .hasRole("STUDENT")

                        .requestMatchers(HttpMethod.GET, "/api/v1/enrollments/pending")
                        .hasRole("TEACHER")

                        .requestMatchers(HttpMethod.PUT, "/api/v1/enrollments/*/approve")
                        .hasRole("TEACHER")

                        .requestMatchers(HttpMethod.GET, "/api/v1/enrollments/teacher/batches/**").hasRole("TEACHER")

                        .requestMatchers(HttpMethod.POST, "/api/v1/assessments").hasRole("TEACHER")
                        .requestMatchers("/api/v1/assessments/**").authenticated()

                        .requestMatchers("/api/v1/materials/**").authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/v1/schedules/**").permitAll()
                        .requestMatchers("/api/v1/schedules/**").authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/v1/announcements/**").permitAll()
                        .requestMatchers("/api/v1/announcements/**").authenticated()

                        .requestMatchers("/api/v1/invoices/**").authenticated()

                        .anyRequest().authenticated())
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        configuration.setAllowedOrigins(origins);

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));

        configuration.setExposedHeaders(
                List.of("Authorization"));

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
