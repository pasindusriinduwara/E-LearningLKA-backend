package lk.tutionlms.backend.identity.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lk.tutionlms.backend.security.AuthRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordRecoveryController {
    private final PasswordService passwords;
    private final AuthRateLimiter limiter;
    public record ForgotRequest(@NotBlank @Email @Size(max=150) String email) {}
    public record ResetRequest(@NotBlank @Size(max=128) String token, @NotBlank @Size(min=8,max=72) String password) {
        @Override public String toString() { return "ResetRequest[redacted]"; }
    }
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String,String>> forgot(@Valid @RequestBody ForgotRequest request, HttpServletRequest http) {
        limiter.check(true, "reset:" + http.getRemoteAddr(), "reset:" + request.email());
        passwords.requestReset(request.email());
        return ResponseEntity.accepted().body(Map.of("message", "If an active account matches that email, a reset link will be sent. Check your inbox and spam folder."));
    }
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String,String>> reset(@Valid @RequestBody ResetRequest request, HttpServletRequest http) {
        limiter.check(false, "reset-submit:" + http.getRemoteAddr(), "reset-submit:" + PasswordService.hash(request.token()));
        passwords.reset(request.token(), request.password());
        return ResponseEntity.ok(Map.of("message", "Password reset. Sign in with your new password."));
    }
}
