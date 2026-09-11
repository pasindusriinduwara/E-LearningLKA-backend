package lk.tutionlms.backend.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    Optional<Submission> findByAssessmentIdAndStudentIdAndDeletedFalse(UUID assessmentId, UUID studentId);

    List<Submission> findByStudentIdAndDeletedFalse(UUID studentId);

    List<Submission> findByAssessmentIdAndDeletedFalseOrderBySubmittedAtDesc(UUID assessmentId);

    long countByAssessmentIdAndDeletedFalse(UUID assessmentId);

    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.answers WHERE s.id = :id AND s.deleted = false")
    Optional<Submission> findByIdWithAnswers(@Param("id") UUID id);

    @Query("SELECT s FROM Submission s LEFT JOIN FETCH s.answers WHERE s.assessmentId = :assessmentId AND s.studentId = :studentId AND s.deleted = false")
    Optional<Submission> findByAssessmentAndStudentWithAnswers(@Param("assessmentId") UUID assessmentId, @Param("studentId") UUID studentId);
}
