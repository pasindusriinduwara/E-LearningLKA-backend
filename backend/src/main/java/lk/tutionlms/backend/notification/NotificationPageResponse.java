package lk.tutionlms.backend.notification;

import java.util.List;

public record NotificationPageResponse(List<NotificationResponse> items, long unreadCount, int page,
                                       int size, long totalElements, int totalPages) {
}
