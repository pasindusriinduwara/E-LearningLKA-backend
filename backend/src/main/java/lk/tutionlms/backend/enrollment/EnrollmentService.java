// src/main/java/lk/tutionlms/backend/enrollment/EnrollmentService.java
package lk.tutionlms.backend.enrollment;

import lk.tutionlms.backend.academic.Enrollment;
import lk.tutionlms.backend.academic.EnrollmentRepository;
import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.StudentRepository;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRequestRepository enrollmentRequestRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;

    @Transactional
    public void createRequest(String studentEmail, String batchId) {
        
        // 1. Fetch User by email
        User user = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 2. Fetch Student by user ID to get the actual Student UUID
        Student student = studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found"));

        UUID studentUuid = student.getId();
        UUID batchUuid = UUID.fromString(batchId);

        // 3. Check for duplicate requests
        boolean alreadyRequested = enrollmentRequestRepository
                .existsByStudentIdAndBatchId(studentUuid, batchUuid);

        if (alreadyRequested) {
            throw new IllegalStateException("Enrollment request already exists for this batch.");
        }

        EnrollmentRequest request = new EnrollmentRequest();
        request.setStudentId(studentUuid);
        request.setBatchId(batchUuid);
        request.setStatus(EnrollmentStatus.PENDING);

        enrollmentRequestRepository.save(request);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentRequest> getPendingRequestsForTeacher(String teacherEmail) {
        // NOTE: Currently fetches all pending requests. 
        // To filter by teacher, you would join Batch and Teacher entities here.
        return enrollmentRequestRepository.findByStatus(EnrollmentStatus.PENDING);
    }

    @Transactional
    public void approveRequest(UUID requestId) {
        EnrollmentRequest request = enrollmentRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment request not found"));

        if (request.getStatus() != EnrollmentStatus.PENDING) {
            throw new IllegalStateException("Only pending requests can be approved.");
        }

        // Update request status
        request.setStatus(EnrollmentStatus.APPROVED);
        enrollmentRequestRepository.save(request);

        // Insert actual enrollment record to enrollments table
        enrollmentRepository.save(Enrollment.builder()
                .studentId(request.getStudentId())
                .batchId(request.getBatchId())
                .enrolledDate(LocalDate.now())
                .status("ACTIVE")
                .build());
    }
}