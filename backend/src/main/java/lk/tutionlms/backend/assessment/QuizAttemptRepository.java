package lk.tutionlms.backend.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {
    Optional<QuizAttempt> findByAssessmentIdAndStudentIdAndDeletedFalse(UUID assessmentId, UUID studentId);
}
