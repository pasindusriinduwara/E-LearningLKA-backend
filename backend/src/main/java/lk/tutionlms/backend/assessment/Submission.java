package lk.tutionlms.backend.assessment;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Submission extends BaseEntity {

    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "submitted_at")
    @Builder.Default
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "score_obtained", precision = 5, scale = 2)
    private BigDecimal scoreObtained;

    @Column(name = "paper_upload_url", length = 500)
    private String paperUploadUrl;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(length = 20, nullable = false)
    @Builder.Default
    private String status = "GRADED";

    @OneToMany(mappedBy = "submission", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    @Builder.Default
    private List<SubmissionAnswer> answers = new ArrayList<>();
}
