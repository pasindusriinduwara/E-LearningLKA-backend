package lk.tutionlms.backend.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import lk.tutionlms.backend.identity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Runs the real JWT filter without a database or a Spring application context. */
class JwtAuthenticationFilterTests {
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "test-only-signing-key-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
    private final JwtService jwtService = new JwtService(SECRET, 420_000);
    private final UserDetailsService users = mock(UserDetailsService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, users);

    @BeforeEach
    void clearContextBeforeRequest() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContextAfterRequest() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeAccountAuthenticatesWithItsCurrentAuthorities() throws Exception {
        User user = activeUser();
        when(users.loadUserByUsername(user.getUsername())).thenReturn(user);

        Authentication authentication = request(jwtService.generateToken(user));

        assertNotNull(authentication);
        assertSame(user, authentication.getPrincipal());
        assertTrue(authentication.isAuthenticated());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_STUDENT".equals(authority.getAuthority())));
    }

    @Test
    void samePreviouslyIssuedTokenStopsAuthenticatingAfterAccountSuspension() throws Exception {
        User active = activeUser();
        User suspended = activeUser();
        suspended.setStatus("SUSPENDED");
        when(users.loadUserByUsername(active.getUsername())).thenReturn(active, suspended);
        String token = jwtService.generateToken(active);

        assertNotNull(request(token));
        SecurityContextHolder.clearContext();
        assertNull(request(token), "Every new request must re-check the current persisted account status");

        verify(users, times(2)).loadUserByUsername(active.getUsername());
    }

    @Test
    void deletedAccountCannotUseAPreviouslyIssuedToken() throws Exception {
        User user = activeUser();
        String token = jwtService.generateToken(user);
        user.setDeleted(true);
        when(users.loadUserByUsername(user.getUsername())).thenReturn(user);

        assertNull(request(token));
    }

    @Test
    void invalidOrExpiredTokensContinueUnauthenticatedInsteadOfCausingServerErrors() throws Exception {
        User user = activeUser();
        when(users.loadUserByUsername(user.getUsername())).thenReturn(user);
        String expired = Jwts.builder().setSubject(user.getUsername())
                .setExpiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256)
                .compact();
        String noExpiration = Jwts.builder().setSubject(user.getUsername())
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256)
                .compact();

        for (String token : new String[] {"not.a.jwt", "", expired, noExpiration}) {
            SecurityContextHolder.clearContext();
            assertNull(request(token));
        }
    }

    @Test
    void removedAccountCannotAuthenticateAndRequestStillReachesSecurityChain() throws Exception {
        User user = activeUser();
        when(users.loadUserByUsername(user.getUsername()))
                .thenThrow(new UsernameNotFoundException("Account no longer exists"));

        assertNull(request(jwtService.generateToken(user)));
    }

    private Authentication request(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/announcements");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertTrue(response.getStatus() < 500);
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static User activeUser() {
        return User.builder().email("student@example.test").userType("STUDENT").status("ACTIVE").build();
    }
}
