package lk.tutionlms.backend.assessment;

import lk.tutionlms.backend.assessment.dto.QuizDto;
import lk.tutionlms.backend.batch.*;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.identity.*;
import lk.tutionlms.backend.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResultPublicationTests {
    final AssessmentRepository assessments = mock(AssessmentRepository.class);
    final SubmissionRepository submissions = mock(SubmissionRepository.class);
    final BatchRepository batches = mock(BatchRepository.class);
    final StudentRepository students = mock(StudentRepository.class);
    final TeacherRepository teachers = mock(TeacherRepository.class);
    final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    final QuizAttemptRepository attempts = mock(QuizAttemptRepository.class);
    final NotificationService notifications = mock(NotificationService.class);
    final AssessmentService service = new AssessmentService(assessments, mock(QuestionRepository.class), batches,
            submissions, students, enrollments, teachers, mock(AssessmentFileRepository.class), null, attempts, notifications);
    final User teacherUser = user("TEACHER"), studentUser = user("STUDENT");
    final Teacher teacher = new Teacher();
    final Student student = new Student();
    final Batch batch = new Batch();
    final Assessment assessment = Assessment.builder().title("Term test").totalMarks(new BigDecimal("100")).build();
    final List<Submission> all = new ArrayList<>();

    static User user(String role) { User u = new User(); u.setId(UUID.randomUUID()); u.setUserType(role); return u; }

    @BeforeEach
    void setup() {
        teacher.setId(UUID.randomUUID()); student.setId(UUID.randomUUID()); student.setUserId(studentUser.getId());
        student.setName("Student One"); student.setStudentId("ST-001");
        batch.setId(UUID.randomUUID()); batch.setTeacherId(teacher.getId()); batch.setName("Batch A");
        assessment.setId(UUID.randomUUID()); assessment.setBatchId(batch.getId());
        when(teachers.findByUserId(teacherUser.getId())).thenReturn(Optional.of(teacher));
        when(students.findByUserId(studentUser.getId())).thenReturn(Optional.of(student));
        when(students.findById(student.getId())).thenReturn(Optional.of(student));
        when(batches.findById(batch.getId())).thenReturn(Optional.of(batch));
        when(assessments.findById(assessment.getId())).thenReturn(Optional.of(assessment));
        when(assessments.findByIdForUpdate(assessment.getId())).thenReturn(Optional.of(assessment));
        when(enrollments.isStudentActiveInBatch(student.getId(), batch.getId())).thenReturn(true);
        when(submissions.findByAssessmentIdAndDeletedFalseOrderBySubmittedAtDesc(assessment.getId())).thenReturn(all);
        when(submissions.save(any())).thenAnswer(invocation -> {
            Submission s = invocation.getArgument(0); if (s.getId() == null) s.setId(UUID.randomUUID()); return s;
        });
    }

    Submission submission(String status, String marks) {
        Submission s = Submission.builder().assessmentId(assessment.getId()).studentId(student.getId())
                .status(status).scoreObtained(marks == null ? null : new BigDecimal(marks)).feedback("Teacher feedback").build();
        s.setId(UUID.randomUUID()); all.add(s);
        when(submissions.findById(s.getId())).thenReturn(Optional.of(s));
        return s;
    }

    QuizDto.QuizSubmissionResultResponse studentView(Submission s) {
        when(submissions.findByAssessmentAndStudentWithAnswers(assessment.getId(), student.getId())).thenReturn(Optional.of(s));
        return service.getStudentSubmission(assessment.getId(), "another-student-id", studentUser);
    }

    @Test void gradedResultsRemainPrivateBeforePublication() {
        Submission s = submission("GRADED", "80");
        var result = studentView(s);
        assertFalse(result.isResultPublished()); assertEquals("GRADED", result.getStatus());
        assertNull(result.getScoreObtained()); assertNull(result.getPercentage()); assertNull(result.getGrade());
        assertNull(result.getCorrectCount()); assertNull(result.getFeedback()); assertTrue(result.getAnswers().isEmpty());
    }

    @Test void publishReleasesGradedResultsOnlyAndIsIdempotent() {
        Submission graded = submission("GRADED", "80");
        Submission pending = submission("SUBMITTED", null);
        var result = service.publishResults(assessment.getId(), teacherUser);
        assertTrue(graded.isResultPublished()); assertNotNull(graded.getResultPublishedAt());
        assertEquals(teacherUser.getId(), graded.getResultPublishedBy()); assertFalse(pending.isResultPublished());
        assertEquals(1, result.assessment().publishedCount());
        var date = graded.getResultPublishedAt();
        service.publishResults(assessment.getId(), teacherUser); assertEquals(date, graded.getResultPublishedAt());
        verify(notifications, times(1)).notifyUser(eq(studentUser.getId()), eq("RESULT_PUBLISHED"),
                eq("Assessment result published"), eq("Your result for Term test is now available."), eq("/assessments"));
        verifyNoMoreInteractions(notifications);
        assertEquals(new BigDecimal("80"), studentView(graded).getScoreObtained());
        assertEquals("Teacher feedback", studentView(graded).getFeedback());
    }

    @Test void unpublishPreservesGradesButHidesThemFromStudents() {
        Submission s = submission("GRADED", "80"); service.publishResults(assessment.getId(), teacherUser);
        service.unpublishResults(assessment.getId(), teacherUser);
        assertEquals(new BigDecimal("80"), s.getScoreObtained()); assertFalse(s.isResultPublished());
        assertNull(s.getResultPublishedAt()); assertNull(studentView(s).getScoreObtained());
    }

    @Test void submissionsReceivedAfterPublicationRemainPrivate() {
        Submission released = submission("GRADED", "80");
        service.publishResults(assessment.getId(), teacherUser);
        Submission later = submission("GRADED", "70");
        assertTrue(released.isResultPublished());
        assertFalse(later.isResultPublished());
        assertNull(studentView(later).getScoreObtained());
        assertEquals(1, service.getTeacherResultDetail(assessment.getId(), teacherUser).assessment().publishedCount());
    }

    @Test void changingTotalMarksRequiresRepublicationAndRejectsInvalidDenominators() {
        Submission s = submission("GRADED", "80");
        service.publishResults(assessment.getId(), teacherUser);
        service.updateAssessment(assessment.getId(), QuizDto.UpdateAssessmentRequest.builder()
                .totalMarks(new BigDecimal("120")).build(), teacherUser);
        assertFalse(s.isResultPublished());
        assertNull(studentView(s).getGrade());
        assertEquals(new BigDecimal("80"), s.getScoreObtained());
        assertThrows(IllegalArgumentException.class, () -> service.updateAssessment(assessment.getId(),
                QuizDto.UpdateAssessmentRequest.builder().totalMarks(new BigDecimal("50")).build(), teacherUser));
        assertEquals(new BigDecimal("120"), assessment.getTotalMarks());
    }

    @Test void regradingRequiresExplicitRepublication() {
        Submission s = submission("GRADED", "80"); service.publishResults(assessment.getId(), teacherUser);
        service.gradeSubmission(assessment.getId(), s.getId(), QuizDto.GradeSubmissionRequest.builder()
                .scoreObtained(new BigDecimal("90")).feedback("Updated").build(), teacherUser);
        assertFalse(s.isResultPublished()); assertNull(s.getResultPublishedAt()); assertNull(studentView(s).getScoreObtained());
        assertEquals(new BigDecimal("90"), s.getScoreObtained());
    }

    @Test void emptyOrInvalidGradesCannotBePublished() {
        submission("SUBMITTED", null);
        assertThrows(IllegalArgumentException.class, () -> service.publishResults(assessment.getId(), teacherUser));
        Submission invalid = submission("GRADED", "101");
        assertThrows(IllegalArgumentException.class, () -> service.publishResults(assessment.getId(), teacherUser));
        assertFalse(invalid.isResultPublished());
    }

    @Test void otherTeachersAndStudentsCannotPublishOrUnpublish() {
        submission("GRADED", "80"); batch.setTeacherId(UUID.randomUUID());
        assertThrows(AccessDeniedException.class, () -> service.publishResults(assessment.getId(), teacherUser));
        assertThrows(AccessDeniedException.class, () -> service.unpublishResults(assessment.getId(), teacherUser));
        assertThrows(AccessDeniedException.class, () -> service.publishResults(assessment.getId(), studentUser));
        verify(submissions, never()).saveAllAndFlush(any());
        verifyNoInteractions(notifications);
    }

    @Test void publishingHiddenAssessmentDoesNotNotifyStudentsAboutPrivateContent() {
        assessment.setHidden(true);
        submission("GRADED", "80");
        service.publishResults(assessment.getId(), teacherUser);
        verifyNoInteractions(notifications);
    }

    @Test void publishingDoesNotNotifyStudentsWhoNoLongerHaveAccessToTheBatch() {
        Submission graded = submission("GRADED", "80");
        when(enrollments.isStudentActiveInBatch(student.getId(), batch.getId())).thenReturn(false);

        service.publishResults(assessment.getId(), teacherUser);

        assertTrue(graded.isResultPublished());
        assertThrows(AccessDeniedException.class, () -> studentView(graded));
        verifyNoInteractions(notifications);
    }

    @Test void ranksShareTiesAndExcludeUngradedStudents() {
        submission("GRADED", "90"); submission("GRADED", "90"); submission("GRADED", "70"); submission("SUBMITTED", null);
        var detail = service.getTeacherResultDetail(assessment.getId(), teacherUser);
        assertEquals(Arrays.asList(1, 1, 3, null), detail.submissions().stream().map(QuizDto.AssessmentSubmissionSummary::getRank).toList());
        assertEquals(new BigDecimal("83.33"), detail.assessment().average());
    }

    @Test void studentListsHideUnpublishedMarksAndFeedback() {
        Submission s = submission("GRADED", "80");
        when(enrollments.findActiveBatchIdsByStudentId(student.getId())).thenReturn(List.of(batch.getId()));
        when(submissions.findByStudentIdAndDeletedFalse(student.getId())).thenReturn(List.of(s));
        when(assessments.findByBatchIdInAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(List.of(batch.getId()))).thenReturn(List.of(assessment));
        when(assessments.findByBatchIdAndDeletedFalseOrderByCreatedAtDesc(batch.getId())).thenReturn(List.of(assessment));
        var summary = service.getStudentAssessments(studentUser, null).get(0);
        assertEquals("Awaiting publication", summary.getStatus()); assertNull(summary.getScoreObtained()); assertNull(summary.getFeedback());
        assertNull(service.getStudentAssessmentsByBatch(batch.getId(), studentUser, null).get(0).getGrade());
        service.publishResults(assessment.getId(), teacherUser);
        assertEquals(new BigDecimal("80"), service.getStudentAssessments(studentUser, null).get(0).getScoreObtained());
    }

    @Test void mcqSubmissionResponseDoesNotLeakImmediateGradeOrAnswers() {
        QuizAttempt attempt = new QuizAttempt();
        attempt.setStartedAt(java.time.Instant.now());
        attempt.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
        when(attempts.findByAssessmentIdAndStudentIdAndDeletedFalse(assessment.getId(), student.getId()))
                .thenReturn(Optional.of(attempt));
        QuestionOption option = QuestionOption.builder().id(UUID.randomUUID()).optionText("Correct").correct(true).build();
        Question q = Question.builder().marks(new BigDecimal("100")).questionText("Question").options(List.of(option)).build();
        q.setId(UUID.randomUUID()); assessment.setQuestions(List.of(q));
        var result = service.submitQuiz(assessment.getId(), QuizDto.QuizSubmissionRequest.builder().answers(List.of(
                QuizDto.StudentAnswerInput.builder().questionId(q.getId()).selectedOptionId(option.getId()).build())).build(), studentUser);
        assertFalse(result.isResultPublished()); assertNull(result.getScoreObtained()); assertTrue(result.getAnswers().isEmpty());
    }

    @Test void studentIdentityComesFromPrincipalRatherThanSuppliedStudentId() {
        Submission own = submission("GRADED", "80"); own.setResultPublished(true);
        studentView(own);
        verify(submissions).findByAssessmentAndStudentWithAnswers(assessment.getId(), student.getId());
    }
}
