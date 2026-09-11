package lk.tutionlms.backend.assessment;

import lk.tutionlms.backend.assessment.dto.QuizDto;
import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.StudentRepository;
import lk.tutionlms.backend.identity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
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
    private final Cloudinary cloudinary;

    public AssessmentService(
            AssessmentRepository assessmentRepository,
            QuestionRepository questionRepository,
            BatchRepository batchRepository,
            SubmissionRepository submissionRepository,
            StudentRepository studentRepository,
            Cloudinary cloudinary) {
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.batchRepository = batchRepository;
        this.submissionRepository = submissionRepository;
        this.studentRepository = studentRepository;
        this.cloudinary = cloudinary;
    }

    @Transactional
    public Assessment createQuizWithQuestions(QuizDto.CreateQuizRequest request) {
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new IllegalArgumentException("Assessment title is required");
        }

        // Safely resolve batchId to satisfy foreign key constraint
        UUID targetBatchId = request.getBatchId();
        if (targetBatchId == null || !batchRepository.existsById(targetBatchId)) {
            List<Batch> available = batchRepository.findAll();
            if (!available.isEmpty()) {
                targetBatchId = available.get(0).getId();
            } else {
                Batch fallbackBatch = batchRepository.save(Batch.builder()
                        .name("A/L 2026 Batch A")
                        .subjectId(UUID.randomUUID())
                        .build());
                targetBatchId = fallbackBatch.getId();
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
    public List<QuizDto.AssessmentSummaryResponse> getAllAssessments() {
        return assessmentRepository.findByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::mapToSummary)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QuizDto.AssessmentSummaryResponse> getStudentAssessments(User currentUser, String studentIdParam) {
        UUID studentId = resolveStudentId(currentUser, studentIdParam);
        if (studentId == null) {
            studentId = ensureDefaultStudentId();
        }

        Map<UUID, Submission> studentSubmissions = new HashMap<>();
        if (studentId != null) {
            submissionRepository.findByStudentIdAndDeletedFalse(studentId)
                    .forEach(s -> studentSubmissions.put(s.getAssessmentId(), s));
        }

        return assessmentRepository.findByDeletedFalseAndHiddenFalseOrderByCreatedAtDesc()
                .stream()
                .map(a -> {
                    QuizDto.AssessmentSummaryResponse summary = mapToSummary(a);
                    if (studentSubmissions.containsKey(a.getId())) {
                        Submission sub = studentSubmissions.get(a.getId());
                        summary.setSubmitted(true);
                        summary.setScoreObtained(sub.getScoreObtained());
                        summary.setPaperUploadUrl(sub.getPaperUploadUrl());
                        summary.setFeedback(sub.getFeedback());
                        String subStatus = "GRADED".equalsIgnoreCase(sub.getStatus()) ? "Graded" : "Submitted";
                        summary.setStatus(subStatus);
                        if ("Graded".equals(subStatus) && sub.getScoreObtained() != null) {
                            summary.setGrade(calculateGrade(sub.getScoreObtained(), a.getTotalMarks()));
                        } else {
                            summary.setGrade(null);
                        }
                    } else {
                        summary.setSubmitted(false);
                    }
                    return summary;
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<QuizDto.AssessmentSummaryResponse> getAssessmentsByBatch(UUID batchId) {
        return assessmentRepository.findByBatchIdAndDeletedFalseOrderByCreatedAtDesc(batchId)
                .stream()
                .map(this::mapToSummary)
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

    /**
     * Anti-cheating student quiz take endpoint:
     * Delivers questions and options with zero correct answer flags.
     */
    @Transactional(readOnly = true)
    public QuizDto.StudentQuizTakeResponse getAssessmentForTaking(UUID assessmentId) {
        Assessment assessment = getAssessmentById(assessmentId);

        if (assessment.isHidden() || assessment.isDeleted()) {
            throw new IllegalArgumentException("Assessment is not currently available to students");
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

        Assessment assessment = getAssessmentById(assessmentId);

        UUID studentId = resolveStudentId(currentUser, request != null ? request.getStudentId() : null);
        if (studentId == null) {
            studentId = ensureDefaultStudentId();
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
            studentId = ensureDefaultStudentId();
        }

        Submission submission = submissionRepository.findByAssessmentAndStudentWithAnswers(assessmentId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("No submission found for this student and assessment"));

        return buildSubmissionResult(assessment, submission);
    }

    private QuizDto.QuizSubmissionResultResponse buildSubmissionResult(Assessment assessment, Submission submission) {
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
                .answers(reviewAnswers)
                .build();
    }

    /**
     * Get all student submissions for an assessment (Teacher view).
     */
    @Transactional(readOnly = true)
    public List<QuizDto.AssessmentSubmissionSummary> getAssessmentSubmissions(UUID assessmentId) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .filter(a -> !a.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found with id: " + assessmentId));

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

        Assessment assessment = getAssessmentById(assessmentId);

        UUID studentId = resolveStudentId(currentUser, request != null ? request.getStudentId() : null);
        if (studentId == null) {
            studentId = ensureDefaultStudentId();
        }

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
                    .answers(new ArrayList<>())
                    .build();

            if (request != null && request.getAnswerText() != null) {
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

        submission = submissionRepository.save(submission);

        return QuizDto.QuizSubmissionResultResponse.builder()
                .submissionId(submission.getId())
                .assessmentId(assessment.getId())
                .title(assessment.getTitle())
                .scoreObtained(submission.getScoreObtained())
                .totalMarks(assessment.getTotalMarks())
                .percentage(0.0)
                .grade("Submitted")
                .correctCount(0)
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

        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found with id: " + submissionId));

        Assessment assessment = getAssessmentById(assessmentId);

        submission.setScoreObtained(request.getScoreObtained());
        submission.setFeedback(request.getFeedback());
        submission.setStatus("GRADED");
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
                .build();
    }

    /**
     * Upload question paper or answer sheet.
     */
    public Map<String, String> uploadPaperFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
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
            java.nio.file.Files.copy(file.getInputStream(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

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

    private UUID ensureDefaultStudentId() {
        Optional<Student> byCode = studentRepository.findByStudentId("ST-NEW001");
        if (byCode.isPresent()) {
            return byCode.get().getId();
        }
        List<Student> students = studentRepository.findAll();
        if (!students.isEmpty()) {
            return students.get(0).getId();
        }
        // Fallback default student
        Student fallback = studentRepository.save(Student.builder()
                .name("Pasindu Sri")
                .studentId("ST-NEW001")
                .stream("Combined Mathematics")
                .build());
        return fallback.getId();
    }

    @Transactional
    public Assessment toggleHideAssessment(UUID id) {
        Assessment a = assessmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found with id: " + id));
        a.setHidden(!a.isHidden());
        return assessmentRepository.save(a);
    }

    @Transactional
    public Assessment updateAssessment(UUID id, QuizDto.UpdateAssessmentRequest request) {
        Assessment a = assessmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found with id: " + id));

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            a.setTitle(request.getTitle().trim());
        }
        if (request.getTotalMarks() != null) {
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
        Assessment a = assessmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found with id: " + id));
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
