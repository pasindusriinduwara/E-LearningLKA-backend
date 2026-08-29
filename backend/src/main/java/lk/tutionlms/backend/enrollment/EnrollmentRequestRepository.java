package lk.tutionlms.backend.enrollment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EnrollmentRequestRepository
        extends JpaRepository<EnrollmentRequest, UUID> {

    boolean existsByStudentIdAndBatchId(
            UUID studentId,
            UUID batchId
    );

    List<EnrollmentRequest> findByStudentIdAndIsDeletedFalse(
            UUID studentId
    );

    List<EnrollmentRequest> findByStatus(
            EnrollmentStatus status
    );

    List<EnrollmentRequest> findByBatchIdOrderByCreatedAtDesc(
            UUID batchId
    );
}