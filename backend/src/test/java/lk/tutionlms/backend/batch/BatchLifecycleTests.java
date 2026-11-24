package lk.tutionlms.backend.batch;
import lk.tutionlms.backend.academic.*;
import lk.tutionlms.backend.content.MaterialRepository;
import lk.tutionlms.backend.enrollment.*;
import lk.tutionlms.backend.identity.*;
import lk.tutionlms.backend.scheduling.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BatchLifecycleTests {
    final BatchRepository batches=mock(BatchRepository.class);
    final UserRepository users=mock(UserRepository.class);
    final TeacherRepository teachers=mock(TeacherRepository.class);
    final SubjectRepository subjects=mock(SubjectRepository.class);
    final EnrollmentRepository enrollments=mock(EnrollmentRepository.class);
    final BatchService service=new BatchService(batches,users,teachers,subjects,mock(ScheduleRepository.class),enrollments,mock(EnrollmentRequestRepository.class),mock(StudentRepository.class),mock(MaterialRepository.class));
    Batch owned() {
        User user=User.builder().email("owner@example.test").userType("TEACHER").build();user.setId(UUID.randomUUID());
        Teacher teacher=new Teacher();teacher.setId(UUID.randomUUID());
        Batch batch=Batch.builder().teacherId(teacher.getId()).active(true).monthlyFee(new BigDecimal("3500")).name("Old name").build();batch.setId(UUID.randomUUID());
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));when(teachers.findByUserId(user.getId())).thenReturn(Optional.of(teacher));
        when(batches.findByIdForUpdate(batch.getId())).thenReturn(Optional.of(batch));when(batches.save(any())).thenAnswer(i->i.getArgument(0));return batch;
    }
    @Test void archiveAndRestorePreserveTheClassAndEnrollmentHistory() {
        Batch batch=owned();assertFalse(service.setActive("owner@example.test",batch.getId(),false).isActive());assertFalse(batch.isDeleted());
        assertTrue(service.setActive("owner@example.test",batch.getId(),true).isActive());assertEquals(new BigDecimal("3500"),batch.getMonthlyFee());
        verifyNoInteractions(enrollments);verify(batches,never()).delete(any());
    }
    @Test void foreignOwnerCannotEditArchiveOrRestore() {
        Batch batch=owned();batch.setTeacherId(UUID.randomUUID());
        assertThrows(AccessDeniedException.class,()->service.setActive("owner@example.test",batch.getId(),false));
        assertThrows(AccessDeniedException.class,()->service.updateBatch("owner@example.test",batch.getId(),new CreateBatchDto()));
        verify(batches,never()).save(any());assertTrue(batch.isActive());
    }
    @Test void editsValidateInputAndPreserveLegacyFees() {
        Batch batch=owned();CreateBatchDto dto=new CreateBatchDto();dto.setName(" Updated ");dto.setExamYear("2027");dto.setDeliveryMode("HYBRID");
        Subject subject=Subject.builder().name("Physics").active(true).build();subject.setId(UUID.randomUUID());dto.setSubjectId(subject.getId());
        when(subjects.findById(subject.getId())).thenReturn(Optional.of(subject));
        service.updateBatch("owner@example.test",batch.getId(),dto);assertEquals("Updated",batch.getName());assertEquals("HYBRID",batch.getDeliveryMode());assertEquals(new BigDecimal("3500"),batch.getMonthlyFee());
        dto.setDeliveryMode("INVALID");assertThrows(IllegalArgumentException.class,()->service.updateBatch("owner@example.test",batch.getId(),dto));
        assertEquals("HYBRID",batch.getDeliveryMode());
    }
}
