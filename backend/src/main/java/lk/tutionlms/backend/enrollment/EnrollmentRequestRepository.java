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

    @org.springframework.data.jpa.repository.Query("SELECT r FROM EnrollmentRequest r WHERE r.status = 'PENDING' AND r.isDeleted = false AND r.batchId IN " +
            "(SELECT b.id FROM lk.tutionlms.backend.batch.Batch b WHERE b.teacherId = :teacherId AND b.deleted = false) ORDER BY r.createdAt DESC")
    List<EnrollmentRequest> findPendingRequestsByTeacherId(@org.springframework.data.repository.query.Param("teacherId") UUID teacherId);

    List<EnrollmentRequest> findByBatchIdOrderByCreatedAtDesc(
            UUID batchId
    );
}