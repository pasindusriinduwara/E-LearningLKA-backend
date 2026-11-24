package lk.tutionlms.backend.enrollment;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.StudentRepository;
import lk.tutionlms.backend.identity.Teacher;
import lk.tutionlms.backend.identity.TeacherRepository;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.identity.UserRepository;
import lk.tutionlms.backend.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class EnrollmentService {
    private final EnrollmentRequestRepository enrollmentRequestRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final BatchRepository batchRepository;
    private final TeacherRepository teacherRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<StudentEnrollmentStatus> getStudentStatuses(String studentEmail) {
        Student student = getStudent(studentEmail);
        Set<UUID> allowed = new HashSet<>(enrollmentRepository.findActiveBatchIdsByStudentId(student.getId()));
        return enrollmentRequestRepository.findByStudentIdAndIsDeletedFalse(student.getId()).stream()
                .map(request -> new StudentEnrollmentStatus(request.getBatchId(), allowed.contains(request.getBatchId())
                        ? EnrollmentStatus.APPROVED : request.getStatus() == EnrollmentStatus.APPROVED
                        ? EnrollmentStatus.REJECTED : request.getStatus())).toList();
    }

    @Transactional
    public void createRequest(String studentEmail, String batchId) {
        UUID batchUuid = parseBatchId(batchId);
        Student student = lockStudent(getStudent(studentEmail).getId());
        Batch batch = batchRepository.findById(batchUuid).filter(value -> value.isActive() && !value.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        if (enrollmentRepository.isStudentActiveInBatch(student.getId(), batchUuid)) {
            throw new IllegalStateException("You are already enrolled in this class");
        }
        var existing = enrollmentRequestRepository
                .findTopByStudentIdAndBatchIdAndIsDeletedFalseOrderByCreatedAtDesc(student.getId(), batchUuid);
        EnrollmentRequest request = existing.orElseGet(EnrollmentRequest::new);
        if (existing.isPresent() && request.getStatus() == EnrollmentStatus.PENDING) {
            throw new IllegalStateException("Enrollment request is already pending review");
        }
        request.setStudentId(student.getId());
        request.setBatchId(batch.getId());
        request.setStatus(EnrollmentStatus.PENDING);
        request.setUpdatedAt(LocalDateTime.now());
        enrollmentRequestRepository.save(request);
        notificationService.notifyBatchTeacher(batchUuid, "ENROLLMENT_REQUESTED", "New enrollment request",
                "A student has requested to join your class. Review the request in Enrollments.", "/teacher/enrollments");
    }

    @Transactional
    public void cancelRequest(String studentEmail, String batchId) {
        UUID batchUuid = parseBatchId(batchId);
        Student student = lockStudent(getStudent(studentEmail).getId());
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
        UUID batchUuid = parseBatchId(batchId);
        Student student = lockStudent(getStudent(studentEmail).getId());
        List<Enrollment> enrollments = enrollmentRepository.findByStudentAndBatchForUpdate(student.getId(), batchUuid);
        if (enrollments.stream().noneMatch(enrollment -> "ACTIVE".equals(enrollment.getStatus()))) {
            throw new IllegalStateException("You are not currently enrolled in this class");
        }
        setEnrollmentAccess(student.getId(), batchUuid, false, enrollments);
        enrollmentRequestRepository.findByStudentIdAndIsDeletedFalse(student.getId()).stream()
                .filter(request -> request.getBatchId().equals(batchUuid)).forEach(request -> {
                    request.setDeleted(true);
                    enrollmentRequestRepository.delete(request);
                });
    }

    @Transactional(readOnly = true)
    public List<TeacherEnrollmentRequestResponse> getPendingRequestsForTeacher(String teacherEmail) {
        return withEffectiveAccess(enrollmentRequestRepository.findPendingRequestsByTeacherId(getTeacher(teacherEmail).getId()));
    }

    @Transactional(readOnly = true)
    public List<TeacherEnrollmentRequestResponse> getRequestsForTeacher(String teacherEmail) {
        UUID teacherId = getTeacher(teacherEmail).getId();
        List<TeacherEnrollmentRequestResponse> requests = new ArrayList<>(enrollmentRequestRepository.findRequestsByTeacherId(teacherId));
        Set<String> included = new HashSet<>();
        requests.forEach(request -> included.add(request.studentId() + ":" + request.batchId()));
        // Read-only compatibility for active enrollments created before enrollment requests existed.
        for (TeacherEnrollmentRequestResponse legacy : enrollmentRepository.findLegacyRequestsByTeacherId(teacherId)) {
            if (included.add(legacy.studentId() + ":" + legacy.batchId())) requests.add(legacy);
        }
        return withEffectiveAccess(requests);
    }

    private List<TeacherEnrollmentRequestResponse> withEffectiveAccess(List<TeacherEnrollmentRequestResponse> requests) {
        Map<UUID, Set<UUID>> studentBatches = new HashMap<>();
        return requests.stream().map(request -> request.withAccessGranted(request.batchActive()
                && studentBatches.computeIfAbsent(request.studentId(), id ->
                new HashSet<>(enrollmentRepository.findActiveBatchIdsByStudentId(id))).contains(request.batchId()))).toList();
    }

    @Transactional(readOnly = true)
    public List<BatchEnrollmentResponse> getBatchEnrollments(String teacherEmail, UUID batchId) {
        Teacher teacher = getTeacher(teacherEmail);
        Batch batch = batchRepository.findById(batchId).filter(value -> !value.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));
        if (!teacher.getId().equals(batch.getTeacherId())) throw new AccessDeniedException("This batch does not belong to the logged-in teacher");
        return enrollmentRequestRepository.findByBatchIdOrderByCreatedAtDesc(batchId).stream().map(request -> {
            Student student = studentRepository.findById(request.getStudentId()).filter(value -> !value.isDeleted()).orElse(null);
            return new BatchEnrollmentResponse(request.getId(), request.getStudentId(),
                    student == null ? "Unknown student" : student.getName(),
                    student == null ? "Unavailable" : student.getStudentId(), request.getStatus(), request.getCreatedAt());
        }).toList();
    }

    @Transactional
    public void approveRequest(String teacherEmail, UUID requestId) {
        EnrollmentRequest request = getTeacherOwnedRequest(teacherEmail, requestId);
        requirePending(request, "approved");
        validateGrant(request);
        setEnrollmentAccess(request, true);
        saveDecision(request, EnrollmentStatus.APPROVED);
        notifyEnrollmentDecision(request, true);
    }

    /** Retained for internal callers; HTTP callers must use the teacher-scoped overload. */
    @Transactional
    public void approveRequest(UUID requestId) {
        EnrollmentRequest request = lockRequestAndStudent(requestId);
        requirePending(request, "approved");
        validateGrant(request);
        setEnrollmentAccess(request, true);
        saveDecision(request, EnrollmentStatus.APPROVED);
        notifyEnrollmentDecision(request, true);
    }

    @Transactional
    public void rejectRequest(String teacherEmail, UUID requestId) {
        EnrollmentRequest request = getTeacherOwnedRequest(teacherEmail, requestId);
        requirePending(request, "rejected");
        setEnrollmentAccess(request, false);
        saveDecision(request, EnrollmentStatus.REJECTED);
        notifyEnrollmentDecision(request, false);
    }

    @Transactional
    public StudentClassAccessResponse setStudentClassAccess(String teacherEmail, UUID requestId, Boolean allowed) {
        if (allowed == null) throw new IllegalArgumentException("allowed must be true or false");
        EnrollmentRequest request = getTeacherOwnedAccessRequest(teacherEmail, requestId);
        if (allowed) validateGrant(request);
        EnrollmentStatus desired = allowed ? EnrollmentStatus.APPROVED : EnrollmentStatus.REJECTED;
        boolean changed = setEnrollmentAccess(request, allowed);
        changed |= saveDecision(request, desired);
        if (changed) studentRepository.findById(request.getStudentId()).filter(student -> !student.isDeleted())
                .ifPresent(student -> notificationService.notifyUser(student.getUserId(),
                        allowed ? "ACCESS_GRANTED" : "ACCESS_REVOKED",
                        allowed ? "Class access allowed" : "Class access denied",
                        allowed ? "Your teacher has allowed access to the class." : "Your teacher has denied access to the class.",
                        allowed ? "/classes/" + request.getBatchId() : "/enrollment/" + request.getBatchId()));
        return new StudentClassAccessResponse(allowed ? "Class access allowed" : "Class access denied", allowed, desired);
    }

    private boolean setEnrollmentAccess(EnrollmentRequest request, boolean allowed) {
        return setEnrollmentAccess(request.getStudentId(), request.getBatchId(), allowed,
                enrollmentRepository.findByStudentAndBatchForUpdate(request.getStudentId(), request.getBatchId()));
    }

    private boolean setEnrollmentAccess(UUID studentId, UUID batchId, boolean allowed, List<Enrollment> rows) {
        Enrollment selected = allowed ? rows.stream().filter(row -> "ACTIVE".equals(row.getStatus())).findFirst()
                .orElse(rows.isEmpty() ? null : rows.getFirst()) : null;
        boolean changed = false;
        if (allowed && selected == null) {
            selected = Enrollment.builder().studentId(studentId).batchId(batchId)
                    .enrolledDate(LocalDate.now()).status("ACTIVE").build();
            enrollmentRepository.save(selected);
            changed = true;
        }
        for (Enrollment row : rows) {
            String desired = row == selected ? "ACTIVE" : "DROPPED";
            if (!desired.equals(row.getStatus())) {
                row.setStatus(desired);
                if ("ACTIVE".equals(desired) && row.getEnrolledDate() == null) row.setEnrolledDate(LocalDate.now());
                enrollmentRepository.save(row);
                changed = true;
            }
        }
        return changed;
    }

    private boolean saveDecision(EnrollmentRequest request, EnrollmentStatus desired) {
        boolean changed = updateDecision(request, desired);
        // Legacy duplicate requests must not retain a contradictory effective decision.
        for (EnrollmentRequest duplicate : enrollmentRequestRepository.findByStudentIdAndIsDeletedFalse(request.getStudentId())) {
            if (!duplicate.getId().equals(request.getId()) && duplicate.getBatchId().equals(request.getBatchId())) {
                changed |= updateDecision(duplicate, desired);
            }
        }
        return changed;
    }

    private boolean updateDecision(EnrollmentRequest request, EnrollmentStatus desired) {
        if (request.getStatus() == desired) return false;
        request.setStatus(desired);
        request.setUpdatedAt(LocalDateTime.now());
        enrollmentRequestRepository.save(request);
        return true;
    }

    private void requirePending(EnrollmentRequest request, String decision) {
        if (request.getStatus() != EnrollmentStatus.PENDING) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Only pending requests can be " + decision);
    }

    private void validateGrant(EnrollmentRequest request) {
        Batch batch = batchRepository.findById(request.getBatchId()).filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found"));
        if (!batch.isActive()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Access cannot be allowed for an inactive class");
        Student student = studentRepository.findById(request.getStudentId()).filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        userRepository.findById(student.getUserId()).filter(User::isEnabled)
                .filter(value -> "STUDENT".equals(value.getUserType()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Student account is unavailable"));
    }

    private void notifyEnrollmentDecision(EnrollmentRequest request, boolean approved) {
        studentRepository.findById(request.getStudentId()).filter(student -> !student.isDeleted())
                .ifPresent(student -> notificationService.notifyUser(student.getUserId(),
                        approved ? "ENROLLMENT_APPROVED" : "ENROLLMENT_REJECTED",
                        approved ? "Enrollment approved" : "Enrollment request declined",
                        approved ? "Your enrollment request was approved. You can now access the class."
                                : "Your enrollment request was declined. View the class for more information.",
                        approved ? "/classes/" + request.getBatchId() : "/enrollment/" + request.getBatchId()));
    }

    private EnrollmentRequest getTeacherOwnedRequest(String teacherEmail, UUID requestId) {
        Teacher teacher = getTeacher(teacherEmail);
        EnrollmentRequestIdentity initial = enrollmentRequestRepository.findIdentityById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment request not found"));
        requireOwnedBatch(teacher, initial.batchId());
        EnrollmentRequest locked = lockRequestAndStudent(initial);
        requireOwnedBatch(teacher, locked.getBatchId());
        return locked;
    }

    private EnrollmentRequest getTeacherOwnedAccessRequest(String teacherEmail, UUID id) {
        if (enrollmentRequestRepository.findIdentityById(id).isPresent()) return getTeacherOwnedRequest(teacherEmail, id);
        Teacher teacher = getTeacher(teacherEmail);
        EnrollmentIdentity legacy = enrollmentRepository.findIdentityById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment request not found"));
        requireOwnedBatch(teacher, legacy.batchId());
        lockStudent(legacy.studentId());
        EnrollmentIdentity fresh = enrollmentRepository.findIdentityById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment request not found"));
        if (!fresh.studentId().equals(legacy.studentId()) || !fresh.batchId().equals(legacy.batchId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Enrollment changed. Refresh and retry.");
        }
        requireOwnedBatch(teacher, fresh.batchId());
        // The student lock serializes legacy requests without reversing request/enrollment lock order.
        var existing = enrollmentRequestRepository.findTopByStudentIdAndBatchIdAndIsDeletedFalseOrderByCreatedAtDesc(
                fresh.studentId(), fresh.batchId());
        if (existing.isPresent()) return lockRequestAndStudent(new EnrollmentRequestIdentity(
                existing.get().getId(), fresh.studentId(), fresh.batchId()));
        EnrollmentRequest request = new EnrollmentRequest();
        request.setStudentId(fresh.studentId()); request.setBatchId(fresh.batchId());
        return enrollmentRequestRepository.save(request);
    }

    private void requireOwnedBatch(Teacher teacher, UUID batchId) {
        Batch batch = batchRepository.findById(batchId).filter(value -> !value.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment request not found"));
        if (!teacher.getId().equals(batch.getTeacherId())) throw new AccessDeniedException(
                "This request does not belong to the logged-in teacher");
    }

    private EnrollmentRequest lockRequestAndStudent(UUID requestId) {
        EnrollmentRequestIdentity initial = enrollmentRequestRepository.findIdentityById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment request not found"));
        return lockRequestAndStudent(initial);
    }

    private EnrollmentRequest lockRequestAndStudent(EnrollmentRequestIdentity initial) {
        // Student first, request second everywhere, including leave/cancel/re-request.
        lockStudent(initial.studentId());
        EnrollmentRequest locked = enrollmentRequestRepository.findByIdForUpdate(initial.id())
                .filter(request -> !request.isDeleted()).orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Enrollment request not found"));
        if (!locked.getStudentId().equals(initial.studentId()) || !locked.getBatchId().equals(initial.batchId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Enrollment request changed. Refresh and retry.");
        }
        return locked;
    }

    private Student lockStudent(UUID studentId) {
        return studentRepository.findByIdForUpdate(studentId).filter(student -> !student.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
    }

    private Student getStudent(String email) {
        User user = userRepository.findByEmail(email).filter(User::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Student account is unavailable"));
        return studentRepository.findByUserId(user.getId()).filter(student -> !student.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found"));
    }

    private Teacher getTeacher(String email) {
        User user = userRepository.findByEmail(email).filter(User::isEnabled)
                .orElseThrow(() -> new AccessDeniedException("Teacher account is unavailable"));
        return teacherRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Teacher profile not found"));
    }

    private UUID parseBatchId(String batchId) {
        if (batchId == null || batchId.isBlank()) throw new IllegalArgumentException("Batch ID is required");
        try { return UUID.fromString(batchId); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("Invalid batch ID"); }
    }
}
