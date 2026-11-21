package lk.tutionlms.backend.assessment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class ResultDto {
    public record Summary(UUID id, UUID batchId, String batchName, String title, String assessmentType,
                          BigDecimal totalMarks, int submittedCount, int gradedCount, int publishedCount,
                          long enrolledCount, BigDecimal average, BigDecimal highest, BigDecimal passRate,
                          LocalDateTime lastPublishedAt) {}

    public record Detail(Summary assessment, List<QuizDto.AssessmentSubmissionSummary> submissions) {}
}
