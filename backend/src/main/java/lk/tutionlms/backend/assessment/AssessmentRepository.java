package lk.tutionlms.backend.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Assessment a WHERE a.id = :id AND a.deleted = false")
    Optional<Assessment> findByIdForUpdate(@Param("id") UUID id);

    List<Assessment> findByBatchIdAndDeletedFalseOrderByCreatedAtDesc(UUID batchId);

    List<Assessment> findByDeletedFalseOrderByCreatedAtDesc();

    List<Assessment> findByBatchIdInAndDeletedFalseOrderByCreatedAtDesc(List<UUID> batchIds);

    List<Assessment> findByAttachmentUrlAndDeletedFalse(String attachmentUrl);

    @Query("SELECT a FROM Assessment a WHERE a.deleted = false AND (a.hidden IS NULL OR a.hidden = false) ORDER BY a.createdAt DESC")
    List<Assessment> findByDeletedFalseAndHiddenFalseOrderByCreatedAtDesc();

    @Query("SELECT a FROM Assessment a WHERE a.deleted = false AND (a.hidden IS NULL OR a.hidden = false) AND a.batchId IN :batchIds ORDER BY a.createdAt DESC")
    List<Assessment> findByBatchIdInAndDeletedFalseAndHiddenFalseOrderByCreatedAtDesc(@Param("batchIds") List<UUID> batchIds);
}
