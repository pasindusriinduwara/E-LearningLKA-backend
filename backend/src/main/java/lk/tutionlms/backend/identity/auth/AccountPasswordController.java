package lk.tutionlms.backend.identity.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.security.AuthRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountPasswordController {
    private final PasswordService passwords;
    private final AuthRateLimiter limiter;
    public record ChangeRequest(@NotBlank @Size(max=72) String currentPassword, @NotBlank @Size(min=8,max=72) String password) {
        @Override public String toString() { return "ChangeRequest[redacted]"; }
    }
    @PostMapping("/password")
    public ResponseEntity<Map<String,String>> change(@AuthenticationPrincipal User user, @Valid @RequestBody ChangeRequest request, HttpServletRequest http) {
        limiter.check(false, "change:" + http.getRemoteAddr(), "change:" + user.getEmail());
        passwords.change(user.getId(), request.currentPassword(), request.password());
        return ResponseEntity.ok(Map.of("message", "Password changed. Sign in again on your devices."));
    }
}
