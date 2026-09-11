package lk.tutionlms.backend.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByAssessmentIdAndDeletedFalseOrderByDisplayOrderAsc(UUID assessmentId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT q FROM Question q LEFT JOIN FETCH q.options WHERE q.assessment.id = :assessmentId AND q.deleted = false ORDER BY q.displayOrder ASC")
    List<Question> findByAssessmentIdWithOptions(@org.springframework.data.repository.query.Param("assessmentId") UUID assessmentId);
}
