package lk.tutionlms.backend.identity.auth;

import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PasswordService {
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public void requestReset(String email) {
        var found = users.findByEmailForUpdate(email.trim().toLowerCase(Locale.ROOT));
        if (found.isEmpty() || !found.get().isEnabled()) return;
        User user = found.get();
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId()); token.setTokenHash(hash(rawToken));
        token.setCredentialVersion(user.getCredentialVersion());
        token.setExpiresAt(Instant.now().plusSeconds(1800));
        tokens.save(token);
        events.publishEvent(new PasswordResetMail(user.getEmail(), rawToken));
    }

    @Transactional
    public void reset(String rawToken, String newPassword) {
        validatePassword(newPassword);
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) throw invalidToken();
        String hash = hash(rawToken);
        // Always lock the user before the token to serialize issuance, reset and change.
        UUID userId = tokens.findUserIdByTokenHash(hash).orElseThrow(PasswordService::invalidToken);
        User user = users.findByIdForUpdate(userId).orElseThrow(PasswordService::invalidToken);
        PasswordResetToken token = tokens.findByHashForUpdate(hash).orElseThrow(PasswordService::invalidToken);
        Instant now = Instant.now();
        if (!user.isEnabled() || token.getConsumedAt() != null || !token.getExpiresAt().isAfter(now)
                || token.getCredentialVersion() != user.getCredentialVersion()) throw invalidToken();
        user.setPassword(encoder.encode(newPassword));
        user.setCredentialVersion(Math.incrementExact(user.getCredentialVersion()));
        tokens.consumeForUser(user.getId(), now);
        users.save(user);
    }

    @Transactional
    public void change(UUID userId, String currentPassword, String newPassword) {
        validatePassword(newPassword);
        User user = users.findByIdForUpdate(userId).filter(User::isEnabled)
                .orElseThrow(() -> new IllegalArgumentException("Account is unavailable."));
        if (currentPassword == null || currentPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        if (encoder.matches(newPassword, user.getPassword())) throw new IllegalArgumentException("Choose a different new password.");
        user.setPassword(encoder.encode(newPassword));
        user.setCredentialVersion(Math.incrementExact(user.getCredentialVersion()));
        tokens.consumeForUser(userId, Instant.now());
        users.save(user);
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must contain at least 8 characters and at most 72 UTF-8 bytes.");
        }
    }

    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private static IllegalArgumentException invalidToken() { return new IllegalArgumentException("This reset link is invalid or expired. Request a new link."); }
}
