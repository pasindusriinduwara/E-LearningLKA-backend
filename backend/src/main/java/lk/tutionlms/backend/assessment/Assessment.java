package lk.tutionlms.backend.assessment;

import jakarta.persistence.*;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "assessments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Assessment extends BaseEntity {

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "assessment_type", length = 30)
    @Builder.Default
    private String assessmentType = "MCQ_QUIZ";

    @Column(name = "total_marks", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal totalMarks = new BigDecimal("100.00");

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "duration_minutes")
    @Builder.Default
    private Integer durationMinutes = 60;

    @Column(name = "is_hidden")
    @Builder.Default
    private boolean hidden = false;

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<Question> questions = new ArrayList<>();
}
