package lk.tutionlms.backend.teacher;

import lk.tutionlms.backend.academic.SubjectRepository;
import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.communication.Announcement;
import lk.tutionlms.backend.communication.AnnouncementRepository;
import lk.tutionlms.backend.content.LearningMaterial;
import lk.tutionlms.backend.content.MaterialRepository;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.identity.*;
import lk.tutionlms.backend.notification.NotificationService;
import lk.tutionlms.backend.scheduling.*;
import lk.tutionlms.backend.teacher.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TeacherNotificationTests {
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final BatchRepository batches = mock(BatchRepository.class);
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final MaterialRepository materials = mock(MaterialRepository.class);
    private final AnnouncementRepository announcements = mock(AnnouncementRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final TeacherService service = new TeacherService(teachers, mock(UserRepository.class), batches,
            mock(EnrollmentRepository.class), schedules, materials, announcements, mock(SubjectRepository.class), notifications);
    private final User user = new User();
    private final Teacher teacher = new Teacher();
    private final Batch batch = new Batch();
    private final ScheduleItem schedule = ScheduleItem.builder().title("Physics revision")
            .dayOfWeek(DayOfWeek.MONDAY).effectiveDate(LocalDate.of(2026, 11, 23))
            .startTime(LocalTime.of(9, 0)).endTime(LocalTime.of(10, 0)).location("Room A")
            .mode(DeliveryMode.IN_PERSON).recurrence(RecurrenceType.WEEKLY).build();

    @BeforeEach
    void setup() {
        user.setId(UUID.randomUUID()); user.setUserType("TEACHER");
        teacher.setId(UUID.randomUUID()); batch.setId(UUID.randomUUID()); batch.setTeacherId(teacher.getId());
        schedule.setId(UUID.randomUUID()); schedule.setBatchId(batch.getId());
        when(teachers.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(batches.existsByIdAndTeacherIdAndDeletedFalse(batch.getId(), teacher.getId())).thenReturn(true);
        when(batches.findById(batch.getId())).thenReturn(Optional.of(batch));
        when(schedules.findById(schedule.getId())).thenReturn(Optional.of(schedule));
        when(schedules.save(any(ScheduleItem.class))).thenAnswer(call -> call.getArgument(0));
        when(materials.save(any(LearningMaterial.class))).thenAnswer(call -> call.getArgument(0));
        when(announcements.save(any(Announcement.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void classMaterialNotifiesOnlyTheOwnedBatch() {
        service.createMaterial(user, new CreateMaterialRequest(batch.getId(), "Lesson notes", "Physics", "PDF",
                "Now", "1 MB", "https://example.com/notes.pdf"));
        verify(notifications).notifyBatchStudents(eq(batch.getId()), eq("MATERIAL_ADDED"),
                eq("New learning material"), eq("Lesson notes was added to your class."), eq("/materials"));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void batchAnnouncementNotifiesEnrolledStudentsWithItsClassroomTab() {
        service.createAnnouncement(user, new CreateAnnouncementRequest(batch.getId(), "Bring calculators",
                "Private announcement body", "info", "Now"));
        verify(notifications).notifyBatchStudents(eq(batch.getId()), eq("ANNOUNCEMENT_CREATED"),
                eq("New class announcement"), eq("Bring calculators"),
                eq("/classes/" + batch.getId() + "?tab=announcements"));
    }

    @Test
    void globalAnnouncementDoesNotNotifyStudentsOutsideExistingVisibilityPolicy() {
        service.createAnnouncement(user, new CreateAnnouncementRequest(null, "Global", "Content", "info", "Now"));
        verifyNoInteractions(notifications);
    }

    @Test
    void newScheduleNotifiesEnrolledStudents() {
        service.createSchedule(user, new CreateScheduleRequest(batch.getId(), "Physics revision", "2026-11-23",
                "09:00", "10:00", "Room A", "IN_PERSON", "WEEKLY"));
        verify(notifications).notifyBatchStudents(eq(batch.getId()), eq("SCHEDULE_CREATED"), anyString(),
                eq("Physics revision was added to your class schedule."), eq("/schedule"));
    }

    @Test
    void savingUnchangedScheduleDoesNotNotifyStudentsAgain() {
        service.updateSchedule(user, schedule.getId(), new UpdateScheduleRequest(batch.getId(), "Physics revision",
                "2026-11-23", "09:00", "10:00", "Room A", "IN_PERSON", "WEEKLY"));
        verifyNoInteractions(notifications);
    }

    @Test
    void movingSessionBetweenOwnedBatchesNotifiesBothAffectedClasses() {
        Batch destination = new Batch(); destination.setId(UUID.randomUUID()); destination.setTeacherId(teacher.getId());
        when(batches.findById(destination.getId())).thenReturn(Optional.of(destination));
        when(batches.existsByIdAndTeacherIdAndDeletedFalse(destination.getId(), teacher.getId())).thenReturn(true);
        service.updateSchedule(user, schedule.getId(), new UpdateScheduleRequest(destination.getId(), "Physics revision",
                "2026-11-23", "10:00", "11:00", "Room B", "IN_PERSON", "WEEKLY"));
        verify(notifications).notifyBatchStudents(eq(batch.getId()), eq("SCHEDULE_CANCELLED"), anyString(), anyString(), eq("/schedule"));
        verify(notifications).notifyBatchStudents(eq(destination.getId()), eq("SCHEDULE_UPDATED"), anyString(), anyString(), eq("/schedule"));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void cancellationNotifiesOnceAndRepeatedDeletionIsRejected() {
        service.deleteSchedule(user, schedule.getId());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.deleteSchedule(user, schedule.getId()));
        verify(notifications, times(1)).notifyBatchStudents(eq(batch.getId()), eq("SCHEDULE_CANCELLED"),
                anyString(), anyString(), eq("/schedule"));
    }
}
