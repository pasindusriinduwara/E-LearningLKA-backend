package lk.tutionlms.backend.identity.auth;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lk.tutionlms.backend.security.AuthRateLimiter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthenticationService service;
    private final AuthRateLimiter rateLimiter;

    public AuthController(AuthenticationService service, AuthRateLimiter rateLimiter) {
        this.service = service;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        rateLimiter.check(true, servletRequest.getRemoteAddr(), request.getEmail());
        return ResponseEntity.ok(service.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestBody AuthenticationRequest request, HttpServletRequest servletRequest) {
        rateLimiter.check(false, servletRequest.getRemoteAddr(), request.getEmail());
        return ResponseEntity.ok(service.authenticate(request));
    }
}
