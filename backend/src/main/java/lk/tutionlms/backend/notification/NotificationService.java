package lk.tutionlms.backend.notification;

import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.identity.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional
public class NotificationService {
    private final NotificationRepository notifications;
    private final UserRepository users;
    private final Clock clock;

    @Autowired
    public NotificationService(NotificationRepository notifications, UserRepository users) {
        this(notifications, users, Clock.systemUTC());
    }

    NotificationService(NotificationRepository notifications, UserRepository users, Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse list(UUID recipientId, int page, int size) {
        if (page < 0 || size < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be nonnegative and size must be positive");
        }
        int cappedSize = Math.min(size, 50);
        var result = notifications.findByRecipientId(recipientId,
                PageRequest.of(page, cappedSize, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new NotificationPageResponse(result.getContent().stream().map(NotificationResponse::from).toList(),
                unreadCount(recipientId), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID recipientId) {
        return notifications.countByRecipientIdAndReadAtIsNull(recipientId);
    }

    public NotificationResponse markRead(UUID recipientId, UUID notificationId) {
        // Conditional update preserves the original timestamp on retries and concurrent requests.
        notifications.markRead(notificationId, recipientId, clock.instant());
        return notifications.findByIdAndRecipientId(notificationId, recipientId)
                .map(NotificationResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    public long markAllRead(UUID recipientId) {
        notifications.markAllRead(recipientId, clock.instant());
        return unreadCount(recipientId);
    }

    /** Internal only: recipients are selected by authorized business operations, never by HTTP input. */
    public void notifyUser(UUID recipientId, String type, String title, String message, String link) {
        validateContent(type, title, message, link);
        if (recipientId == null) return;
        users.findById(recipientId).filter(this::isEligibleUser).ifPresent(user ->
                saveRecipients(List.of(user.getId()), type, title, message, link));
    }

    public void notifyBatchStudents(UUID batchId, String type, String title, String message, String link) {
        validateContent(type, title, message, link);
        if (batchId == null) return;
        saveRecipients(notifications.findActiveStudentRecipientIds(batchId), type, title, message, link);
    }

    public void notifyBatchTeacher(UUID batchId, String type, String title, String message, String link) {
        validateContent(type, title, message, link);
        if (batchId == null) return;
        notifications.findActiveTeacherRecipientId(batchId)
                .ifPresent(id -> saveRecipients(List.of(id), type, title, message, link));
    }

    private boolean isEligibleUser(User user) {
        return user.isEnabled() && ("STUDENT".equals(user.getUserType()) || "TEACHER".equals(user.getUserType()));
    }

    private void saveRecipients(Collection<UUID> recipientIds, String type, String title, String message, String link) {
        Instant createdAt = clock.instant();
        List<Notification> entries = recipientIds.stream().filter(Objects::nonNull).distinct().map(id -> {
            Notification notification = new Notification();
            notification.setRecipientId(id);
            notification.setType(type);
            notification.setTitle(truncate(title.trim(), 160));
            notification.setMessage(truncate(message.trim(), 1000));
            notification.setLink(link);
            notification.setCreatedAt(createdAt);
            return notification;
        }).toList();
        if (!entries.isEmpty()) notifications.saveAll(entries);
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) return value;
        int end = Character.isHighSurrogate(value.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return value.substring(0, end);
    }

    private static void validateContent(String type, String title, String message, String link) {
        if (type == null || !type.matches("[A-Z][A-Z0-9_]{0,59}") || title == null || title.isBlank()
                || message == null || message.isBlank()) {
            throw new IllegalArgumentException("Notification type, title and message are required");
        }
        if (link == null) return;
        try {
            URI uri = URI.create(link);
            String path = uri.getPath();
            String rawPath = uri.getRawPath() == null ? "" : uri.getRawPath().toLowerCase(Locale.ROOT);
            if (link.length() > 512 || !link.startsWith("/") || link.startsWith("//") || uri.isAbsolute()
                    || uri.getRawAuthority() != null || path == null || path.startsWith("//")
                    || path.contains("\\") || path.chars().anyMatch(Character::isISOControl)
                    || rawPath.contains("%2f") || rawPath.contains("%5c")) {
                throw new IllegalArgumentException("Notification links must be safe relative application paths");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Notification links must be safe relative application paths");
        }
    }
}
