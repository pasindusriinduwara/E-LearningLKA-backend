package lk.tutionlms.backend.identity.auth;

public record PasswordResetMail(String email, String token) {
    @Override public String toString() { return "PasswordResetMail[redacted]"; }
}
