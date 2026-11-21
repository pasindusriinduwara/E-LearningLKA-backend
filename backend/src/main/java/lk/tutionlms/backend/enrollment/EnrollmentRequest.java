package lk.tutionlms.backend.enrollment;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.SQLRestriction;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "enrollment_requests", indexes = {
        @Index(name = "idx_enrollment_req_student", columnList = "student_id"),
        @Index(name = "idx_enrollment_req_batch", columnList = "batch_id"),
        @Index(name = "idx_enrollment_req_status", columnList = "status")
})
@Data
@SQLRestriction("is_deleted = false")
public class EnrollmentRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus status = EnrollmentStatus.PENDING;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted")
    private boolean isDeleted = false;
}