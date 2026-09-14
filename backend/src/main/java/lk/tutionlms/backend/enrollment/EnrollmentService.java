package lk.tutionlms.backend.enrollment;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.StudentRepository;
import lk.tutionlms.backend.identity.Teacher;
import lk.tutionlms.backend.identity.TeacherRepository;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.identity.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRequestRepository enrollmentRequestRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;
    private final TeacherRepository teacherRepository;

    @Transactional(readOnly = true)
    public List<StudentEnrollmentStatus> getStudentStatuses(String studentEmail) {
        User user = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Student student = studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found"));

        return enrollmentRequestRepository.findByStudentIdAndIsDeletedFalse(student.getId())
                .stream()
                .map(request -> new StudentEnrollmentStatus(request.getBatchId(), request.getStatus()))
                .collect(Collectors.toList());
    }

    @Transactional
    public void createRequest(String studentEmail, String batchId) {

        if (batchId == null || batchId.isBlank()) {
            throw new IllegalArgumentException("Batch ID is required");
        }

        User user = userRepository.findByEmail(studentEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException("User not found"));

        Student student = studentRepository.findByUserId(user.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Student profile not found"));

        UUID batchUuid;

        try {
            batchUuid = UUID.fromString(batchId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid batch ID");
        }

        if (!batchRepository.existsById(batchUuid)) {
            throw new IllegalArgumentException("Batch not found");
        }

        UUID studentUuid = student.getId();

        boolean isAlreadyActive = enrollmentRepository.isStudentActiveInBatch(studentUuid, batchUuid);
        if (isAlreadyActive) {
            throw new IllegalStateException("You are already enrolled in this class");
        }

        var existingRequestOpt = enrollmentRequestRepository
                .findTopByStudentIdAndBatchIdAndIsDeletedFalseOrderByCreatedAtDesc(studentUuid, batchUuid);

        if (existingRequestOpt.isPresent()) {
            EnrollmentRequest existing = existingRequestOpt.get();
            if (existing.getStatus() == EnrollmentStatus.PENDING) {
                throw new IllegalStateException("Enrollment request is already pending review");
            }
            if (existing.getStatus() == EnrollmentStatus.APPROVED) {
                throw new IllegalStateException("You are already enrolled in this class");
            }
            if (existing.getStatus() == EnrollmentStatus.REJECTED) {
                existing.setStatus(EnrollmentStatus.PENDING);
                existing.setUpdatedAt(java.time.LocalDateTime.now());
                enrollmentRequestRepository.save(existing);
                return;
            }
        }

        EnrollmentRequest request = new EnrollmentRequest();
        request.setStudentId(studentUuid);
        request.setBatchId(batchUuid);
        request.setStatus(EnrollmentStatus.PENDING);

        enrollmentRequestRepository.save(request);
    }

    @Transactional
    public void cancelRequest(String studentEmail, String batchId) {
        if (batchId == null || batchId.isBlank()) {
            throw new IllegalArgumentException("Batch ID is required");
        }

        User user = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Student student = studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found"));

        UUID batchUuid;
        try {
            batchUuid = UUID.fromString(batchId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid batch ID format");
        }

        EnrollmentRequest request = enrollmentRequestRepository
                .findTopByStudentIdAndBatchIdAndIsDeletedFalseOrderByCreatedAtDesc(student.getId(), batchUuid)
                .orElseThrow(() -> new IllegalArgumentException("No enrollment request found for this batch"));

        if (request.getStatus() != EnrollmentStatus.PENDING) {
            throw new IllegalStateException("Only pending enrollment requests can be cancelled");
        }

        request.setDeleted(true);
        enrollmentRequestRepository.delete(request);
    }

    @Transactional
    public void leaveClass(String studentEmail, String batchId) {
        if (batchId == null || batchId.isBlank()) {
            throw new IllegalArgumentException("Batch ID is required");
        }

        User user = userRepository.findByEmail(studentEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Student student = studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found"));

        UUID batchUuid;
        try {
            batchUuid = UUID.fromString(batchId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid batch ID format");
        }

        List<Enrollment> enrollments = enrollmentRepository
                .findByStudentIdAndBatchIdAndDeletedFalse(student.getId(), batchUuid);

        if (enrollments.isEmpty()) {
            throw new IllegalStateException("You are not currently enrolled in this class");
        }

        for (Enrollment enrollment : enrollments) {
            enrollment.setStatus("DROPPED");
            enrollment.setDeleted(true);
            enrollmentRepository.save(enrollment);
        }

        List<EnrollmentRequest> requests = enrollmentRequestRepository
                .findByStudentIdAndIsDeletedFalse(student.getId())
                .stream()
                .filter(r -> r.getBatchId().equals(batchUuid))
                .toList();

        for (EnrollmentRequest req : requests) {
            req.setDeleted(true);
            enrollmentRequestRepository.delete(req);
        }
    }

    @Transactional(readOnly = true)
    public List<EnrollmentRequest> getPendingRequestsForTeacher(
            String teacherEmail) {

        User teacherUser = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Teacher teacher = teacherRepository.findByUserId(teacherUser.getId())
                .orElseThrow(() -> new IllegalArgumentException("Teacher profile not found"));

        return enrollmentRequestRepository.findPendingRequestsByTeacherId(teacher.getId());
    }

    @Transactional
    public void approveRequest(UUID requestId) {

        EnrollmentRequest request =
                enrollmentRequestRepository.findById(requestId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Enrollment request not found"
                                ));

        if (request.getStatus() != EnrollmentStatus.PENDING) {
            throw new IllegalStateException(
                    "Only pending requests can be approved"
            );
        }

        request.setStatus(EnrollmentStatus.APPROVED);
        enrollmentRequestRepository.save(request);

        List<Enrollment> activeEnrollments =
                enrollmentRepository.findByStudentIdAndBatchIdAndDeletedFalse(
                        request.getStudentId(),
                        request.getBatchId()
                );

        if (activeEnrollments.isEmpty()) {
            enrollmentRepository.save(
                    Enrollment.builder()
                            .studentId(request.getStudentId())
                            .batchId(request.getBatchId())
                            .enrolledDate(LocalDate.now())
                            .status("ACTIVE")
                            .build()
            );
        }
    }
    @Transactional(readOnly = true)
public List<BatchEnrollmentResponse> getBatchEnrollments(
        String teacherEmail,
        UUID batchId
) {
    User teacherUser = userRepository.findByEmail(teacherEmail)
            .orElseThrow(() ->
                    new IllegalArgumentException("User not found")
            );

    Teacher teacher = teacherRepository.findByUserId(teacherUser.getId())
            .orElseThrow(() ->
                    new IllegalArgumentException("Teacher profile not found")
            );

    Batch batch = batchRepository.findById(batchId)
            .orElseThrow(() ->
                    new IllegalArgumentException("Batch not found")
            );

    if (!teacher.getId().equals(batch.getTeacherId())) {
        throw new IllegalArgumentException(
                "This batch does not belong to the logged-in teacher"
        );
    }

    return enrollmentRequestRepository
            .findByBatchIdOrderByCreatedAtDesc(batchId)
            .stream()
            .map(request -> {
                Student student = studentRepository
                        .findById(request.getStudentId())
                        .orElse(null);

                return new BatchEnrollmentResponse(
                        request.getId(),
                        request.getStudentId(),
                        student == null
                                ? "Unknown student"
                                : student.getName(),
                        student == null
                                ? "Unavailable"
                                : student.getStudentId(),
                        request.getStatus(),
                        request.getCreatedAt()
                );
            })
            .toList();
}
@Transactional
public void approveRequest(
        String teacherEmail,
        UUID requestId
) {
    EnrollmentRequest request =
            getTeacherOwnedRequest(teacherEmail, requestId);

    if (request.getStatus() != EnrollmentStatus.PENDING) {
        throw new IllegalStateException(
                "Only pending requests can be approved"
        );
    }

    request.setStatus(EnrollmentStatus.APPROVED);
    enrollmentRequestRepository.save(request);

    List<Enrollment> activeEnrollments =
            enrollmentRepository.findByStudentIdAndBatchIdAndDeletedFalse(
                    request.getStudentId(),
                    request.getBatchId()
            );

    if (activeEnrollments.isEmpty()) {
        enrollmentRepository.save(
                Enrollment.builder()
                        .studentId(request.getStudentId())
                        .batchId(request.getBatchId())
                        .enrolledDate(LocalDate.now())
                        .status("ACTIVE")
                        .build()
        );
    }
}

@Transactional
public void rejectRequest(
        String teacherEmail,
        UUID requestId
) {
    EnrollmentRequest request =
            getTeacherOwnedRequest(teacherEmail, requestId);

    if (request.getStatus() != EnrollmentStatus.PENDING) {
        throw new IllegalStateException(
                "Only pending requests can be rejected"
        );
    }

    request.setStatus(EnrollmentStatus.REJECTED);
    enrollmentRequestRepository.save(request);
}

private EnrollmentRequest getTeacherOwnedRequest(
        String teacherEmail,
        UUID requestId
) {
    User teacherUser = userRepository.findByEmail(teacherEmail)
            .orElseThrow(() ->
                    new IllegalArgumentException("User not found"));

    Teacher teacher = teacherRepository
            .findByUserId(teacherUser.getId())
            .orElseThrow(() ->
                    new IllegalArgumentException(
                            "Teacher profile not found"));

    EnrollmentRequest request =
            enrollmentRequestRepository.findById(requestId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Enrollment request not found"));

    Batch batch = batchRepository.findById(request.getBatchId())
            .orElseThrow(() ->
                    new IllegalArgumentException("Batch not found"));

    if (!teacher.getId().equals(batch.getTeacherId())) {
        throw new SecurityException(
                "This request does not belong to the logged-in teacher"
        );
    }

    return request;
}

}
