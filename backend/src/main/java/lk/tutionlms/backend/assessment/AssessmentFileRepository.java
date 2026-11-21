package lk.tutionlms.backend.assessment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssessmentFileRepository extends JpaRepository<AssessmentFile, UUID> {
    Optional<AssessmentFile> findByFilenameAndDeletedFalse(String filename);
}
