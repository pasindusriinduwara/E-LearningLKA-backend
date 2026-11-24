package lk.tutionlms.backend.assessment;

import com.cloudinary.Cloudinary;
import lk.tutionlms.backend.assessment.dto.QuizDto;
import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.identity.*;
import lk.tutionlms.backend.notification.NotificationService;
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
            batches, submissions, students, enrollments, teachers, files, cloudinary, mock(QuizAttemptRepository.class),
            mock(NotificationService.class));
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
    void teacherCanCreateAQuizInTheirOwnBatchUsingTheirProfileId() {
        // User IDs and teacher profile IDs are separate identifiers.
        assertNotEquals(teacherUser.getId(), teacher.getId());
        when(teachers.findByUserId(teacherUser.getId())).thenReturn(Optional.of(teacher));
        Batch ownBatch = new Batch();
        ownBatch.setId(batchId);
        ownBatch.setTeacherId(teacher.getId());
        when(batches.existsById(batchId)).thenReturn(true);
        when(batches.findById(batchId)).thenReturn(Optional.of(ownBatch));
        when(assessments.save(any(Assessment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        QuizDto.CreateQuizRequest request = QuizDto.CreateQuizRequest.builder()
                .batchId(batchId).title("Imported quiz")
                .questions(List.of(QuizDto.QuestionInputDto.builder()
                        .questionText("Which planet is known as the Red Planet?")
                        .options(List.of(
                                QuizDto.OptionInputDto.builder().optionText("Venus").build(),
                                QuizDto.OptionInputDto.builder().optionText("Mars").isCorrect(true).build()))
                        .build()))
                .build();

        Assessment saved = service.createQuizWithQuestions(request, teacherUser);

        assertEquals(batchId, saved.getBatchId());
        assertEquals("Imported quiz", saved.getTitle());
        assertEquals(1, saved.getQuestions().size());
        assertEquals(2, saved.getQuestions().getFirst().getOptions().size());
        assertTrue(saved.getQuestions().getFirst().getOptions().get(1).isCorrect());
        verify(assessments, times(2)).save(saved);
    }

    @Test
    void teacherCannotCreateAQuizInAnotherTeachersBatch() {
        when(teachers.findByUserId(teacherUser.getId())).thenReturn(Optional.of(teacher));
        Batch foreignBatch = new Batch();
        foreignBatch.setId(batchId);
        foreignBatch.setTeacherId(UUID.randomUUID());
        when(batches.existsById(batchId)).thenReturn(true);
        when(batches.findById(batchId)).thenReturn(Optional.of(foreignBatch));
        QuizDto.CreateQuizRequest request = QuizDto.CreateQuizRequest.builder()
                .batchId(batchId).title("Imported quiz").build();

        assertThrows(AccessDeniedException.class, () -> service.createQuizWithQuestions(request, teacherUser));

        verify(assessments, never()).save(any(Assessment.class));
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
        Map<String, String> response = service.uploadAnswerFile(assessment.getId(), paper, studentUser);
        String url = response.get("url");
        assertTrue(url.startsWith("/api/v1/assessments/files/"));
        var stored = org.mockito.ArgumentCaptor.forClass(AssessmentFile.class);
        verify(files).saveAndFlush(stored.capture());
        assertEquals(studentUser.getId(), stored.getValue().getUploadedBy());
        assertEquals(assessment.getId(), stored.getValue().getAssessmentId());
        assertTrue(stored.getValue().isAnswerSheet());
        verifyNoInteractions(cloudinary);
        java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("uploads", "assessments", stored.getValue().getFilename()));
    }

    @Test
    void executableAndHtmlOrMismatchedTypesAreRejectedBeforeStorage() {
        enrollStudent();
        for (MockMultipartFile unsafe : List.of(
                new MockMultipartFile("file", "answer.html", "text/html", new byte[]{1}),
                new MockMultipartFile("file", "answer.exe", "application/pdf", new byte[]{1}),
                new MockMultipartFile("file", "answer.pdf", "text/html", new byte[]{1}))) {
            assertThrows(IllegalArgumentException.class, () -> service.uploadAnswerFile(assessment.getId(), unsafe, studentUser));
        }
        verifyNoInteractions(cloudinary, files);
    }

    @Test
    void newAnswerCannotUseAnArbitraryExternalUrl() {
        enrollStudent();
        when(assessments.findByIdForUpdate(assessment.getId())).thenReturn(Optional.of(assessment));
        var request = QuizDto.EssaySubmissionRequest.builder().paperUploadUrl("https://example.test/other-answer.pdf").build();
        assertThrows(AccessDeniedException.class, () -> service.submitEssay(assessment.getId(), request, studentUser));
        verify(submissions, never()).save(any());
    }

    @Test
    void anotherStudentsUploadedFileCannotBeAttachedToMySubmission() {
        enrollStudent();
        when(assessments.findByIdForUpdate(assessment.getId())).thenReturn(Optional.of(assessment));
        AssessmentFile other = new AssessmentFile();
        other.setUploadedBy(UUID.randomUUID()); other.setAnswerSheet(true); other.setAssessmentId(assessment.getId());
        when(files.findByFilenameAndDeletedFalse("other.pdf")).thenReturn(Optional.of(other));
        var request = QuizDto.EssaySubmissionRequest.builder().paperUploadUrl("/api/v1/assessments/files/other.pdf").build();
        assertThrows(AccessDeniedException.class, () -> service.submitEssay(assessment.getId(), request, studentUser));
        verify(submissions, never()).save(any());
    }

    @Test
    void studentCanSubmitTheirOwnPrivateFileForTheSameAssessment() {
        enrollStudent();
        when(assessments.findByIdForUpdate(assessment.getId())).thenReturn(Optional.of(assessment));
        AssessmentFile own = new AssessmentFile();
        own.setUploadedBy(studentUser.getId()); own.setAnswerSheet(true); own.setAssessmentId(assessment.getId());
        when(files.findByFilenameAndDeletedFalse("own.pdf")).thenReturn(Optional.of(own));
        when(submissions.save(any())).thenAnswer(call -> call.getArgument(0));
        var request = QuizDto.EssaySubmissionRequest.builder().paperUploadUrl("/api/v1/assessments/files/own.pdf").build();
        assertDoesNotThrow(() -> service.submitEssay(assessment.getId(), request, studentUser));
        verify(submissions).save(argThat(submission -> "/api/v1/assessments/files/own.pdf".equals(submission.getPaperUploadUrl())));
    }

    @Test
    void existingLegacyAnswerLinkCanBeRetainedWhileUpdatingText() {
        enrollStudent();
        when(assessments.findByIdForUpdate(assessment.getId())).thenReturn(Optional.of(assessment));
        String legacy = "https://example.test/legacy-answer.pdf";
        Submission existing = Submission.builder().assessmentId(assessment.getId()).studentId(student.getId())
                .status("SUBMITTED").paperUploadUrl(legacy).answers(new ArrayList<>()).build();
        when(submissions.findByAssessmentIdAndStudentIdAndDeletedFalse(assessment.getId(), student.getId()))
                .thenReturn(Optional.of(existing));
        when(submissions.findByAssessmentAndStudentWithAnswers(assessment.getId(), student.getId()))
                .thenReturn(Optional.of(existing));
        when(submissions.save(any())).thenAnswer(call -> call.getArgument(0));
        var request = QuizDto.EssaySubmissionRequest.builder().paperUploadUrl(legacy).answerText("Updated answer").build();
        assertDoesNotThrow(() -> service.submitEssay(assessment.getId(), request, studentUser));
        assertEquals(legacy, existing.getPaperUploadUrl());
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
    void originalAnswerUploaderMustRetainCurrentClassAccess() {
        enrollStudent();
        AssessmentFile file = new AssessmentFile(); file.setUploadedBy(studentUser.getId());
        file.setAnswerSheet(true); file.setAssessmentId(assessment.getId());
        when(files.findByFilenameAndDeletedFalse("answer.pdf")).thenReturn(Optional.of(file));
        assertDoesNotThrow(() -> service.verifyFileAccess("answer.pdf", studentUser));
        when(enrollments.isStudentActiveInBatch(student.getId(),batchId)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.verifyFileAccess("answer.pdf", studentUser));
    }

    @Test
    void legacyLinkedAnswersAlsoRequireCurrentClassAccess() {
        enrollStudent();
        Submission submission = Submission.builder().assessmentId(assessment.getId()).studentId(student.getId()).build();
        when(submissions.findByPaperUploadUrlAndDeletedFalse("/api/v1/assessments/files/legacy.pdf")).thenReturn(List.of(submission));
        assertDoesNotThrow(() -> service.verifyFileAccess("legacy.pdf", studentUser));
        when(enrollments.isStudentActiveInBatch(student.getId(),batchId)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.verifyFileAccess("legacy.pdf", studentUser));
    }

    @Test
    void fileTraversalIsRejectedBeforeAnyLookup() {
        assertThrows(AccessDeniedException.class, () -> service.verifyFileAccess("../paper.pdf", teacherUser));
        verifyNoInteractions(files);
    }
}
