package lk.tutionlms.backend.assessment;

import lk.tutionlms.backend.assessment.dto.QuizDto;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.StudentRepository;
import lk.tutionlms.backend.identity.TeacherRepository;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class QuizAttemptSecurityTests {
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final QuizAttemptRepository attempts = mock(QuizAttemptRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AssessmentService service = new AssessmentService(assessments, mock(QuestionRepository.class),
            mock(BatchRepository.class), submissions, students, enrollments, mock(TeacherRepository.class),
            mock(AssessmentFileRepository.class), null, attempts, notifications);
    private final User user = new User();
    private final Student student = new Student();
    private final Assessment quiz = Assessment.builder().title("Timed quiz").batchId(UUID.randomUUID())
            .assessmentType("MCQ_QUIZ").durationMinutes(20).build();
    private final AtomicReference<QuizAttempt> persisted = new AtomicReference<>();
    private final QuizDto.QuizSubmissionRequest answers = QuizDto.QuizSubmissionRequest.builder().answers(List.of()).build();
    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    @BeforeEach
    void setup() {
        user.setId(UUID.randomUUID()); user.setUserType("STUDENT");
        student.setId(UUID.randomUUID()); quiz.setId(UUID.randomUUID());
        quiz.setDueDate(LocalDateTime.ofInstant(Instant.now().plusSeconds(7200), COLOMBO));
        when(students.findByUserId(user.getId())).thenReturn(Optional.of(student));
        when(enrollments.isStudentActiveInBatch(student.getId(), quiz.getBatchId())).thenReturn(true);
        when(assessments.findById(quiz.getId())).thenReturn(Optional.of(quiz));
        when(assessments.findByIdForUpdate(quiz.getId())).thenReturn(Optional.of(quiz));
        when(attempts.findByAssessmentIdAndStudentIdAndDeletedFalse(quiz.getId(), student.getId()))
                .thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
        when(attempts.save(any(QuizAttempt.class))).thenAnswer(invocation -> {
            QuizAttempt attempt = invocation.getArgument(0); attempt.setId(UUID.randomUUID());
            persisted.set(attempt); return attempt;
        });
        when(submissions.save(any(Submission.class))).thenAnswer(invocation -> {
            Submission submission = invocation.getArgument(0); submission.setId(UUID.randomUUID()); return submission;
        });
    }

    private void attemptExpiredSecondsAgo(long seconds) {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setAssessmentId(quiz.getId()); attempt.setStudentId(student.getId());
        attempt.setStartedAt(Instant.now().minusSeconds(3600));
        attempt.setExpiresAt(Instant.now().minusSeconds(seconds)); persisted.set(attempt);
    }

    @Test
    void startingPersistsServerTimesAndRepeatedStartOrGetCannotResetThem() {
        var first = service.startQuizAttempt(quiz.getId(), user);
        var resumed = service.startQuizAttempt(quiz.getId(), user);
        var read = service.getAssessmentForTaking(quiz.getId(), user, UUID.randomUUID().toString());
        assertEquals(first.getStartedAt().plusSeconds(1200), first.getExpiresAt());
        assertEquals(first.getStartedAt(), resumed.getStartedAt());
        assertEquals(first.getExpiresAt(), resumed.getExpiresAt());
        assertEquals(first.getExpiresAt(), read.getExpiresAt());
        assertNotNull(first.getServerTime());
        assertEquals(student.getId(), persisted.get().getStudentId());
        verify(attempts, times(1)).save(any());
    }

    @Test
    void questionsAndDirectSubmissionsRequireAnExplicitStartedAttempt() {
        assertThrows(IllegalArgumentException.class, () -> service.getAssessmentForTaking(quiz.getId(), user, null));
        assertThrows(IllegalArgumentException.class, () -> service.submitQuiz(quiz.getId(), answers, user));
        verify(attempts, never()).save(any()); verify(submissions, never()).save(any());
    }

    @Test
    void anonymousAndTeacherCallersCannotUseExplicitStudentIdentity() {
        assertThrows(AccessDeniedException.class, () -> service.startQuizAttempt(quiz.getId(), null));
        assertThrows(AccessDeniedException.class, () -> service.getAssessmentForTaking(quiz.getId(), null, student.getId().toString()));
        answers.setStudentId(student.getId().toString());
        user.setUserType("TEACHER");
        assertThrows(AccessDeniedException.class, () -> service.startQuizAttempt(quiz.getId(), user));
        assertThrows(AccessDeniedException.class, () -> service.submitQuiz(quiz.getId(), answers, user));
        verifyNoInteractions(attempts);
    }

    @Test
    void studentWithoutActiveEnrollmentCannotStartOrSubmit() {
        when(enrollments.isStudentActiveInBatch(student.getId(), quiz.getBatchId())).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.startQuizAttempt(quiz.getId(), user));
        assertThrows(AccessDeniedException.class, () -> service.submitQuiz(quiz.getId(), answers, user));
        verifyNoInteractions(attempts);
    }

    @Test
    void hiddenQuizRejectsBothNewAttemptsAndExistingAttemptSubmissions() {
        service.startQuizAttempt(quiz.getId(), user); quiz.setHidden(true);
        assertThrows(IllegalArgumentException.class, () -> service.startQuizAttempt(quiz.getId(), user));
        assertThrows(IllegalArgumentException.class, () -> service.submitQuiz(quiz.getId(), answers, user));
        verify(submissions, never()).save(any());
    }

    @Test
    void elapsedDeadlineRejectsStartAndSubmitEvenWhenAttemptDurationIsLonger() {
        service.startQuizAttempt(quiz.getId(), user);
        quiz.setDueDate(LocalDateTime.ofInstant(Instant.now().minusSeconds(60), COLOMBO));
        assertThrows(IllegalArgumentException.class, () -> service.startQuizAttempt(quiz.getId(), user));
        assertThrows(IllegalArgumentException.class, () -> service.submitQuiz(quiz.getId(), answers, user));
        verify(submissions, never()).save(any());
    }

    @Test
    void deadlineCapsThePersistedAttemptUsingColomboTime() {
        Instant deadline = Instant.now().plusSeconds(60);
        quiz.setDueDate(LocalDateTime.ofInstant(deadline, COLOMBO));
        var result = service.startQuizAttempt(quiz.getId(), user);
        assertEquals(deadline, result.getExpiresAt()); assertEquals(deadline, persisted.get().getExpiresAt());
    }

    @Test
    void essayCannotUseQuizStartOrSubmitButHistoricalEssayDetailsRemainReadable() {
        quiz.setAssessmentType("ESSAY");
        quiz.setDueDate(LocalDateTime.ofInstant(Instant.now().minusSeconds(3600), COLOMBO));
        assertThrows(IllegalArgumentException.class, () -> service.startQuizAttempt(quiz.getId(), user));
        assertThrows(IllegalArgumentException.class, () -> service.submitQuiz(quiz.getId(), answers, user));
        var details = service.getAssessmentForTaking(quiz.getId(), user, null);
        assertEquals("ESSAY", details.getAssessmentType()); assertNull(details.getStartedAt());
        verifyNoInteractions(attempts);
    }

    @Test
    void expiredAttemptCannotBeRestartedOrReadOrSubmittedAfterTransportAllowance() {
        attemptExpiredSecondsAgo(60);
        assertThrows(IllegalArgumentException.class, () -> service.startQuizAttempt(quiz.getId(), user));
        assertThrows(IllegalArgumentException.class, () -> service.getAssessmentForTaking(quiz.getId(), user, null));
        assertThrows(IllegalArgumentException.class, () -> service.submitQuiz(quiz.getId(), answers, user));
        verify(attempts, never()).save(any()); verify(submissions, never()).save(any());
    }

    @Test
    void timeoutAutoSubmissionHasOnlyA15SecondTransportAllowance() {
        attemptExpiredSecondsAgo(3);
        var result = service.submitQuiz(quiz.getId(), answers, user);
        assertFalse(result.isResultPublished()); assertNull(result.getScoreObtained());
        verify(submissions).save(any());
        verify(notifications).notifyBatchTeacher(eq(quiz.getBatchId()), eq("ASSESSMENT_SUBMITTED"),
                eq("New assessment submission"), eq("A student submitted Timed quiz."), eq("/teacher/results"));
    }

    @Test
    void transportAllowanceDoesNotPermitReloadingExpiredQuestions() {
        attemptExpiredSecondsAgo(3);
        assertThrows(IllegalArgumentException.class, () -> service.startQuizAttempt(quiz.getId(), user));
        assertThrows(IllegalArgumentException.class, () -> service.getAssessmentForTaking(quiz.getId(), user, null));
    }

    @Test
    void existingSubmissionIsIdempotentAndCannotBeReplacedEvenAfterExpiry() {
        Submission original = Submission.builder().assessmentId(quiz.getId()).studentId(student.getId())
                .scoreObtained(BigDecimal.TEN).status("GRADED").build();
        original.setId(UUID.randomUUID());
        when(submissions.findByAssessmentAndStudentWithAnswers(quiz.getId(), student.getId())).thenReturn(Optional.of(original));
        quiz.setDueDate(LocalDateTime.ofInstant(Instant.now().minusSeconds(3600), COLOMBO));
        assertEquals(original.getId(), service.submitQuiz(quiz.getId(), answers, user).getSubmissionId());
        assertThrows(IllegalArgumentException.class, () -> service.startQuizAttempt(quiz.getId(), user));
        verifyNoInteractions(attempts); verify(submissions, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void invalidDurationRejectsStartAndSubmit() {
        quiz.setDurationMinutes(0);
        assertThrows(IllegalArgumentException.class, () -> service.startQuizAttempt(quiz.getId(), user));
        assertThrows(IllegalArgumentException.class, () -> service.submitQuiz(quiz.getId(), answers, user));
        verifyNoInteractions(attempts);
    }

    @Test
    void foreignOptionsAndQuestionsAndDuplicateAnswersAreRejected() {
        service.startQuizAttempt(quiz.getId(), user);
        QuestionOption ownOption = QuestionOption.builder().id(UUID.randomUUID()).optionText("Own option").correct(true).build();
        Question question = Question.builder().questionText("Question").options(List.of(ownOption)).build();
        question.setId(UUID.randomUUID()); quiz.setQuestions(List.of(question));
        QuizDto.StudentAnswerInput foreignQuestion = QuizDto.StudentAnswerInput.builder()
                .questionId(UUID.randomUUID()).selectedOptionId(ownOption.getId()).build();
        answers.setAnswers(List.of(foreignQuestion));
        assertThrows(IllegalArgumentException.class, () -> service.submitQuiz(quiz.getId(), answers, user));
        QuizDto.StudentAnswerInput foreignOption = QuizDto.StudentAnswerInput.builder()
                .questionId(question.getId()).selectedOptionId(UUID.randomUUID()).build();
        answers.setAnswers(List.of(foreignOption));
        assertThrows(IllegalArgumentException.class, () -> service.submitQuiz(quiz.getId(), answers, user));
        QuizDto.StudentAnswerInput ownAnswer = QuizDto.StudentAnswerInput.builder()
                .questionId(question.getId()).selectedOptionId(ownOption.getId()).build();
        answers.setAnswers(List.of(ownAnswer, ownAnswer));
        assertThrows(IllegalArgumentException.class, () -> service.submitQuiz(quiz.getId(), answers, user));
        verify(submissions, never()).save(any());
    }

    @Test
    void suppliedStudentIdCannotSelectAnotherStudentsAttemptOrSubmission() {
        service.startQuizAttempt(quiz.getId(), user);
        answers.setStudentId(UUID.randomUUID().toString());
        service.submitQuiz(quiz.getId(), answers, user);
        verify(attempts, times(2)).findByAssessmentIdAndStudentIdAndDeletedFalse(quiz.getId(), student.getId());
        verify(students, never()).findByStudentId(any());
    }
}
