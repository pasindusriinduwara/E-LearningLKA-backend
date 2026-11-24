package lk.tutionlms.backend.enrollment;

import java.time.LocalDateTime;
import java.util.UUID;

/** Only the student and class details needed to review an enrollment request. */
public record TeacherEnrollmentRequestResponse(
        UUID id,
        UUID studentId,
        UUID batchId,
        EnrollmentStatus status,
        LocalDateTime createdAt,
        String studentName,
        String studentNumber,
        String batchName,
        String subject,
        boolean accessGranted,
        boolean batchActive
) {
    public TeacherEnrollmentRequestResponse(UUID id, UUID studentId, UUID batchId, EnrollmentStatus status,
            LocalDateTime createdAt, String studentName, String studentNumber, String batchName, String subject) {
        this(id, studentId, batchId, status, createdAt, studentName, studentNumber, batchName, subject, false, true);
    }

    public TeacherEnrollmentRequestResponse withAccessGranted(boolean allowed) {
        return new TeacherEnrollmentRequestResponse(id, studentId, batchId, status, createdAt,
                studentName, studentNumber, batchName, subject, allowed, batchActive);
    }
}
