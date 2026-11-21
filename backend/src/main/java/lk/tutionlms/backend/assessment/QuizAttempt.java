package lk.tutionlms.backend.assessment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One server-timed attempt per student and quiz; refreshing never resets its clock. */
@Entity
@Table(name = "quiz_attempts", uniqueConstraints =
        @UniqueConstraint(name = "uk_quiz_attempt_student", columnNames = {"assessment_id", "student_id"}))
@Getter
@Setter
@NoArgsConstructor
public class QuizAttempt extends BaseEntity {
    @Column(name = "assessment_id", nullable = false)
    private UUID assessmentId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
