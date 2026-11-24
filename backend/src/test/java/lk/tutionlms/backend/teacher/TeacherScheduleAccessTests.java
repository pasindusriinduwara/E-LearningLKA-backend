package lk.tutionlms.backend.teacher;

import lk.tutionlms.backend.academic.SubjectRepository;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.communication.AnnouncementRepository;
import lk.tutionlms.backend.content.MaterialRepository;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.identity.*;
import lk.tutionlms.backend.notification.NotificationService;
import lk.tutionlms.backend.scheduling.ScheduleItem;
import lk.tutionlms.backend.scheduling.ScheduleRepository;
import lk.tutionlms.backend.teacher.dto.UpdateScheduleRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherScheduleAccessTests {
    @Mock private TeacherRepository teachers;
    @Mock private UserRepository users;
    @Mock private BatchRepository batches;
    @Mock private EnrollmentRepository enrollments;
    @Mock private ScheduleRepository schedules;
    @Mock private MaterialRepository materials;
    @Mock private AnnouncementRepository announcements;
    @Mock private SubjectRepository subjects;
    private TeacherService service;
    private User user;
    private Teacher teacher;
    private ScheduleItem victim;

    @BeforeEach
    void setup() {
        service = new TeacherService(teachers, users, batches, enrollments, schedules, materials, announcements, subjects,
                mock(NotificationService.class));
        user = new User();
        user.setId(UUID.randomUUID());
        teacher = new Teacher();
        teacher.setId(UUID.randomUUID());
        when(teachers.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        victim = ScheduleItem.builder().batchId(UUID.randomUUID()).title("Another teacher's class").build();
        victim.setId(UUID.randomUUID());
        when(schedules.findById(victim.getId())).thenReturn(Optional.of(victim));
    }

    @Test
    void updateChecksTheExistingSchedulesBatchBeforeMovingToAnOwnedBatch() {
        UUID ownBatchId = UUID.randomUUID();
        UpdateScheduleRequest request = new UpdateScheduleRequest(ownBatchId, "Overwritten", "2026-11-23",
                "09:00", "10:00", null, "ONLINE", "WEEKLY");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateSchedule(user, victim.getId(), request));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals("Another teacher's class", victim.getTitle());
        verify(batches).existsByIdAndTeacherIdAndDeletedFalse(victim.getBatchId(), teacher.getId());
        verify(batches, never()).existsByIdAndTeacherIdAndDeletedFalse(ownBatchId, teacher.getId());
        verify(schedules, never()).save(any());
    }

    @Test
    void deleteChecksTheExistingSchedulesBatchBeforeChangingDeletionFlag() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deleteSchedule(user, victim.getId()));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertFalse(victim.isDeleted());
        verify(batches).existsByIdAndTeacherIdAndDeletedFalse(victim.getBatchId(), teacher.getId());
        verify(schedules, never()).save(any());
    }
}
