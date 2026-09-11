package lk.tutionlms.backend.assessment.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class QuizDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateQuizRequest {
        private UUID batchId;
        private String title;
        private String assessmentType; // MCQ_QUIZ, ESSAY, ASSIGNMENT
        private BigDecimal totalMarks;
        private LocalDateTime dueDate;
        private Integer durationMinutes;
        private String attachmentUrl;
        private String instructions;
        private String submissionType; // FILE_UPLOAD, TEXT, BOTH
        @Builder.Default
        private List<QuestionInputDto> questions = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuestionInputDto {
        private String questionText;
        private String imageUrl;
        private BigDecimal marks;
        private Integer displayOrder;
        @Builder.Default
        private List<OptionInputDto> options = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OptionInputDto {
        private String optionText;

        @JsonProperty("isCorrect")
        @JsonAlias({"correct", "is_correct"})
        @Builder.Default
        private Boolean isCorrect = false;

        public boolean isCorrect() {
            return Boolean.TRUE.equals(isCorrect);
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParsedQuestionDto {
        private Integer questionNumber;
        private String questionText;
        private BigDecimal marks;
        private double confidence;
        private boolean hasCorrectAnswer;
        @Builder.Default
        private List<ParsedOptionDto> options = new ArrayList<>();
        private String warning;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParsedOptionDto {
        private String label; // "A", "B", "C", "D"
        private String text;
        private boolean isCorrect;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssessmentSummaryResponse {
        private UUID id;
        private UUID batchId;
        private String batchName;
        private String title;
        private String assessmentType;
        private BigDecimal totalMarks;
        private LocalDateTime dueDate;
        private Integer durationMinutes;
        private int questionCount;
        private String status;
        private int submissionsCount;
        private int totalStudents;
        private boolean hidden;
        private boolean submitted;
        private BigDecimal scoreObtained;
        private String grade;
        private String attachmentUrl;
        private String instructions;
        private String submissionType;
        private String paperUploadUrl;
        private String feedback;
        private LocalDateTime createdAt;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateAssessmentRequest {
        private String title;
        private BigDecimal totalMarks;
        private LocalDateTime dueDate;
        private Integer durationMinutes;
        private Boolean hidden;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParseTextRequest {
        private String rawText;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentQuizTakeResponse {
        private UUID id;
        private String title;
        private UUID batchId;
        private String batchName;
        private String assessmentType;
        private BigDecimal totalMarks;
        private Integer durationMinutes;
        private LocalDateTime dueDate;
        private String attachmentUrl;
        private String instructions;
        private String submissionType;
        private List<StudentQuestionDto> questions;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentQuestionDto {
        private UUID id;
        private String questionText;
        private int displayOrder;
        private BigDecimal marks;
        private List<StudentOptionDto> options;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentOptionDto {
        private UUID id;
        private String optionText;
        private int displayOrder;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuizSubmissionRequest {
        private String studentId;
        private List<StudentAnswerInput> answers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StudentAnswerInput {
        private UUID questionId;
        private UUID selectedOptionId;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class QuizSubmissionResultResponse {
        private UUID submissionId;
        private UUID assessmentId;
        private String title;
        private BigDecimal scoreObtained;
        private BigDecimal totalMarks;
        private double percentage;
        private String grade;
        private int correctCount;
        private int totalQuestions;
        private LocalDateTime submittedAt;
        private List<ReviewAnswerDto> answers;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReviewAnswerDto {
        private UUID questionId;
        private String questionText;
        private UUID selectedOptionId;
        private UUID correctOptionId;
        private boolean isCorrect;
        private BigDecimal marksAwarded;
        private String explanation;
        private List<ReviewOptionDto> options;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReviewOptionDto {
        private UUID id;
        private String optionText;
        private boolean isCorrect;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AssessmentSubmissionSummary {
        private UUID id;
        private String studentId;
        private String studentName;
        private String submittedAt;
        private String status;
        private BigDecimal marks;
        private BigDecimal totalMarks;
        private String grade;
        private String paperUploadUrl;
        private String answerText;
        private String feedback;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EssaySubmissionRequest {
        private String studentId;
        private String answerText;
        private String paperUploadUrl;
        private String fileName;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class GradeSubmissionRequest {
        private BigDecimal scoreObtained;
        private String feedback;
    }
}
