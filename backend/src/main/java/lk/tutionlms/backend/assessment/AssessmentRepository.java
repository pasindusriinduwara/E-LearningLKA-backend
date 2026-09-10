package lk.tutionlms.backend.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    List<Assessment> findByBatchIdAndDeletedFalseOrderByCreatedAtDesc(UUID batchId);

    @Query("SELECT a FROM Assessment a LEFT JOIN FETCH a.questions q LEFT JOIN FETCH q.options WHERE a.id = :id AND a.deleted = false")
    Optional<Assessment> findByIdWithQuestionsAndOptions(@Param("id") UUID id);
}
