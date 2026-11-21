package lk.tutionlms.backend.enrollment;

import jakarta.persistence.*;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "enrollments", indexes = {
        @Index(name = "idx_enrollments_batch_status", columnList = "batch_id, status"),
        @Index(name = "idx_enrollments_student_status", columnList = "student_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Enrollment extends BaseEntity {
    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "enrolled_date")
    private LocalDate enrolledDate;

    private String status;
}
