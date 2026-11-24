package lk.tutionlms.backend.notification;

import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.identity.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class NotificationServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-21T05:00:00Z");
    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private NotificationService service;

    @BeforeEach
    void setup() {
        reset(notifications, users);
        service = new NotificationService(notifications, users, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void listScopesRecipientCapsPageSizeAndUsesDeterministicNewestFirstOrder() {
        UUID userId = UUID.randomUUID();
        List<Notification> ownEntries = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> notification(userId)).toList();
        when(notifications.findByRecipientId(eq(userId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(ownEntries, PageRequest.of(2, 50), 120));
        when(notifications.countByRecipientIdAndReadAtIsNull(userId)).thenReturn(7L);

        NotificationPageResponse result = service.list(userId, 2, 500);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(notifications).findByRecipientId(eq(userId), page.capture());
        assertEquals(2, page.getValue().getPageNumber());
        assertEquals(50, page.getValue().getPageSize());
        assertEquals("createdAt: DESC,id: DESC", page.getValue().getSort().toString().replace(", ", ","));
        assertEquals(7, result.unreadCount());
        assertEquals(120, result.totalElements());
        assertEquals(3, result.totalPages());
        assertFalse(result.items().getFirst().read());
        assertEquals(NOW, result.items().getFirst().createdAt());
    }

    @Test
    void rejectsInvalidPaginationBeforeQueryingTheInbox() {
        UUID userId = UUID.randomUUID();
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.list(userId, -1, 20)).getStatusCode());
        assertThrows(ResponseStatusException.class, () -> service.list(userId, 0, 0));
        verifyNoInteractions(notifications);
    }

    @Test
    void foreignOrMissingNotificationsReturnNotFoundAfterOnlyScopedOperations() {
        UUID recipient = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(notifications.findByIdAndRecipientId(id, recipient)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.markRead(recipient, id));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(notifications).markRead(id, recipient, NOW);
        verify(notifications).findByIdAndRecipientId(id, recipient);
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void retryingReadPreservesTheFirstReadTimestamp() {
        UUID recipient = UUID.randomUUID();
        Notification entry = notification(recipient);
        Instant firstRead = NOW.minusSeconds(60);
        entry.setReadAt(firstRead);
        when(notifications.markRead(entry.getId(), recipient, NOW)).thenReturn(0);
        when(notifications.findByIdAndRecipientId(entry.getId(), recipient)).thenReturn(Optional.of(entry));

        NotificationResponse response = service.markRead(recipient, entry.getId());

        assertTrue(response.read());
        assertEquals(firstRead, response.readAt());
        verify(notifications, never()).save(any());
    }

    @Test
    void markAllReadUsesOnlyThePrincipalAndReturnsTheRemainingCount() {
        UUID recipient = UUID.randomUUID();
        when(notifications.countByRecipientIdAndReadAtIsNull(recipient)).thenReturn(1L);

        assertEquals(1, service.markAllRead(recipient));

        var order = inOrder(notifications);
        order.verify(notifications).markAllRead(recipient, NOW);
        order.verify(notifications).countByRecipientIdAndReadAtIsNull(recipient);
    }

    @Test
    void activeUserCreationStoresPlainTextWithUtcTimestampAndNoReadState() {
        User user = user("STUDENT");
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        service.notifyUser(user.getId(), "RESULT_PUBLISHED", "Results available", "View your assessment result", "/student/results");

        Notification saved = capturedEntries().getFirst();
        assertEquals(user.getId(), saved.getRecipientId());
        assertEquals("RESULT_PUBLISHED", saved.getType());
        assertEquals("/student/results", saved.getLink());
        assertEquals(NOW, saved.getCreatedAt());
        assertNull(saved.getReadAt());
    }

    @Test
    void suspendedDeletedMissingAndUnrelatedAccountsDoNotReceiveNotifications() {
        User suspended = user("STUDENT");
        suspended.setStatus("SUSPENDED");
        User deleted = user("TEACHER");
        deleted.setDeleted(true);
        User administrator = user("ADMIN");
        for (User user : List.of(suspended, deleted, administrator)) {
            when(users.findById(user.getId())).thenReturn(Optional.of(user));
            service.notifyUser(user.getId(), "NEW_MATERIAL", "New material", "A material was added", "/student/materials");
        }
        UUID missingId = UUID.randomUUID();
        when(users.findById(missingId)).thenReturn(Optional.empty());
        service.notifyUser(missingId, "NEW_MATERIAL", "New material", "A material was added", "/student/materials");
        verifyNoInteractions(notifications);
    }

    @Test
    void batchStudentRecipientsAreSelectedFromTheActiveAudienceAndDeduplicated() {
        UUID batchId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(notifications.findActiveStudentRecipientIds(batchId)).thenReturn(List.of(first, second, first));

        service.notifyBatchStudents(batchId, "NEW_ASSESSMENT", "Assessment added", "New assessment", "/student/assignments");

        assertEquals(List.of(first, second), capturedEntries().stream().map(Notification::getRecipientId).toList());
        verifyNoInteractions(users);
    }

    @Test
    void batchTeacherRecipientIsResolvedFromTheOwnedBatchRatherThanItsProfileId() {
        UUID batchId = UUID.randomUUID();
        UUID teacherUserId = UUID.randomUUID();
        when(notifications.findActiveTeacherRecipientId(batchId)).thenReturn(Optional.of(teacherUserId));

        service.notifyBatchTeacher(batchId, "NEW_SUBMISSION", "Submission received", "An assessment was submitted", "/teacher/results");

        assertEquals(teacherUserId, capturedEntries().getFirst().getRecipientId());
    }

    @Test
    void emptyBatchAudienceDoesNotWriteEmptyRows() {
        UUID batchId = UUID.randomUUID();
        when(notifications.findActiveStudentRecipientIds(batchId)).thenReturn(List.of());
        when(notifications.findActiveTeacherRecipientId(batchId)).thenReturn(Optional.empty());
        service.notifyBatchStudents(batchId, "NEW_MATERIAL", "New material", "New material", "/student/materials");
        service.notifyBatchTeacher(batchId, "NEW_SUBMISSION", "New submission", "New submission", "/teacher/results");
        verify(notifications, never()).saveAll(any());
    }

    @Test
    void unsafeNavigationLinksAreRejectedBeforeWritingAnyRecipient() {
        for (String link : List.of("https://example.test", "//example.test", "javascript:alert(1)",
                "/%2fexample.test", "/%5cexample.test", "/student/%0d%0aresults", "/\\example.test")) {
            assertThrows(IllegalArgumentException.class, () -> service.notifyUser(UUID.randomUUID(),
                    "NEW_MATERIAL", "New material", "New material", link), link);
        }
        verifyNoInteractions(users, notifications);
    }

    @Test
    void longValidBusinessContentIsTruncatedWithoutRollingBackTheOperation() {
        User teacher = user("TEACHER");
        when(users.findById(teacher.getId())).thenReturn(Optional.of(teacher));

        service.notifyUser(teacher.getId(), "NEW_ASSESSMENT", "T".repeat(170), "M".repeat(1100), null);

        Notification saved = capturedEntries().getFirst();
        assertEquals(160, saved.getTitle().length());
        assertEquals(1000, saved.getMessage().length());
        assertNull(saved.getLink());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private List<Notification> capturedEntries() {
        ArgumentCaptor<List<Notification>> captor = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(notifications).saveAll(captor.capture());
        return captor.getValue();
    }

    private static User user(String role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUserType(role);
        user.setStatus("ACTIVE");
        return user;
    }

    private static Notification notification(UUID recipient) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipientId(recipient);
        notification.setType("RESULT_PUBLISHED");
        notification.setTitle("Result available");
        notification.setMessage("View your result");
        notification.setCreatedAt(NOW);
        return notification;
    }
}
