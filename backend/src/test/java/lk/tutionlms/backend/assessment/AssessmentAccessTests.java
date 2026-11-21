package lk.tutionlms.backend.assessment;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.identity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssessmentAccessTests {
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final BatchRepository batches = mock(BatchRepository.class);
    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final AssessmentFileRepository files = mock(AssessmentFileRepository.class);
    private final Cloudinary cloudinary = mock(Cloudinary.class);
    private final AssessmentService service = new AssessmentService(assessments, mock(QuestionRepository.class),
            batches, submissions, students, enrollments, teachers, files, cloudinary);
    private final User studentUser = user("STUDENT");
    private final User teacherUser = user("TEACHER");
    private final Student student = new Student();
    private final Teacher teacher = new Teacher();
    private final UUID batchId = UUID.randomUUID();
    private final Assessment assessment = Assessment.builder().batchId(batchId).assessmentType("ESSAY")
            .title("Essay").dueDate(LocalDateTime.now().plusDays(1)).build();
    private final MockMultipartFile paper = new MockMultipartFile("file", "answer.pdf", "application/pdf", new byte[]{1, 2});

    private static User user(String role) {
        User user = new User(); user.setId(UUID.randomUUID()); user.setUserType(role); return user;
    }

    @BeforeEach
    void setup() {
        student.setId(UUID.randomUUID());
        teacher.setId(UUID.randomUUID());
        assessment.setId(UUID.randomUUID());
        when(assessments.findById(assessment.getId())).thenReturn(Optional.of(assessment));
    }

    private void enrollStudent() {
        when(students.findByUserId(studentUser.getId())).thenReturn(Optional.of(student));
        when(enrollments.isStudentActiveInBatch(student.getId(), batchId)).thenReturn(true);
    }

    @Test
    void studentsCannotReadAnswerKeysThroughManagementDetails() {
        assertThrows(AccessDeniedException.class, () -> service.getTeacherAssessment(assessment.getId(), studentUser));
        verifyNoInteractions(teachers);
    }

    @Test
    void anotherTeacherCannotReadFullDetails() {
        when(teachers.findByUserId(teacherUser.getId())).thenReturn(Optional.of(teacher));
        Batch batch = new Batch(); batch.setTeacherId(UUID.randomUUID());
        when(batches.findById(batchId)).thenReturn(Optional.of(batch));
        assertThrows(AccessDeniedException.class, () -> service.getTeacherAssessment(assessment.getId(), teacherUser));
    }

    @Test
    void managementListQueriesOnlyTheTeachersBatches() {
        when(teachers.findByUserId(teacherUser.getId())).thenReturn(Optional.of(teacher));
        Batch ownBatch = new Batch(); ownBatch.setId(batchId);
        when(batches.findByTeacherIdAndDeletedFalse(teacher.getId())).thenReturn(List.of(ownBatch));
        when(assessments.findByBatchIdInAndDeletedFalseOrderByCreatedAtDesc(List.of(batchId))).thenReturn(List.of());
        assertTrue(service.getAllAssessments(teacherUser).isEmpty());
        verify(assessments).findByBatchIdInAndDeletedFalseOrderByCreatedAtDesc(List.of(batchId));
        verify(assessments, never()).findByDeletedFalseOrderByCreatedAtDesc();
    }

    @Test
    void unenrolledStudentCannotUploadAnAnswer() {
        when(students.findByUserId(studentUser.getId())).thenReturn(Optional.of(student));
        assertThrows(AccessDeniedException.class, () -> service.uploadAnswerFile(assessment.getId(), paper, studentUser));
        verifyNoInteractions(cloudinary, files);
    }

    @Test
    void enrolledStudentCanUploadAnEssayAnswer() throws Exception {
        enrollStudent();
        Uploader uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("secure_url", "https://example.test/answer.pdf"));
        assertEquals("https://example.test/answer.pdf", service.uploadAnswerFile(assessment.getId(), paper, studentUser).get("url"));
    }

    @Test
    void hiddenAssessmentRejectsUploads() {
        enrollStudent(); assessment.setHidden(true);
        assertThrows(IllegalArgumentException.class, () -> service.uploadAnswerFile(assessment.getId(), paper, studentUser));
        verifyNoInteractions(cloudinary);
    }

    @Test
    void expiredAssessmentRejectsUploads() {
        enrollStudent(); assessment.setDueDate(LocalDateTime.now().minusMinutes(1));
        assertThrows(IllegalArgumentException.class, () -> service.uploadAnswerFile(assessment.getId(), paper, studentUser));
    }

    @Test
    void gradedSubmissionCannotBeReplaced() {
        enrollStudent();
        Submission graded = Submission.builder().status("GRADED").build();
        when(submissions.findByAssessmentIdAndStudentIdAndDeletedFalse(assessment.getId(), student.getId()))
                .thenReturn(Optional.of(graded));
        assertThrows(IllegalArgumentException.class, () -> service.uploadAnswerFile(assessment.getId(), paper, studentUser));
    }

    @Test
    void textOnlyAssessmentRejectsFileUpload() {
        enrollStudent(); assessment.setSubmissionType("TEXT");
        assertThrows(IllegalArgumentException.class, () -> service.uploadAnswerFile(assessment.getId(), paper, studentUser));
    }

    @Test
    void studentCannotUseTeacherPaperUpload() {
        assertThrows(AccessDeniedException.class, () -> service.uploadPaperFile(paper, studentUser));
    }

    @Test
    void uploaderCanPreviewAnUnassignedPaper() {
        AssessmentFile file = new AssessmentFile(); file.setUploadedBy(teacherUser.getId());
        when(files.findByFilenameAndDeletedFalse("paper.pdf")).thenReturn(Optional.of(file));
        assertDoesNotThrow(() -> service.verifyFileAccess("paper.pdf", teacherUser));
    }

    @Test
    void anotherStudentCannotReadAnAnswerSheet() {
        AssessmentFile file = new AssessmentFile(); file.setUploadedBy(UUID.randomUUID());
        file.setAnswerSheet(true); file.setAssessmentId(assessment.getId());
        when(files.findByFilenameAndDeletedFalse("answer.pdf")).thenReturn(Optional.of(file));
        assertThrows(AccessDeniedException.class, () -> service.verifyFileAccess("answer.pdf", studentUser));
    }

    @Test
    void fileTraversalIsRejectedBeforeAnyLookup() {
        assertThrows(AccessDeniedException.class, () -> service.verifyFileAccess("../paper.pdf", teacherUser));
        verifyNoInteractions(files);
    }
}
