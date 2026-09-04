package lk.tutionlms.backend.batch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "batches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Batch extends BaseEntity {
    @Column(name = "institute_id")
    private UUID instituteId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    @Column(nullable = false)
    private String name;

    @Column(name = "exam_year", nullable = false)
    private String examYear;

    @Column(name = "monthly_fee")
    private BigDecimal monthlyFee;

    @Column(name = "delivery_mode")
    private String deliveryMode;

    @Column(name = "is_active")
    private boolean active;
}
