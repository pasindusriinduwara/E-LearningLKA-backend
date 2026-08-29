package lk.tutionlms.backend.enrollment;

import java.time.LocalDateTime;
import java.util.UUID;

public record BatchEnrollmentResponse(
        UUID requestId,
        UUID studentId,
        String studentName,
        String studentNumber,
        EnrollmentStatus status,
        LocalDateTime requestedAt
) {
}