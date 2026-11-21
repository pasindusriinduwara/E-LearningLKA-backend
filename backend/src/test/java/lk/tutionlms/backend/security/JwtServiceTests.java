package lk.tutionlms.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtServiceTests {
    private static final String SECRET = Base64.getEncoder().encodeToString(
            "test-only-signing-key-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
    private final JwtService jwtService = new JwtService(SECRET, 420_000);

    @Test
    void generatedTokenUsesConfiguredLifetimeAndSubject() {
        UserDetails user = activeUser("student@example.test");
        String token = jwtService.generateToken(user);

        assertEquals(user.getUsername(), jwtService.extractUsername(token));
        Date issuedAt = jwtService.extractClaim(token, Claims::getIssuedAt);
        Date expiresAt = jwtService.extractClaim(token, Claims::getExpiration);
        assertEquals(420_000, expiresAt.getTime() - issuedAt.getTime());
        assertTrue(jwtService.isTokenValid(token, user));
    }

    @Test
    void nonPositiveConfiguredLifetimeIsRejectedAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService(SECRET, 0));
        assertThrows(IllegalArgumentException.class, () -> new JwtService(SECRET, -1));
    }

    @Test
    void everyAccountFlagMustRemainValidEvenWithAValidSignedToken() {
        UserDetails user = activeUser("student@example.test");
        String token = jwtService.generateToken(user);

        when(user.isEnabled()).thenReturn(false);
        assertFalse(jwtService.isTokenValid(token, user), "Disabled accounts must lose JWT access");
        when(user.isEnabled()).thenReturn(true);
        when(user.isAccountNonLocked()).thenReturn(false);
        assertFalse(jwtService.isTokenValid(token, user), "Locked accounts must lose JWT access");
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isAccountNonExpired()).thenReturn(false);
        assertFalse(jwtService.isTokenValid(token, user), "Expired accounts must lose JWT access");
        when(user.isAccountNonExpired()).thenReturn(true);
        when(user.isCredentialsNonExpired()).thenReturn(false);
        assertFalse(jwtService.isTokenValid(token, user), "Expired credentials must lose JWT access");
    }

    @Test
    void tokenCannotAuthenticateADifferentAccount() {
        String token = jwtService.generateToken(activeUser("owner@example.test"));
        assertFalse(jwtService.isTokenValid(token, activeUser("other@example.test")));
    }

    @Test
    void malformedExpiredUnsignedOrNonExpiringTokensAreInvalidWithoutThrowing() {
        UserDetails user = activeUser("student@example.test");
        String expired = Jwts.builder().setSubject(user.getUsername())
                .setExpiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256)
                .compact();
        String noExpiration = Jwts.builder().setSubject(user.getUsername())
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256)
                .compact();
        String unsigned = Jwts.builder().setSubject(user.getUsername())
                .setExpiration(Date.from(Instant.now().plusSeconds(60))).compact();

        for (String token : new String[] {"not.a.jwt", "", expired, noExpiration, unsigned}) {
            assertFalse(assertDoesNotThrow(() -> jwtService.isTokenValid(token, user)));
        }
    }

    private static UserDetails activeUser(String email) {
        UserDetails user = mock(UserDetails.class);
        when(user.getUsername()).thenReturn(email);
        when(user.isEnabled()).thenReturn(true);
        when(user.isAccountNonLocked()).thenReturn(true);
        when(user.isAccountNonExpired()).thenReturn(true);
        when(user.isCredentialsNonExpired()).thenReturn(true);
        return user;
    }
}
