package lk.tutionlms.backend.identity.auth;

import lk.tutionlms.backend.identity.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class PasswordServiceTests {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final PasswordService service = new PasswordService(users,tokens,encoder,events);
    private final String raw = "A".repeat(43);
    private User account() {
        User user = User.builder().email("owner@example.test").password("old-hash").userType("STUDENT").build();
        user.setId(UUID.randomUUID());
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        return user;
    }
    private PasswordResetToken token(User user) {
        PasswordResetToken token = new PasswordResetToken(); token.setUserId(user.getId());
        token.setTokenHash(PasswordService.hash(raw)); token.setExpiresAt(Instant.now().plusSeconds(1800));
        when(tokens.findUserIdByTokenHash(token.getTokenHash())).thenReturn(Optional.of(user.getId()));
        when(tokens.findByHashForUpdate(token.getTokenHash())).thenReturn(Optional.of(token));
        return token;
    }
    @Test void unknownAndDisabledAccountsDoNotIssueTokens() {
        when(users.findByEmailForUpdate(anyString())).thenReturn(Optional.empty());
        service.requestReset("unknown@example.test");
        User user = account(); user.setStatus("SUSPENDED");
        when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        service.requestReset(user.getEmail()); verifyNoInteractions(tokens,events);
    }
    @Test void issuanceStoresOnlyAHashAndSendsARandomTokenAfterTransaction() {
        User user = account(); when(users.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        service.requestReset(" OWNER@example.test ");
        ArgumentCaptor<PasswordResetToken> stored = ArgumentCaptor.forClass(PasswordResetToken.class);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(tokens).save(stored.capture()); verify(events).publishEvent(event.capture());
        PasswordResetMail mail = (PasswordResetMail) event.getValue();
        assertEquals(43,mail.token().length()); assertEquals(PasswordService.hash(mail.token()),stored.getValue().getTokenHash());
        assertNotEquals(mail.token(),stored.getValue().getTokenHash());
        assertTrue(stored.getValue().getExpiresAt().isAfter(Instant.now().plusSeconds(1700)));
        assertFalse(mail.toString().contains(mail.token()));
        verify(tokens,never()).consumeForUser(any(),any());
    }
    @Test void resetChangesCredentialsAndConsumesAllLinks() {
        User user = account(); token(user); when(encoder.encode("new-password")).thenReturn("new-hash");
        service.reset(raw,"new-password");
        assertEquals("new-hash",user.getPassword()); assertEquals(1,user.getCredentialVersion());
        verify(tokens).consumeForUser(eq(user.getId()),any()); verify(users).save(user);
    }
    @Test void invalidExpiredConsumedAndStaleLinksDoNotChangeCredentials() {
        User user = account(); PasswordResetToken token = token(user);
        assertThrows(IllegalArgumentException.class,()->service.reset("invalid","new-password"));
        token.setExpiresAt(Instant.now().minusSeconds(1)); assertThrows(IllegalArgumentException.class,()->service.reset(raw,"new-password"));
        token.setExpiresAt(Instant.now().plusSeconds(60)); token.setConsumedAt(Instant.now()); assertThrows(IllegalArgumentException.class,()->service.reset(raw,"new-password"));
        token.setConsumedAt(null); user.setCredentialVersion(1); assertThrows(IllegalArgumentException.class,()->service.reset(raw,"new-password"));
        verify(encoder,never()).encode(any()); assertEquals("old-hash",user.getPassword());
    }
    @Test void passwordChangeRequiresTheCurrentPasswordAndRevokesSessions() {
        User user = account();
        assertThrows(IllegalArgumentException.class,()->service.change(user.getId(),"wrong","new-password"));
        when(encoder.matches("current-password","old-hash")).thenReturn(true);
        when(encoder.encode("new-password")).thenReturn("new-hash");
        service.change(user.getId(),"current-password","new-password");
        assertEquals(1,user.getCredentialVersion()); assertEquals("new-hash",user.getPassword());
        verify(tokens).consumeForUser(eq(user.getId()),any());
    }
    @Test void bcryptByteLimitIsEnforcedForMultibytePasswords() {
        assertDoesNotThrow(()->PasswordService.validatePassword("é".repeat(36)));
        assertThrows(IllegalArgumentException.class,()->PasswordService.validatePassword("é".repeat(37)));
        assertThrows(IllegalArgumentException.class,()->PasswordService.validatePassword("short"));
    }
}
