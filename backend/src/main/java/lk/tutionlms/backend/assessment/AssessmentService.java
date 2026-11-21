package lk.tutionlms.backend.assessment;

import lk.tutionlms.backend.assessment.dto.QuizDto;
import lk.tutionlms.backend.assessment.dto.ResultDto;
import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.StudentRepository;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.identity.Teacher;
import lk.tutionlms.backend.identity.TeacherRepository;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final QuestionRepository questionRepository;
    private final BatchRepository batchRepository;
    private final SubmissionRepository submissionRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TeacherRepository teacherRepository;
    private final Cloudinary cloudinary;
    private final AssessmentFileRepository assessmentFileRepository;

    @Autowired
    public AssessmentService(
            AssessmentRepository assessmentRepository,
            QuestionRepository questionRepository,
            BatchRepository batchRepository,
            SubmissionRepository submissionRepository,
            StudentRepository studentRepository,
            EnrollmentRepository enrollmentRepository,
            TeacherRepository teacherRepository,
            AssessmentFileRepository assessmentFileRepository,
            @Autowired(required = false) Cloudinary cloudinary) {
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.batchRepository = batchRepository;
        this.submissionRepository = submissionRepository;
        this.studentRepository = studentRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.teacherRepository = teacherRepository;
        this.cloudinary = cloudinary;
        this.assessmentFileRepository = assessmentFileRepository;
    }

    public void verifyTeacherAssessmentAccess(User currentUser, Assessment assessment) {
        if (currentUser == null) {
            throw new AccessDeniedException("Authentication required.");
        }
        if ("ADMIN".equalsIgnoreCase(currentUser.getUserType())) {
            return;
        }
        if (!"TEACHER".equalsIgnoreCase(currentUser.getUserType()) || assessment.getBatchId() == null) {
            throw new AccessDeniedException("Teacher access to an owned batch is required.");
        }
        Teacher teacher = teacherRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Teacher profile not found for user."));
        if (assessment.getBatchId() != null) {
            Batch batch = batchRepository.findById(assessment.getBatchId())
                    .orElseThrow(() -> new IllegalArgumentException("Batch not found for assessment."));
            if (!teacher.getId().equals(batch.getTeacherId())) {
                throw new AccessDeniedException("You do not have permission to manage assessments for this batch.");
            }
        }
    }

    @Transactional
    public Assessment createQuizWithQuestions(QuizDto.CreateQuizRequest request) {
        return createQuizWithQuestions(request, null);
    }

    @Transactional
    public Assessment createQuizWithQuestions(QuizDto.CreateQuizRequest request, User currentUser) {
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("Assessment title is required");
        }
        verifyUploadedFileOwner(request.getAttachmentUrl(), currentUser, null, false);

        UUID targetBatchId = request.getBatchId();
        if (targetBatchId == null || !batchRepository.existsById(targetBatchId)) {
            throw new IllegalArgumentException("A valid Batch ID is required to create an assessment");
        }

        if (currentUser != null && !"ADMIN".equalsIgnoreCase(currentUser.getUserType())) {
            Teacher teacher = teacherRepository.findByUserId(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Teacher profile not found"));
            Batch batch = batchRepository.findById(targetBatchId)
                    .orElseThrow(() -> new IllegalArgumentException("Batch not found"));
            if (!teacher.getId().equals(batch.getTeacherId())) {
                throw new AccessDeniedException("You do not have permission to create assessments for this batch");
            }
        }

        Assessment assessment = Assessment.builder()
                .title(request.getTitle().trim())
                .batchId(targetBatchId)
                .assessmentType(request.getAssessmentType() != null ? request.getAssessmentType() : "MCQ_QUIZ")
                .totalMarks(request.getTotalMarks() != null ? request.getTotalMarks() : new BigDecimal("100.00"))
                .durationMinutes(request.getDurationMinutes() != null ? request.getDurationMinutes() : 60)
                .dueDate(request.getDueDate() != null ? request.getDueDate() : LocalDateTime.now().plusDays(7))
                .attachmentUrl(request.getAttachmentUrl())
                .instructions(request.getInstructions())
                .submissionType(request.getSubmissionType() != null ? request.getSubmissionType() : "BOTH")
                .hidden(false)
                .build();

        Assessment savedAssessment = assessmentRepository.save(assessment);

        if (request.getQuestions() != null && !request.getQuestions().isEmpty()) {
            int order = 1;
            for (QuizDto.QuestionInputDto qDto : request.getQuestions()) {
                if (qDto.getQuestionText() == null || qDto.getQuestionText().isBlank()) {
                    continue;
                }

                Question question = Question.builder()
                        .assessment(savedAssessment)
                        .questionText(qDto.getQuestionText().trim())
                        .marks(qDto.getMarks() != null ? qDto.getMarks() : BigDecimal.ONE)
                        .displayOrder(qDto.getDisplayOrder() != null ? qDto.getDisplayOrder() : order++)
                        .build();

                if (qDto.getOptions() != null && !qDto.getOptions().isEmpty()) {
                    for (QuizDto.OptionInputDto optDto : qDto.getOptions()) {
                        if (optDto.getOptionText() == null || optDto.getOptionText().isBlank()) {
                            continue;
                        }
                        QuestionOption opt = QuestionOption.builder()
                                .question(question)
                                .optionText(optDto.getOptionText().trim())
                                .correct(optDto.isCorrect())
                                .build();
                        question.getOptions().add(opt);
                    }
                }

                savedAssessment.getQuestions().add(question);
            }

            savedAssessment = assessmentRepository.save(savedAssessment);
        }

        return savedAssessment;
    }

    @Transactional(readOnly = true)
    public List<QuizDto.AssessmentSummaryResponse> getAllAssessments(User currentUser) {
        if (currentUser == null || !"TEACHER".equalsIgnoreCase(currentUser.getUserType())) {
            throw new AccessDeniedException("Teacher authentication required.");
        }
        Teacher teacher = teacherRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Teacher profile not found."));
        List<UUID> batchIds = batchRepository.findByTeacherIdAndDeletedFalse(teacher.getId())
                .stream().map(Batch::getId).toList();
        if (batchIds.isEmpty()) return List.of();
        return assessmentRepository.findByBatchIdInAndDeletedFalseOrderByCreatedAtDesc(batchIds)
                .stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QuizDto.AssessmentSummaryResponse> getStudentAssessments(User currentUser, String studentIdParam) {
        UUID studentId = resolveStudentId(currentUser, studentIdParam);
        if (studentId == null) {
            return Collections.emptyList();
        }

        // Industrial Standard: Restrict resource discovery strictly to batches the student is actively enrolled in
        List<UUID> activeBatchIds = enrollmentRepository.findActiveBatchIdsByStudentId(studentId);
        if (activeBatchIds == null || activeBatchIds.isEmpty()) {
            return Collections.emptyList();
        }

        Map<UUID, Submission> studentSubmissions = new HashMap<>();
        submissionRepository.findByStudentIdAndDeletedFalse(studentId)
                .forEach(s -> studentSubmissions.put(s.getAssessmentId(), s));

        return assessmentRepository.findByBatchIdInAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(activeBatchIds)
                .stream()
                .map(a -> {
                    QuizDto.AssessmentSummaryResponse summary = mapToSummary(a);
                    if (studentSubmissions.containsKey(a.getId())) {
                        Submission sub = studentSubmissions.get(a.getId());
                        applyStudentResult(summary, a, sub);
                    } else {
                        summary.setSubmitted(false);
                    }
                    return summary;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QuizDto.AssessmentSummaryResponse> getAssessmentsByBatch(UUID batchId, User currentUser) {
        Assessment scope = Assessment.builder().batchId(batchId).build();
        verifyTeacherAssessmentAccess(currentUser, scope);
        return assessmentRepository.findByBatchIdAndDeletedFalseOrderByCreatedAtDesc(batchId)
                .stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QuizDto.AssessmentSummaryResponse> getStudentAssessmentsByBatch(
            UUID batchId,
            User currentUser,
            String studentIdParam) {

        UUID studentId = resolveStudentId(currentUser, studentIdParam);
        if (studentId == null || !enrollmentRepository.isStudentActiveInBatch(studentId, batchId)) {
            return Collections.emptyList();
        }

        Map<UUID, Submission> studentSubmissions = new HashMap<>();
        if (studentId != null) {
            submissionRepository.findByStudentIdAndDeletedFalse(studentId)
                    .forEach(s -> studentSubmissions.put(s.getAssessmentId(), s));
        }

        return assessmentRepository.findByBatchIdAndDeletedFalseOrderByCreatedAtDesc(batchId)
                .stream()
                .filter(a -> !a.isHidden())
                .map(a -> {
                    QuizDto.AssessmentSummaryResponse summary = mapToSummary(a);
                    if (studentSubmissions.containsKey(a.getId())) {
                        Submission sub = studentSubmissions.get(a.getId());
                        applyStudentResult(summary, a, sub);
                    } else {
                        summary.setSubmitted(false);
                    }
                    return summary;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Assessment getAssessmentById(UUID id) {
        Assessment assessment = assessmentRepository.findById(id)
                .filter(a -> !a.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found with id: " + id));
        if (assessment.getQuestions() != null) {
            for (Question q : assessment.getQuestions()) {
                if (q.getOptions() != null) {
                    q.getOptions().size(); // force lazy initialize
                }
            }
        }
        return assessment;
    }

    @Transactional(readOnly = true)
    public Assessment getTeacherAssessment(UUID id, User currentUser) {
        Assessment assessment = getAssessmentById(id);
        verifyTeacherAssessmentAccess(currentUser, assessment);
        return assessment;
    }

    /**
     * Anti-cheating student quiz take endpoint:
     * Delivers questions and options with zero correct answer flags.
     */
    @Transactional(readOnly = true)
    public QuizDto.StudentQuizTakeResponse getAssessmentForTaking(UUID assessmentId) {
        return getAssessmentForTaking(assessmentId, null, null);
    }

    /**
     * Anti-cheating student quiz take endpoint:
     * Delivers questions and options with zero correct answer flags.
     * Enforces that student must be actively enrolled in the class/batch.
     */
    @Transactional(readOnly = true)
    public QuizDto.StudentQuizTakeResponse getAssessmentForTaking(UUID assessmentId, User currentUser, String studentIdParam) {
        Assessment assessment = getAssessmentById(assessmentId);

        if (assessment.isHidden() || assessment.isDeleted()) {
            throw new IllegalArgumentException("Assessment is not currently available to students");
        }

        // Industrial Standard Guard: Verify student is actively enrolled in this assessment's batch
        if (assessment.getBatchId() != null) {
            UUID studentId = resolveStudentId(currentUser, studentIdParam);
            if (studentId == null || !enrollmentRepository.isStudentActiveInBatch(studentId, assessment.getBatchId())) {
                throw new AccessDeniedException("Access denied: You are not enrolled in the class for this assessment.");
            }
        }

        String batchName = "General Batch";
        if (assessment.getBatchId() != null) {
            batchName = batchRepository.findById(assessment.getBatchId())
                    .map(Batch::getName)
                    .orElse("General Batch");
        }

        List<QuizDto.StudentQuestionDto> studentQuestions = new ArrayList<>();
        if (assessment.getQuestions() != null) {
            List<Question> sorted = new ArrayList<>(assessment.getQuestions());
            sorted.sort(Comparator.comparingInt(q -> q.getDisplayOrder() != null ? q.getDisplayOrder() : 0));

            for (Question q : sorted) {
                List<QuizDto.StudentOptionDto> opts = new ArrayList<>();
                if (q.getOptions() != null) {
                    int optOrder = 1;
                    for (QuestionOption opt : q.getOptions()) {
                        opts.add(QuizDto.StudentOptionDto.builder()
                                .id(opt.getId())
                                .optionText(opt.getOptionText())
                                .displayOrder(optOrder++)
                                .build());
                    }
                }

                studentQuestions.add(QuizDto.StudentQuestionDto.builder()
                        .id(q.getId())
                        .questionText(q.getQuestionText())
                        .displayOrder(q.getDisplayOrder() != null ? q.getDisplayOrder() : 1)
                        .marks(q.getMarks())
                        .options(opts)
                        .build());
            }
        }

        return QuizDto.StudentQuizTakeResponse.builder()
                .id(assessment.getId())
                .title(assessment.getTitle())
                .batchId(assessment.getBatchId())
                .batchName(batchName)
                .assessmentType(assessment.getAssessmentType())
                .totalMarks(assessment.getTotalMarks())
                .durationMinutes(assessment.getDurationMinutes())
                .dueDate(assessment.getDueDate())
                .attachmentUrl(assessment.getAttachmentUrl())
                .instructions(assessment.getInstructions())
                .submissionType(assessment.getSubmissionType())
                .questions(studentQuestions)
                .build();
    }

    /**
     * Server-side grading of student quiz submission.
     */
    @Transactional
    public QuizDto.QuizSubmissionResultResponse submitQuiz(
            UUID assessmentId,
            QuizDto.QuizSubmissionRequest request,
            User currentUser) {

        Assessment assessment = lockAssessment(assessmentId);

        UUID studentId = resolveStudentId(currentUser, request != null ? request.getStudentId() : null);
        if (studentId == null) {
            throw new AccessDeniedException("Access denied: Authenticated student identity could not be verified.");
        }

        // Industrial Standard Guard: Verify student is actively enrolled in this assessment's batch
        if (assessment.getBatchId() != null && !enrollmentRepository.isStudentActiveInBatch(studentId, assessment.getBatchId())) {
            throw new AccessDeniedException("Access denied: You are not enrolled in the class for this assessment.");
        }

        // Check if student already submitted - return existing graded submission
        Optional<Submission> existing = submissionRepository.findByAssessmentAndStudentWithAnswers(assessmentId, studentId);
        if (existing.isPresent()) {
            return buildSubmissionResult(assessment, existing.get());
        }

        // Map student's chosen option IDs by questionId
        Map<UUID, UUID> studentAnswersMap = new HashMap<>();
        if (request != null && request.getAnswers() != null) {
            for (QuizDto.StudentAnswerInput ans : request.getAnswers()) {
                if (ans.getQuestionId() != null) {
                    studentAnswersMap.put(ans.getQuestionId(), ans.getSelectedOptionId());
                }
            }
        }

        List<Question> questions = assessment.getQuestions() != null ? assessment.getQuestions() : Collections.emptyList();
        int totalQuestions = questions.size();
        BigDecimal pointsPerQuestion = totalQuestions > 0
                ? assessment.getTotalMarks().divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal totalScore = BigDecimal.ZERO;
        int correctCount = 0;

        Submission submission = Submission.builder()
                .assessmentId(assessmentId)
                .studentId(studentId)
                .submittedAt(LocalDateTime.now())
                .status("GRADED")
                .build();

        List<SubmissionAnswer> submissionAnswers = new ArrayList<>();
        for (Question q : questions) {
            UUID selectedOptId = studentAnswersMap.get(q.getId());

            QuestionOption correctOpt = q.getOptions().stream()
                    .filter(QuestionOption::isCorrect)
                    .findFirst()
                    .orElse(null);

            boolean isCorrect = selectedOptId != null && correctOpt != null && selectedOptId.equals(correctOpt.getId());
            BigDecimal marks = isCorrect
                    ? (q.getMarks() != null && q.getMarks().compareTo(BigDecimal.ZERO) > 0 ? q.getMarks() : pointsPerQuestion)
                    : BigDecimal.ZERO;

            if (isCorrect) {
                correctCount++;
                totalScore = totalScore.add(marks);
            }

            SubmissionAnswer sa = SubmissionAnswer.builder()
                    .submission(submission)
                    .questionId(q.getId())
                    .selectedOptionId(selectedOptId)
                    .isCorrect(isCorrect)
                    .marksAwarded(marks)
                    .build();

            submissionAnswers.add(sa);
        }

        submission.setScoreObtained(totalScore);
        submission.setAnswers(submissionAnswers);

        Submission savedSubmission = submissionRepository.save(submission);
        return buildSubmissionResult(assessment, savedSubmission);
    }

    /**
     * Get a student's submission and answer breakdown for an assessment.
     */
    @Transactional(readOnly = true)
    public QuizDto.QuizSubmissionResultResponse getStudentSubmission(
            UUID assessmentId,
            String studentIdParam,
            User currentUser) {

        Assessment assessment = getAssessmentById(assessmentId);

        UUID studentId = resolveStudentId(currentUser, studentIdParam);
        if (studentId == null) {
            throw new AccessDeniedException("Access denied: Authenticated student identity could not be verified.");
        }

        // Industrial Standard Guard: Verify student is actively enrolled in this assessment's batch
        if (assessment.getBatchId() != null && !enrollmentRepository.isStudentActiveInBatch(studentId, assessment.getBatchId())) {
            throw new AccessDeniedException("Access denied: You are not enrolled in the class for this assessment.");
        }

        Submission submission = submissionRepository.findByAssessmentAndStudentWithAnswers(assessmentId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("No submission found for this student and assessment"));

        return buildSubmissionResult(assessment, submission);
    }

    private QuizDto.QuizSubmissionResultResponse buildSubmissionResult(Assessment assessment, Submission submission) {
        if (!isResultVisible(submission)) {
            return QuizDto.QuizSubmissionResultResponse.builder()
                    .submissionId(submission.getId()).assessmentId(assessment.getId()).title(assessment.getTitle())
                    .totalMarks(assessment.getTotalMarks()).submittedAt(submission.getSubmittedAt())
                    .status(submission.getStatus()).resultPublished(false)
                    .totalQuestions(assessment.getQuestions() != null ? assessment.getQuestions().size() : 0)
                    .answers(List.of()).build();
        }
        BigDecimal score = submission.getScoreObtained() != null ? submission.getScoreObtained() : BigDecimal.ZERO;
        BigDecimal totalMarks = assessment.getTotalMarks() != null && assessment.getTotalMarks().compareTo(BigDecimal.ZERO) > 0
                ? assessment.getTotalMarks()
                : new BigDecimal("100.00");

        double percentage = score.divide(totalMarks, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
        percentage = Math.round(percentage * 10.0) / 10.0;
        String grade = calculateGrade(score, totalMarks);

        Map<UUID, SubmissionAnswer> ansByQuestion = new HashMap<>();
        if (submission.getAnswers() != null) {
            submission.getAnswers().forEach(a -> ansByQuestion.put(a.getQuestionId(), a));
        }

        int correctCount = 0;
        List<QuizDto.ReviewAnswerDto> reviewAnswers = new ArrayList<>();
        if (assessment.getQuestions() != null) {
            for (Question q : assessment.getQuestions()) {
                SubmissionAnswer sa = ansByQuestion.get(q.getId());
                UUID selectedOptId = sa != null ? sa.getSelectedOptionId() : null;
                boolean isCorrect = sa != null && Boolean.TRUE.equals(sa.getIsCorrect());

                if (isCorrect) {
                    correctCount++;
                }

                UUID correctOptId = q.getOptions().stream()
                        .filter(QuestionOption::isCorrect)
                        .map(QuestionOption::getId)
                        .findFirst()
                        .orElse(null);

                List<QuizDto.ReviewOptionDto> reviewOpts = q.getOptions().stream()
                        .map(o -> QuizDto.ReviewOptionDto.builder()
                                .id(o.getId())
                                .optionText(o.getOptionText())
                                .isCorrect(o.isCorrect())
                                .build())
                        .collect(Collectors.toList());

                reviewAnswers.add(QuizDto.ReviewAnswerDto.builder()
                        .questionId(q.getId())
                        .questionText(q.getQuestionText())
                        .selectedOptionId(selectedOptId)
                        .correctOptionId(correctOptId)
                        .isCorrect(isCorrect)
                        .marksAwarded(sa != null ? sa.getMarksAwarded() : BigDecimal.ZERO)
                        .options(reviewOpts)
                        .build());
            }
        }

        return QuizDto.QuizSubmissionResultResponse.builder()
                .submissionId(submission.getId())
                .assessmentId(assessment.getId())
                .title(assessment.getTitle())
                .scoreObtained(score)
                .totalMarks(totalMarks)
                .percentage(percentage)
                .grade(grade)
                .correctCount(correctCount)
                .totalQuestions(assessment.getQuestions() != null ? assessment.getQuestions().size() : 0)
                .submittedAt(submission.getSubmittedAt())
                .status(submission.getStatus())
                .resultPublished(true)
                .resultPublishedAt(submission.getResultPublishedAt())
                .feedback(submission.getFeedback())
                .answers(reviewAnswers)
                .build();
    }

    /**
     * Get all student submissions for an assessment (Teacher view).
     */
    @Transactional(readOnly = true)
    public List<QuizDto.AssessmentSubmissionSummary> getAssessmentSubmissions(UUID assessmentId) {
        return getAssessmentSubmissions(assessmentId, null);
    }

    @Transactional(readOnly = true)
    public List<QuizDto.AssessmentSubmissionSummary> getAssessmentSubmissions(UUID assessmentId, User currentUser) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .filter(a -> !a.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found with id: " + assessmentId));

        if (currentUser != null) {
            verifyTeacherAssessmentAccess(currentUser, assessment);
        }

        List<Submission> submissions = submissionRepository.findByAssessmentIdAndDeletedFalseOrderBySubmittedAtDesc(assessmentId);

        return submissions.stream().map(s -> {
            String studentName = "Student";
            String studentCode = "ST-ACTIVE";

            Optional<Student> studentOpt = studentRepository.findById(s.getStudentId());
            if (studentOpt.isPresent()) {
                studentName = studentOpt.get().getName();
                studentCode = studentOpt.get().getStudentId();
            }

            String formattedTime = s.getSubmittedAt() != null
                    ? s.getSubmittedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a"))
                    : "Submitted";

            String grade = calculateGrade(s.getScoreObtained(), assessment.getTotalMarks());

            String status = "GRADED".equalsIgnoreCase(s.getStatus()) ? "Graded" : "Needs Grading";

            String answerText = "";
            if (s.getAnswers() != null && !s.getAnswers().isEmpty()) {
                answerText = s.getAnswers().get(0).getAnswerText();
            }

            return QuizDto.AssessmentSubmissionSummary.builder()
                    .id(s.getId())
                    .studentId(studentCode)
                    .studentName(studentName)
                    .submittedAt(formattedTime)
                    .status(status)
                    .marks(s.getScoreObtained())
                    .totalMarks(assessment.getTotalMarks())
                    .grade(grade)
                    .paperUploadUrl(s.getPaperUploadUrl())
                    .answerText(answerText)
                    .feedback(s.getFeedback())
                    .resultPublished(isResultVisible(s))
                    .resultPublishedAt(s.getResultPublishedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Submit an essay/structured paper assignment.
     */
    @Transactional
    public QuizDto.QuizSubmissionResultResponse submitEssay(
            UUID assessmentId,
            QuizDto.EssaySubmissionRequest request,
            User currentUser) {

        Assessment assessment = lockAssessment(assessmentId);
        boolean hasFile = request != null && request.getPaperUploadUrl() != null && !request.getPaperUploadUrl().isBlank();
        boolean hasText = request != null && request.getAnswerText() != null && !request.getAnswerText().isBlank();
        UUID studentId = validateEssaySubmission(assessment, currentUser, hasFile);
        if (!hasFile && !hasText) throw new IllegalArgumentException("An answer is required.");
        if ("FILE_UPLOAD".equalsIgnoreCase(assessment.getSubmissionType()) && !hasFile) {
            throw new IllegalArgumentException("Please upload an answer file for this assessment.");
        }
        verifyUploadedFileOwner(request.getPaperUploadUrl(), currentUser, assessmentId, true);

        Optional<Submission> existing = submissionRepository.findByAssessmentAndStudentWithAnswers(assessmentId, studentId);
        Submission submission;
        if (existing.isPresent()) {
            submission = existing.get();
            if (request != null && request.getPaperUploadUrl() != null && !request.getPaperUploadUrl().isBlank()) {
                submission.setPaperUploadUrl(request.getPaperUploadUrl());
            }
            submission.setSubmittedAt(LocalDateTime.now());
            submission.setStatus("SUBMITTED");
            if (request != null && request.getAnswerText() != null) {
                if (submission.getAnswers() != null && !submission.getAnswers().isEmpty()) {
                    submission.getAnswers().get(0).setAnswerText(request.getAnswerText());
                } else {
                    SubmissionAnswer sa = SubmissionAnswer.builder()
                            .submission(submission)
                            .questionId(assessment.getQuestions() != null && !assessment.getQuestions().isEmpty()
                                    ? assessment.getQuestions().get(0).getId() : UUID.randomUUID())
                            .answerText(request.getAnswerText())
                            .isCorrect(false)
                            .marksAwarded(BigDecimal.ZERO)
                            .build();
                    submission.getAnswers().add(sa);
                }
            }
        } else {
            submission = Submission.builder()
                    .assessmentId(assessmentId)
                    .studentId(studentId)
                    .submittedAt(LocalDateTime.now())
                    .status("SUBMITTED")
                    .paperUploadUrl(request != null ? request.getPaperUploadUrl() : null)
                    .scoreObtained(null)
                    .feedback(null)
                    .build();

            List<SubmissionAnswer> answers = new ArrayList<>();
            if (request != null && request.getAnswerText() != null && !request.getAnswerText().isBlank()) {
                SubmissionAnswer sa = SubmissionAnswer.builder()
                        .submission(submission)
                        .questionId(assessment.getQuestions() != null && !assessment.getQuestions().isEmpty()
                                ? assessment.getQuestions().get(0).getId() : UUID.randomUUID())
                        .answerText(request.getAnswerText())
                        .isCorrect(false)
                        .marksAwarded(BigDecimal.ZERO)
                        .build();
                answers.add(sa);
            }
            submission.setAnswers(answers);
        }

        submission = submissionRepository.save(submission);

        return QuizDto.QuizSubmissionResultResponse.builder()
                .submissionId(submission.getId())
                .assessmentId(assessmentId)
                .title(assessment.getTitle())
                .scoreObtained(null)
                .totalMarks(assessment.getTotalMarks())
                .status("SUBMITTED")
                .resultPublished(false)
                .totalQuestions(1)
                .submittedAt(submission.getSubmittedAt())
                .answers(new ArrayList<>())
                .build();
    }

    /**
     * Teacher evaluation and grading of a submission.
     */
    @Transactional
    public QuizDto.AssessmentSubmissionSummary gradeSubmission(
            UUID assessmentId,
            UUID submissionId,
            QuizDto.GradeSubmissionRequest request) {
        return gradeSubmission(assessmentId, submissionId, request, null);
    }

    @Transactional
    public QuizDto.AssessmentSubmissionSummary gradeSubmission(
            UUID assessmentId,
            UUID submissionId,
            QuizDto.GradeSubmissionRequest request,
            User currentUser) {

        Assessment assessment = lockAssessment(assessmentId);
        if (currentUser != null) {
            verifyTeacherAssessmentAccess(currentUser, assessment);
        }

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found with id: " + submissionId));

        if (!submission.getAssessmentId().equals(assessment.getId())) {
            throw new IllegalArgumentException("Submission does not belong to the specified assessment.");
        }

        if (request == null || request.getScoreObtained() == null
                || request.getScoreObtained().compareTo(BigDecimal.ZERO) < 0
                || request.getScoreObtained().compareTo(assessment.getTotalMarks()) > 0) {
            throw new IllegalArgumentException("Marks must be between zero and the assessment's total marks.");
        }
        submission.setScoreObtained(request.getScoreObtained());
        submission.setFeedback(request.getFeedback());
        submission.setStatus("GRADED");
        revokeResult(submission);
        submission = submissionRepository.save(submission);

        String grade = calculateGrade(submission.getScoreObtained(), assessment.getTotalMarks());
        String studentName = "Student";
        String studentCode = "ST-ACTIVE";
        Optional<Student> studentOpt = studentRepository.findById(submission.getStudentId());
        if (studentOpt.isPresent()) {
            studentName = studentOpt.get().getName();
            studentCode = studentOpt.get().getStudentId();
        }

        String answerText = "";
        if (submission.getAnswers() != null && !submission.getAnswers().isEmpty()) {
            answerText = submission.getAnswers().get(0).getAnswerText();
        }

        return QuizDto.AssessmentSubmissionSummary.builder()
                .id(submission.getId())
                .studentId(studentCode)
                .studentName(studentName)
                .submittedAt(submission.getSubmittedAt() != null ? submission.getSubmittedAt().toString() : "")
                .status("Graded")
                .marks(submission.getScoreObtained())
                .totalMarks(assessment.getTotalMarks())
                .grade(grade)
                .paperUploadUrl(submission.getPaperUploadUrl())
                .answerText(answerText)
                .feedback(submission.getFeedback())
                .resultPublished(false)
                .build();
    }

    private boolean isResultVisible(Submission submission) {
        return submission.isResultPublished() && "GRADED".equalsIgnoreCase(submission.getStatus())
                && submission.getScoreObtained() != null;
    }

    private void applyStudentResult(QuizDto.AssessmentSummaryResponse summary, Assessment assessment, Submission submission) {
        boolean visible = isResultVisible(submission);
        summary.setSubmitted(true);
        summary.setResultPublished(visible);
        summary.setResultPublishedAt(visible ? submission.getResultPublishedAt() : null);
        summary.setPaperUploadUrl(submission.getPaperUploadUrl());
        summary.setScoreObtained(visible ? submission.getScoreObtained() : null);
        summary.setFeedback(visible ? submission.getFeedback() : null);
        summary.setGrade(visible ? calculateGrade(submission.getScoreObtained(), assessment.getTotalMarks()) : null);
        summary.setStatus(visible ? "Graded" : "GRADED".equalsIgnoreCase(submission.getStatus())
                ? "Awaiting publication" : "Submitted");
    }

    private Assessment lockAssessment(UUID id) {
        return assessmentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found."));
    }

    private void revokeResult(Submission submission) {
        submission.setResultPublished(false);
        submission.setResultPublishedAt(null);
        submission.setResultPublishedBy(null);
    }

    @Transactional(readOnly = true)
    public List<ResultDto.Summary> getTeacherResults(User user) {
        return getAllAssessments(user).stream().map(summary -> resultSummary(summary,
                submissionRepository.findByAssessmentIdAndDeletedFalseOrderBySubmittedAtDesc(summary.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ResultDto.Detail getTeacherResultDetail(UUID id, User user) {
        Assessment assessment = getTeacherAssessment(id, user);
        List<Submission> submissions = submissionRepository.findByAssessmentIdAndDeletedFalseOrderBySubmittedAtDesc(id);
        List<QuizDto.AssessmentSubmissionSummary> rows = getAssessmentSubmissions(id, user);
        List<QuizDto.AssessmentSubmissionSummary> ranked = rows.stream()
                .filter(row -> "Graded".equals(row.getStatus()) && row.getMarks() != null)
                .sorted(Comparator.comparing(QuizDto.AssessmentSubmissionSummary::getMarks).reversed()).toList();
        BigDecimal previous = null;
        int rank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            if (previous == null || previous.compareTo(ranked.get(i).getMarks()) != 0) rank = i + 1;
            ranked.get(i).setRank(rank);
            previous = ranked.get(i).getMarks();
        }
        rows.sort(Comparator.comparing(QuizDto.AssessmentSubmissionSummary::getRank,
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(QuizDto.AssessmentSubmissionSummary::getStudentName));
        return new ResultDto.Detail(resultSummary(mapToSummary(assessment), submissions), rows);
    }

    @Transactional
    public ResultDto.Detail publishResults(UUID id, User user) {
        Assessment assessment = lockAssessment(id);
        verifyTeacherAssessmentAccess(user, assessment);
        List<Submission> submissions = submissionRepository.findByAssessmentIdAndDeletedFalseOrderBySubmittedAtDesc(id);
        List<Submission> graded = submissions.stream().filter(s -> "GRADED".equalsIgnoreCase(s.getStatus())
                && s.getScoreObtained() != null).toList();
        if (graded.isEmpty()) throw new IllegalArgumentException("Grade at least one submission before publishing results.");
        for (Submission submission : graded) {
            if (submission.getScoreObtained().signum() < 0
                    || submission.getScoreObtained().compareTo(assessment.getTotalMarks()) > 0) {
                throw new IllegalArgumentException("Correct invalid marks before publishing results.");
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (Submission submission : graded) {
            if (!submission.isResultPublished()) {
                submission.setResultPublished(true);
                submission.setResultPublishedAt(now);
                submission.setResultPublishedBy(user.getId());
            }
        }
        submissionRepository.saveAllAndFlush(graded);
        return getTeacherResultDetail(id, user);
    }

    @Transactional
    public ResultDto.Detail unpublishResults(UUID id, User user) {
        Assessment assessment = lockAssessment(id);
        verifyTeacherAssessmentAccess(user, assessment);
        List<Submission> submissions = submissionRepository.findByAssessmentIdAndDeletedFalseOrderBySubmittedAtDesc(id);
        submissions.forEach(this::revokeResult);
        submissionRepository.saveAllAndFlush(submissions);
        return getTeacherResultDetail(id, user);
    }

    private ResultDto.Summary resultSummary(QuizDto.AssessmentSummaryResponse assessment, List<Submission> submissions) {
        List<Submission> graded = submissions.stream().filter(s -> "GRADED".equalsIgnoreCase(s.getStatus())
                && s.getScoreObtained() != null).toList();
        BigDecimal total = graded.stream().map(Submission::getScoreObtained).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal highest = graded.stream().map(Submission::getScoreObtained).max(BigDecimal::compareTo).orElse(null);
        BigDecimal average = graded.isEmpty() ? null : total.divide(BigDecimal.valueOf(graded.size()), 2, RoundingMode.HALF_UP);
        long passed = graded.stream().filter(s -> s.getScoreObtained().compareTo(
                assessment.getTotalMarks().multiply(new BigDecimal("0.4"))) >= 0).count();
        BigDecimal passRate = graded.isEmpty() ? null : BigDecimal.valueOf(passed * 100)
                .divide(BigDecimal.valueOf(graded.size()), 1, RoundingMode.HALF_UP);
        LocalDateTime lastPublished = submissions.stream().filter(this::isResultVisible)
                .map(Submission::getResultPublishedAt).filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
        return new ResultDto.Summary(assessment.getId(), assessment.getBatchId(), assessment.getBatchName(),
                assessment.getTitle(), assessment.getAssessmentType(), assessment.getTotalMarks(), submissions.size(),
                graded.size(), (int) submissions.stream().filter(this::isResultVisible).count(),
                enrollmentRepository.countByBatchIdAndStatusAndDeletedFalse(assessment.getBatchId(), "ACTIVE"),
                average, highest, passRate, lastPublished);
    }

    /**
     * Upload question paper or answer sheet.
     */
    @Transactional
    public Map<String, String> uploadPaperFile(MultipartFile file, User currentUser) {
        if (currentUser == null || !"TEACHER".equalsIgnoreCase(currentUser.getUserType())) {
            throw new AccessDeniedException("Teacher authentication required.");
        }
        return storePaperFile(file, currentUser, null, false);
    }

    @Transactional
    public Map<String, String> uploadAnswerFile(UUID assessmentId, MultipartFile file, User currentUser) {
        Assessment assessment = getAssessmentById(assessmentId);
        validateEssaySubmission(assessment, currentUser, true);
        return storePaperFile(file, currentUser, assessmentId, true);
    }

    private UUID validateEssaySubmission(Assessment assessment, User currentUser, boolean fileUpload) {
        if (currentUser == null || !"STUDENT".equalsIgnoreCase(currentUser.getUserType())) {
            throw new AccessDeniedException("Student authentication required.");
        }
        UUID studentId = studentRepository.findByUserId(currentUser.getId()).map(Student::getId)
                .orElseThrow(() -> new AccessDeniedException("Student profile not found."));
        if (assessment.getBatchId() == null
                || !enrollmentRepository.isStudentActiveInBatch(studentId, assessment.getBatchId())) {
            throw new AccessDeniedException("You are not enrolled in this assessment's batch.");
        }
        if (assessment.isHidden() || assessment.isDeleted()
                || (assessment.getDueDate() != null && assessment.getDueDate().isBefore(LocalDateTime.now()))) {
            throw new IllegalArgumentException("This assessment is not accepting submissions.");
        }
        if ("MCQ_QUIZ".equalsIgnoreCase(assessment.getAssessmentType())) {
            throw new IllegalArgumentException("Use the quiz submission endpoint for MCQ quizzes.");
        }
        if (fileUpload && "TEXT".equalsIgnoreCase(assessment.getSubmissionType())) {
            throw new IllegalArgumentException("This assessment accepts text answers only.");
        }
        submissionRepository.findByAssessmentIdAndStudentIdAndDeletedFalse(assessment.getId(), studentId)
                .filter(s -> "GRADED".equalsIgnoreCase(s.getStatus()))
                .ifPresent(s -> { throw new IllegalArgumentException("A graded submission cannot be replaced."); });
        return studentId;
    }

    @Transactional(readOnly = true)
    public void verifyFileAccess(String filename, User currentUser) {
        if (currentUser == null || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new AccessDeniedException("File access denied.");
        }
        Optional<AssessmentFile> stored = assessmentFileRepository.findByFilenameAndDeletedFalse(filename);
        if (stored.isPresent() && stored.get().getUploadedBy().equals(currentUser.getId())) return;
        if (stored.isPresent() && stored.get().isAnswerSheet() && stored.get().getAssessmentId() != null) {
            verifyTeacherAssessmentAccess(currentUser, getAssessmentById(stored.get().getAssessmentId()));
            return;
        }
        String url = "/api/v1/assessments/files/" + filename;
        for (Assessment assessment : assessmentRepository.findByAttachmentUrlAndDeletedFalse(url)) {
            if (canReadAssessmentFile(assessment, currentUser)) return;
        }
        for (Submission submission : submissionRepository.findByPaperUploadUrlAndDeletedFalse(url)) {
            if (studentRepository.findByUserId(currentUser.getId())
                    .map(s -> s.getId().equals(submission.getStudentId())).orElse(false)) return;
            if (isAssessmentTeacher(getAssessmentById(submission.getAssessmentId()), currentUser)) return;
        }
        throw new AccessDeniedException("File access denied.");
    }

    private boolean canReadAssessmentFile(Assessment assessment, User user) {
        if (isAssessmentTeacher(assessment, user)) return true;
        return "STUDENT".equalsIgnoreCase(user.getUserType()) && !assessment.isHidden()
                && studentRepository.findByUserId(user.getId())
                    .map(s -> enrollmentRepository.isStudentActiveInBatch(s.getId(), assessment.getBatchId()))
                    .orElse(false);
    }

    private boolean isAssessmentTeacher(Assessment assessment, User user) {
        if (!"TEACHER".equalsIgnoreCase(user.getUserType())) return false;
        try {
            verifyTeacherAssessmentAccess(user, assessment);
            return true;
        } catch (AccessDeniedException | IllegalArgumentException ex) {
            return false;
        }
    }

    private void verifyUploadedFileOwner(String url, User user, UUID assessmentId, boolean answerSheet) {
        String prefix = "/api/v1/assessments/files/";
        if (url == null || !url.startsWith(prefix)) return;
        AssessmentFile file = assessmentFileRepository.findByFilenameAndDeletedFalse(url.substring(prefix.length()))
                .orElseThrow(() -> new AccessDeniedException("Uploaded file ownership could not be verified."));
        if (user == null || !file.getUploadedBy().equals(user.getId()) || file.isAnswerSheet() != answerSheet
                || !Objects.equals(file.getAssessmentId(), assessmentId)) {
            throw new AccessDeniedException("This uploaded file does not belong to the current submission.");
        }
    }

    private Map<String, String> storePaperFile(MultipartFile file, User currentUser, UUID assessmentId, boolean answerSheet) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        if (file.getSize() > 20 * 1024 * 1024) {
            throw new IllegalArgumentException("File must be 20 MB or smaller.");
        }
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "paper.pdf";
        try {
            if (cloudinary != null) {
                Map<?, ?> uploadResult = cloudinary.uploader().upload(
                        file.getBytes(),
                        ObjectUtils.asMap(
                                "resource_type", "auto",
                                "folder", "tuition-lms/assessments",
                                "use_filename", true,
                                "unique_filename", true));
                String secureUrl = (String) uploadResult.get("secure_url");
                if (secureUrl != null && !secureUrl.isBlank()) {
                    Map<String, String> response = new HashMap<>();
                    response.put("url", secureUrl);
                    response.put("fileName", originalName);
                    return response;
                }
            }
        } catch (Exception e) {
            // Log and fallback to local file storage
            System.err.println("Cloudinary paper upload failed, falling back to local storage: " + e.getMessage());
        }

        // Local storage fallback
        try {
            java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads", "assessments");
            if (!java.nio.file.Files.exists(uploadDir)) {
                java.nio.file.Files.createDirectories(uploadDir);
            }
            String uniqueName = UUID.randomUUID() + "_" + originalName.replaceAll("[^a-zA-Z0-9.-]", "_");
            java.nio.file.Path targetPath = uploadDir.resolve(uniqueName);
            try (java.io.InputStream input = file.getInputStream()) {
                java.nio.file.Files.copy(input, targetPath);
            }
            AssessmentFile stored = new AssessmentFile();
            stored.setFilename(uniqueName);
            stored.setUploadedBy(currentUser.getId());
            stored.setAssessmentId(assessmentId);
            stored.setAnswerSheet(answerSheet);
            try {
                assessmentFileRepository.saveAndFlush(stored);
            } catch (RuntimeException ex) {
                java.nio.file.Files.deleteIfExists(targetPath);
                throw ex;
            }

            Map<String, String> response = new HashMap<>();
            response.put("url", "/api/v1/assessments/files/" + uniqueName);
            response.put("fileName", originalName);
            return response;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to store paper file locally: " + ex.getMessage(), ex);
        }
    }

    private String calculateGrade(BigDecimal score, BigDecimal totalMarks) {
        if (score == null || totalMarks == null || totalMarks.compareTo(BigDecimal.ZERO) <= 0) {
            return "N/A";
        }
        double pct = score.divide(totalMarks, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
        if (pct >= 85.0) return "A+";
        if (pct >= 75.0) return "A";
        if (pct >= 65.0) return "B";
        if (pct >= 55.0) return "C";
        if (pct >= 40.0) return "S";
        return "F";
    }

    private UUID resolveStudentId(User currentUser, String explicitStudentId) {
        if (currentUser != null) {
            Optional<Student> student = studentRepository.findByUserId(currentUser.getId());
            if (student.isPresent()) {
                return student.get().getId();
            }
        }
        if (explicitStudentId != null && !explicitStudentId.isBlank()) {
            Optional<Student> byCode = studentRepository.findByStudentId(explicitStudentId.trim());
            if (byCode.isPresent()) {
                return byCode.get().getId();
            }
            try {
                UUID parsed = UUID.fromString(explicitStudentId.trim());
                if (studentRepository.existsById(parsed)) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    @Transactional
    public Assessment toggleHideAssessment(UUID id) {
        return toggleHideAssessment(id, null);
    }

    @Transactional
    public Assessment toggleHideAssessment(UUID id, User currentUser) {
        Assessment a = assessmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found with id: " + id));
        if (currentUser != null) {
            verifyTeacherAssessmentAccess(currentUser, a);
        }
        a.setHidden(!a.isHidden());
        return assessmentRepository.save(a);
    }

    @Transactional
    public Assessment updateAssessment(UUID id, QuizDto.UpdateAssessmentRequest request) {
        return updateAssessment(id, request, null);
    }

    @Transactional
    public Assessment updateAssessment(UUID id, QuizDto.UpdateAssessmentRequest request, User currentUser) {
        Assessment a = lockAssessment(id);

        if (currentUser != null) {
            verifyTeacherAssessmentAccess(currentUser, a);
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            a.setTitle(request.getTitle().trim());
        }
        if (request.getTotalMarks() != null) {
            if (request.getTotalMarks().signum() <= 0) throw new IllegalArgumentException("Total marks must be positive.");
            if (request.getTotalMarks().compareTo(a.getTotalMarks()) != 0) {
                List<Submission> submissions = submissionRepository.findByAssessmentIdAndDeletedFalseOrderBySubmittedAtDesc(id);
                if (submissions.stream().anyMatch(s -> s.getScoreObtained() != null
                        && s.getScoreObtained().compareTo(request.getTotalMarks()) > 0)) {
                    throw new IllegalArgumentException("Total marks cannot be lower than an existing student's score.");
                }
                submissions.forEach(this::revokeResult);
                submissionRepository.saveAll(submissions);
            }
            a.setTotalMarks(request.getTotalMarks());
        }
        if (request.getDueDate() != null) {
            a.setDueDate(request.getDueDate());
        }
        if (request.getDurationMinutes() != null) {
            a.setDurationMinutes(request.getDurationMinutes());
        }
        if (request.getHidden() != null) {
            a.setHidden(request.getHidden());
        }

        return assessmentRepository.save(a);
    }

    @Transactional
    public void deleteAssessment(UUID id) {
        deleteAssessment(id, null);
    }

    @Transactional
    public void deleteAssessment(UUID id, User currentUser) {
        Assessment a = assessmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found with id: " + id));

        if (currentUser != null) {
            verifyTeacherAssessmentAccess(currentUser, a);
        }

        a.setDeleted(true);
        assessmentRepository.save(a);
    }

    private QuizDto.AssessmentSummaryResponse mapToSummary(Assessment a) {
        String batchName = "All Batches";
        if (a.getBatchId() != null) {
            batchName = batchRepository.findById(a.getBatchId())
                    .map(Batch::getName)
                    .orElse("A/L Batch");
        }

        String status = "Open";
        if (a.isHidden()) {
            status = "Hidden";
        } else if (a.getDueDate() != null && a.getDueDate().isBefore(LocalDateTime.now())) {
            status = "Closed";
        }

        long submissionCount = submissionRepository.countByAssessmentIdAndDeletedFalse(a.getId());

        return QuizDto.AssessmentSummaryResponse.builder()
                .id(a.getId())
                .batchId(a.getBatchId())
                .batchName(batchName)
                .title(a.getTitle())
                .assessmentType(a.getAssessmentType())
                .totalMarks(a.getTotalMarks())
                .dueDate(a.getDueDate())
                .durationMinutes(a.getDurationMinutes())
                .attachmentUrl(a.getAttachmentUrl())
                .instructions(a.getInstructions())
                .submissionType(a.getSubmissionType())
                .questionCount(a.getQuestions() != null ? a.getQuestions().size() : 0)
                .status(status)
                .hidden(a.isHidden())
                .submissionsCount((int) submissionCount)
                .totalStudents(32)
                .createdAt(a.getCreatedAt())
                .build();
    }
}
