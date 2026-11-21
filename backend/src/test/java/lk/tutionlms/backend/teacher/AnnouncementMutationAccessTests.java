package lk.tutionlms.backend.teacher;

import lk.tutionlms.backend.academic.SubjectRepository;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.communication.Announcement;
import lk.tutionlms.backend.communication.AnnouncementRepository;
import lk.tutionlms.backend.content.MaterialRepository;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.identity.Teacher;
import lk.tutionlms.backend.identity.TeacherRepository;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.identity.UserRepository;
import lk.tutionlms.backend.scheduling.ScheduleRepository;
import lk.tutionlms.backend.teacher.dto.CreateAnnouncementRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AnnouncementMutationAccessTests {
    private final AnnouncementRepository announcements = mock(AnnouncementRepository.class);
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final BatchRepository batches = mock(BatchRepository.class);
    private final TeacherService service = new TeacherService(teachers, mock(UserRepository.class), batches,
            mock(EnrollmentRepository.class), mock(ScheduleRepository.class), mock(MaterialRepository.class),
            announcements, mock(SubjectRepository.class));

    @Test
    void creationRecordsTheAuthenticatedAuthor() {
        User author = principal("TEACHER");
        when(announcements.save(any())).thenAnswer(call -> call.getArgument(0));
        Announcement created = service.createAnnouncement(author, new CreateAnnouncementRequest(null,
                "Global", "Announcement text", "info", "Now"));
        assertEquals(author.getId(), created.getCreatedBy());
    }

    @Test
    void anotherTeacherCannotDeleteAGlobalAnnouncement() {
        User author = principal("TEACHER");
        Announcement announcement = announcement(null, author.getId());
        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
                () -> service.deleteAnnouncement(principal("TEACHER"), announcement.getId()));
        assertEquals(403, denied.getStatusCode().value());
        assertFalse(announcement.isDeleted());
        verify(announcements, never()).save(any());
    }

    @Test
    void authorCanDeleteTheirOwnGlobalAnnouncement() {
        User author = principal("TEACHER");
        Announcement announcement = announcement(null, author.getId());
        service.deleteAnnouncement(author, announcement.getId());
        assertTrue(announcement.isDeleted());
        verify(announcements).save(announcement);
    }

    @Test
    void legacyGlobalWithNoAuthorIsReservedForAdministrator() {
        Announcement announcement = announcement(null, null);
        assertThrows(ResponseStatusException.class,
                () -> service.deleteAnnouncement(principal("TEACHER"), announcement.getId()));
        assertDoesNotThrow(() -> service.deleteAnnouncement(principal("ADMIN"), announcement.getId()));
        assertTrue(announcement.isDeleted());
    }

    @Test
    void authorCannotBypassBatchOwnership() {
        User author = principal("TEACHER");
        Teacher teacher = new Teacher(); teacher.setId(UUID.randomUUID());
        when(teachers.findByUserId(author.getId())).thenReturn(Optional.of(teacher));
        Announcement announcement = announcement(UUID.randomUUID(), author.getId());
        assertThrows(ResponseStatusException.class, () -> service.deleteAnnouncement(author, announcement.getId()));
        assertFalse(announcement.isDeleted());
        verify(announcements, never()).save(any());
    }

    private Announcement announcement(UUID batchId, UUID author) {
        Announcement announcement = Announcement.builder().batchId(batchId).createdBy(author).title("Title").build();
        announcement.setId(UUID.randomUUID());
        when(announcements.findById(announcement.getId())).thenReturn(Optional.of(announcement));
        return announcement;
    }

    private static User principal(String role) {
        User user = new User(); user.setId(UUID.randomUUID()); user.setUserType(role); return user;
    }
}
