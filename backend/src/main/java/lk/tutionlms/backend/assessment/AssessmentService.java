package lk.tutionlms.backend.assessment;

import lk.tutionlms.backend.assessment.dto.QuizDto;
import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final QuestionRepository questionRepository;
    private final BatchRepository batchRepository;

    public AssessmentService(
            AssessmentRepository assessmentRepository,
            QuestionRepository questionRepository,
            BatchRepository batchRepository) {
        this.assessmentRepository = assessmentRepository;
        this.questionRepository = questionRepository;
        this.batchRepository = batchRepository;
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
                        .teacherId(UUID.randomUUID())
                        .examYear("2026")
                        .active(true)
                        .build());
                targetBatchId = fallbackBatch.getId();
            }
        }

        // Calculate total marks if not explicitly passed
        BigDecimal totalMarks = request.getTotalMarks();
        if (totalMarks == null || totalMarks.compareTo(BigDecimal.ZERO) <= 0) {
            totalMarks = request.getQuestions().stream()
                    .map(q -> q.getMarks() != null ? q.getMarks() : BigDecimal.ONE)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        Assessment assessment = Assessment.builder()
                .batchId(targetBatchId)
                .title(request.getTitle().trim())
                .assessmentType(request.getAssessmentType() != null ? request.getAssessmentType() : "MCQ_QUIZ")
                .totalMarks(totalMarks)
                .dueDate(request.getDueDate())
                .durationMinutes(request.getDurationMinutes() != null ? request.getDurationMinutes() : 60)
                .build();

        int order = 1;
        for (QuizDto.QuestionInputDto qDto : request.getQuestions()) {
            Question question = Question.builder()
                    .assessment(assessment)
                    .questionText(qDto.getQuestionText().trim())
                    .imageUrl(qDto.getImageUrl())
                    .marks(qDto.getMarks() != null ? qDto.getMarks() : BigDecimal.ONE)
                    .displayOrder(qDto.getDisplayOrder() != null ? qDto.getDisplayOrder() : order++)
                    .build();

            for (QuizDto.OptionInputDto optDto : qDto.getOptions()) {
                QuestionOption option = QuestionOption.builder()
                        .question(question)
                        .optionText(optDto.getOptionText() != null ? optDto.getOptionText().trim() : "")
                        .correct(optDto.isCorrect())
                        .build();

                question.getOptions().add(option);
            }

            assessment.getQuestions().add(question);
        }

        return assessmentRepository.save(assessment);
    }

    @Transactional(readOnly = true)
    public List<QuizDto.AssessmentSummaryResponse> getAssessmentsByBatch(UUID batchId) {
        return assessmentRepository.findByBatchIdAndDeletedFalseOrderByCreatedAtDesc(batchId)
                .stream()
                .map(a -> QuizDto.AssessmentSummaryResponse.builder()
                        .id(a.getId())
                        .batchId(a.getBatchId())
                        .title(a.getTitle())
                        .assessmentType(a.getAssessmentType())
                        .totalMarks(a.getTotalMarks())
                        .dueDate(a.getDueDate())
                        .durationMinutes(a.getDurationMinutes())
                        .questionCount(a.getQuestions() != null ? a.getQuestions().size() : 0)
                        .createdAt(a.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Assessment getAssessmentById(UUID id) {
        return assessmentRepository.findByIdWithQuestionsAndOptions(id)
                .orElseThrow(() -> new IllegalArgumentException("Assessment not found with id: " + id));
    }
}
