package lk.tutionlms.backend.assessment;

import lk.tutionlms.backend.assessment.dto.QuizDto;
import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.identity.*;
import lk.tutionlms.backend.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AssessmentNotificationTests {
    private final AssessmentRepository assessments = mock(AssessmentRepository.class);
    private final SubmissionRepository submissions = mock(SubmissionRepository.class);
    private final BatchRepository batches = mock(BatchRepository.class);
    private final StudentRepository students = mock(StudentRepository.class);
    private final TeacherRepository teachers = mock(TeacherRepository.class);
    private final EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final AssessmentService service = new AssessmentService(assessments, mock(QuestionRepository.class),
            batches, submissions, students, enrollments, teachers, mock(AssessmentFileRepository.class), null,
            mock(QuizAttemptRepository.class), notifications);
    private final User teacherUser = new User(), studentUser = new User();
    private final Teacher teacher = new Teacher();
    private final Student student = new Student();
    private final Batch batch = new Batch();
    private final Assessment assessment = Assessment.builder().title("Essay test").assessmentType("ESSAY")
            .submissionType("TEXT").dueDate(LocalDateTime.now().plusDays(1)).build();

    @BeforeEach
    void setup() {
        teacherUser.setId(UUID.randomUUID()); teacherUser.setUserType("TEACHER");
        studentUser.setId(UUID.randomUUID()); studentUser.setUserType("STUDENT");
        teacher.setId(UUID.randomUUID()); student.setId(UUID.randomUUID());
        batch.setId(UUID.randomUUID()); batch.setTeacherId(teacher.getId());
        assessment.setId(UUID.randomUUID()); assessment.setBatchId(batch.getId());
        when(teachers.findByUserId(teacherUser.getId())).thenReturn(Optional.of(teacher));
        when(students.findByUserId(studentUser.getId())).thenReturn(Optional.of(student));
        when(batches.existsById(batch.getId())).thenReturn(true);
        when(batches.findById(batch.getId())).thenReturn(Optional.of(batch));
        when(enrollments.isStudentActiveInBatch(student.getId(), batch.getId())).thenReturn(true);
        when(assessments.findById(assessment.getId())).thenReturn(Optional.of(assessment));
        when(assessments.findByIdForUpdate(assessment.getId())).thenReturn(Optional.of(assessment));
        when(assessments.save(any(Assessment.class))).thenAnswer(call -> call.getArgument(0));
        when(submissions.save(any(Submission.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void newVisibleAssessmentNotifiesItsEnrolledClass() {
        service.createQuizWithQuestions(QuizDto.CreateQuizRequest.builder().batchId(batch.getId())
                .title("Class quiz").build(), teacherUser);
        verify(notifications).notifyBatchStudents(eq(batch.getId()), eq("ASSESSMENT_AVAILABLE"),
                eq("New assessment available"), eq("Class quiz is available in your class."), eq("/assessments"));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void hidingAnAssessmentDoesNotNotifyStudents() {
        service.toggleHideAssessment(assessment.getId(), teacherUser);
        verify(assessments).findByIdForUpdate(assessment.getId());
        verify(assessments, never()).findById(assessment.getId());
        verifyNoInteractions(notifications);
    }

    @Test
    void visibilityToggleSerializesTheTransitionBeforeSendingAnAvailabilityNotification() {
        assessment.setHidden(true);

        service.toggleHideAssessment(assessment.getId(), teacherUser);

        var ordered = inOrder(assessments, notifications);
        ordered.verify(assessments).findByIdForUpdate(assessment.getId());
        ordered.verify(assessments).save(assessment);
        ordered.verify(notifications).notifyBatchStudents(eq(batch.getId()), eq("ASSESSMENT_AVAILABLE"),
                anyString(), anyString(), eq("/assessments"));
        verify(assessments, never()).findById(assessment.getId());
    }

    @Test
    void revealingThroughUpdateNotifiesOnlyOnTheHiddenToVisibleTransition() {
        assessment.setHidden(true);
        var update = QuizDto.UpdateAssessmentRequest.builder().hidden(false).build();
        service.updateAssessment(assessment.getId(), update, teacherUser);
        service.updateAssessment(assessment.getId(), update, teacherUser);
        verify(notifications, times(1)).notifyBatchStudents(eq(batch.getId()), eq("ASSESSMENT_AVAILABLE"),
                anyString(), anyString(), eq("/assessments"));
    }

    @Test
    void firstEssaySubmissionNotifiesTheTeacherWithoutAnswersOrMarks() {
        service.submitEssay(assessment.getId(), QuizDto.EssaySubmissionRequest.builder()
                .answerText("Private answer contents").build(), studentUser);
        verify(notifications).notifyBatchTeacher(eq(batch.getId()), eq("ASSESSMENT_SUBMITTED"),
                eq("New assessment submission"), eq("A student submitted Essay test."), eq("/teacher/results"));
        verifyNoMoreInteractions(notifications);
    }

    @Test
    void essayRetryOrReplacementDoesNotGenerateDuplicateFirstSubmissionNotifications() {
        Submission existing = Submission.builder().assessmentId(assessment.getId()).studentId(student.getId())
                .status("SUBMITTED").build();
        when(submissions.findByAssessmentAndStudentWithAnswers(assessment.getId(), student.getId()))
                .thenReturn(Optional.of(existing));
        service.submitEssay(assessment.getId(), QuizDto.EssaySubmissionRequest.builder().answerText("Updated answer").build(), studentUser);
        verifyNoInteractions(notifications);
    }
}
