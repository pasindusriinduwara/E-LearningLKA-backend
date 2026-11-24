package lk.tutionlms.backend.notification;

import lk.tutionlms.backend.identity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<NotificationPageResponse> list(@AuthenticationPrincipal User user,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return privateResponse(notificationService.list(recipientId(user), page, size));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount(@AuthenticationPrincipal User user) {
        return privateResponse(new UnreadCountResponse(notificationService.unreadCount(recipientId(user))));
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<NotificationResponse> markRead(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        return privateResponse(notificationService.markRead(recipientId(user), id));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<UnreadCountResponse> markAllRead(@AuthenticationPrincipal User user) {
        return privateResponse(new UnreadCountResponse(notificationService.markAllRead(recipientId(user))));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> invalidParameter(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).cacheControl(CacheControl.noStore())
                .body(Map.of("status", 400, "message", "Invalid notification ID or pagination value"));
    }

    private static UUID recipientId(User user) {
        if (user == null || user.getId() == null || !user.isEnabled()) {
            throw new AccessDeniedException("An active student or teacher account is required");
        }
        return user.getId();
    }

    private static <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
