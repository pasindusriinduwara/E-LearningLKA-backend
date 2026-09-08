package lk.tutionlms.backend.enrollment;

import jakarta.persistence.*;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "enrollments")
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
