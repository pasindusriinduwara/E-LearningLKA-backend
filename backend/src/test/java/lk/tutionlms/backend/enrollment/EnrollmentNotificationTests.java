package lk.tutionlms.backend.enrollment;

import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.identity.*;
import lk.tutionlms.backend.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EnrollmentNotificationTests {
    private final EnrollmentRequestRepository requests = mock(EnrollmentRequestRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final BatchRepository batches = mock(BatchRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final EnrollmentService service = new EnrollmentService(requests, enrollments, users, students,
            batches, teachers, notifications);
    private final User studentUser = new User(), teacherUser = new User();
    private final Student student = new Student();
    private final Teacher teacher = new Teacher();
    private final Batch batch = new Batch();

    @BeforeEach
    void setup() {
        studentUser.setId(UUID.randomUUID()); teacherUser.setId(UUID.randomUUID());
        studentUser.setUserType("STUDENT"); teacherUser.setUserType("TEACHER");
        student.setId(UUID.randomUUID()); student.setUserId(studentUser.getId()); teacher.setId(UUID.randomUUID());
        batch.setId(UUID.randomUUID()); batch.setTeacherId(teacher.getId());
        batch.setActive(true);
        when(users.findByEmail("student@example.com")).thenReturn(Optional.of(studentUser));
        when(users.findByEmail("teacher@example.com")).thenReturn(Optional.of(teacherUser));
        when(students.findByUserId(studentUser.getId())).thenReturn(Optional.of(student));
        when(students.findById(student.getId())).thenReturn(Optional.of(student));
        when(students.findByIdForUpdate(student.getId())).thenReturn(Optional.of(student));
        when(users.findById(studentUser.getId())).thenReturn(Optional.of(studentUser));
        when(teachers.findByUserId(teacherUser.getId())).thenReturn(Optional.of(teacher));
        when(batches.existsById(batch.getId())).thenReturn(true);
        when(batches.findById(batch.getId())).thenReturn(Optional.of(batch));
    }

    private EnrollmentRequest request(EnrollmentStatus status) {
        EnrollmentRequest request = new EnrollmentRequest();
        request.setId(UUID.randomUUID()); request.setBatchId(batch.getId()); request.setStudentId(student.getId());
        request.setStatus(status);
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(requests.findIdentityById(request.getId())).thenReturn(Optional.of(
                new EnrollmentRequestIdentity(request.getId(), request.getStudentId(), request.getBatchId())));
        when(requests.findTopByStudentIdAndBatchIdAndIsDeletedFalseOrderByCreatedAtDesc(student.getId(), batch.getId()))
                .thenReturn(Optional.of(request));
        return request;
    }

    @Test
    void aNewEnrollmentRequestNotifiesOnlyTheClassTeacher() {
        service.createRequest("student@example.com", batch.getId().toString());
        verify(requests).save(any(EnrollmentRequest.class));
        verify(notifications).notifyBatchTeacher(eq(batch.getId()), eq("ENROLLMENT_REQUESTED"),
                eq("New enrollment request"), anyString(), eq("/teacher/enrollments"));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void pendingRequestRetryDoesNotCreateAnotherNotification() {
        request(EnrollmentStatus.PENDING);
        assertThrows(IllegalStateException.class, () -> service.createRequest("student@example.com", batch.getId().toString()));
        verifyNoInteractions(notifications);
    }

    @Test
    void resubmittingARejectedRequestNotifiesTheTeacherAgain() {
        EnrollmentRequest request = request(EnrollmentStatus.REJECTED);
        service.createRequest("student@example.com", batch.getId().toString());
        assertEquals(EnrollmentStatus.PENDING, request.getStatus());
        verify(notifications).notifyBatchTeacher(eq(batch.getId()), eq("ENROLLMENT_REQUESTED"),
                anyString(), anyString(), eq("/teacher/enrollments"));
    }

    @Test
    void approvalNotifiesTheStudentAndDuplicateApprovalDoesNotRepeatIt() {
        EnrollmentRequest request = request(EnrollmentStatus.PENDING);
        service.approveRequest("teacher@example.com", request.getId());
        assertThrows(ResponseStatusException.class, () -> service.approveRequest("teacher@example.com", request.getId()));
        verify(notifications, times(1)).notifyUser(eq(studentUser.getId()), eq("ENROLLMENT_APPROVED"),
                eq("Enrollment approved"), anyString(), eq("/classes/" + batch.getId()));
        verifyNoMoreInteractions(notifications);
        verify(requests, times(2)).findByIdForUpdate(request.getId());
        verify(requests, never()).findById(any());
    }

    @Test
    void rejectionNotifiesTheStudentWithTheirClassEnrollmentLink() {
        EnrollmentRequest request = request(EnrollmentStatus.PENDING);
        service.rejectRequest("teacher@example.com", request.getId());
        verify(notifications).notifyUser(eq(studentUser.getId()), eq("ENROLLMENT_REJECTED"),
                eq("Enrollment request declined"), anyString(), eq("/enrollment/" + batch.getId()));
        verify(requests).findByIdForUpdate(request.getId());
        verify(requests, never()).findById(any());
    }

    @Test
    void waitingRejectionObservesApprovalAndCannotSendAContradictoryDecision() {
        EnrollmentRequest request = request(EnrollmentStatus.PENDING);
        service.approveRequest("teacher@example.com", request.getId());
        assertThrows(ResponseStatusException.class, () -> service.rejectRequest("teacher@example.com", request.getId()));
        assertEquals(EnrollmentStatus.APPROVED, request.getStatus());
        verify(requests, times(2)).findByIdForUpdate(request.getId());
        verify(requests, times(1)).save(request);
        verify(notifications, times(1)).notifyUser(eq(studentUser.getId()), eq("ENROLLMENT_APPROVED"),
                anyString(), anyString(), anyString());
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void internalApprovalOverloadAlsoUsesTheLockedDecisionPath() {
        EnrollmentRequest request = request(EnrollmentStatus.PENDING);
        service.approveRequest(request.getId());
        verify(requests).findByIdForUpdate(request.getId());
        verify(requests, never()).findById(any());
        assertThrows(ResponseStatusException.class, () -> service.rejectRequest("teacher@example.com", request.getId()));
        verify(notifications, times(1)).notifyUser(eq(studentUser.getId()), eq("ENROLLMENT_APPROVED"),
                anyString(), anyString(), anyString());
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void anotherTeacherCannotCauseEnrollmentNotifications() {
        EnrollmentRequest request = request(EnrollmentStatus.PENDING);
        batch.setTeacherId(UUID.randomUUID());
        assertThrows(AccessDeniedException.class, () -> service.approveRequest("teacher@example.com", request.getId()));
        verifyNoInteractions(notifications);
    }
}
