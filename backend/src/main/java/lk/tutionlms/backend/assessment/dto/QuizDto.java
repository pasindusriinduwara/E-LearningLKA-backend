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
        private String assessmentType; // MCQ_QUIZ
        private BigDecimal totalMarks;
        private LocalDateTime dueDate;
        private Integer durationMinutes;
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
}
