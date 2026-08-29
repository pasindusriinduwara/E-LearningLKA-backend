// src/main/java/lk/tutionlms/backend/enrollment/EnrollmentRequestRepository.java
package lk.tutionlms.backend.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnrollmentRequestRepository extends JpaRepository<EnrollmentRequest, UUID> {
    
    boolean existsByStudentIdAndBatchId(UUID studentId, UUID batchId);
    
    List<EnrollmentRequest> findByStatus(EnrollmentStatus status);
    
    // If you need to filter by a specific teacher's batches
    // List<EnrollmentRequest> findByStatusAndBatchIdIn(EnrollmentStatus status, List<String> batchIds);
}
