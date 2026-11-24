package lk.tutionlms.backend.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(UUID id, String type, String title, String message, String link,
                                   Instant createdAt, boolean read, Instant readAt) {
    static NotificationResponse from(Notification notification) {
        return new NotificationResponse(notification.getId(), notification.getType(), notification.getTitle(),
                notification.getMessage(), notification.getLink(), notification.getCreatedAt(),
                notification.getReadAt() != null, notification.getReadAt());
    }
}
