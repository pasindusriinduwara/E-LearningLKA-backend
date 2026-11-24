package lk.tutionlms.backend.batch;

import lk.tutionlms.backend.academic.Subject;
import lk.tutionlms.backend.academic.SubjectRepository;
import lk.tutionlms.backend.content.MaterialRepository;
import lk.tutionlms.backend.enrollment.*;
import lk.tutionlms.backend.identity.*;
import lk.tutionlms.backend.scheduling.ScheduleRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PaymentPausedBatchTests {
    private final BatchRepository batches = mock(BatchRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final SubjectRepository subjects = mock(SubjectRepository.class);
    private final ScheduleRepository schedules = mock(ScheduleRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final EnrollmentRequestRepository requests = mock(EnrollmentRequestRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final MaterialRepository materials = mock(MaterialRepository.class);
    private final BatchService service = new BatchService(batches, users, teachers, subjects, schedules,
            enrollments, requests, students, materials);

    @Test
    void creatingAClassRequiresNoFeeAndUsesTheLegacyZeroDefault() {
        assertEquals(BigDecimal.ZERO, create(null).getMonthlyFee());
    }

    @Test
    void oldClientsCannotEnableBillingBySupplyingAFee() {
        assertEquals(BigDecimal.ZERO, create(new BigDecimal("3500")).getMonthlyFee());
    }

    @Test
    void anApprovedRequestWithoutActiveEnrollmentDoesNotExposeApprovedClassAccess() {
        User user = new User(); user.setId(UUID.randomUUID());
        Student student = new Student(); student.setId(UUID.randomUUID()); student.setUserId(user.getId());
        Batch batch = new Batch(); batch.setId(UUID.randomUUID()); batch.setActive(true);
        batch.setName("Physics"); batch.setExamYear("2026"); batch.setMonthlyFee(new BigDecimal("3500"));
        EnrollmentRequest request = new EnrollmentRequest(); request.setId(UUID.randomUUID());
        request.setBatchId(batch.getId()); request.setStatus(EnrollmentStatus.APPROVED);
        when(users.findByEmail("student@example.test")).thenReturn(Optional.of(user));
        when(students.findByUserId(user.getId())).thenReturn(Optional.of(student));
        when(batches.findById(batch.getId())).thenReturn(Optional.of(batch));
        when(batches.findByActiveTrueAndDeletedFalse()).thenReturn(List.of(batch));
        when(requests.findTopByStudentIdAndBatchIdAndIsDeletedFalseOrderByCreatedAtDesc(student.getId(), batch.getId()))
                .thenReturn(Optional.of(request));
        when(requests.findByStudentIdAndIsDeletedFalse(student.getId())).thenReturn(List.of(request));
        assertEquals("REJECTED", service.getBatchById(batch.getId(), "student@example.test").status());
        assertEquals("REJECTED", service.getAvailableBatches("student@example.test").getFirst().status());
        assertEquals(new BigDecimal("3500"), batch.getMonthlyFee(), "Existing fee/history is retained");
    }

    private Batch create(BigDecimal suppliedFee) {
        User user = new User(); user.setId(UUID.randomUUID());
        Teacher teacher = new Teacher(); teacher.setId(UUID.randomUUID());
        Subject subject = new Subject(); subject.setId(UUID.randomUUID());
        when(users.findByEmail("teacher@example.test")).thenReturn(Optional.of(user));
        when(teachers.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(subjects.findById(subject.getId())).thenReturn(Optional.of(subject));
        when(batches.save(any(Batch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CreateBatchDto dto = new CreateBatchDto(); dto.setName(" Physics "); dto.setExamYear("2026");
        dto.setSubjectId(subject.getId()); dto.setMonthlyFee(suppliedFee); dto.setDeliveryMode("ONLINE");
        return service.createBatch("teacher@example.test", dto);
    }
}
