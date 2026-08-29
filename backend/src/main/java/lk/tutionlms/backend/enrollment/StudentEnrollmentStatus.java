package lk.tutionlms.backend.enrollment;

import java.util.UUID;

public record StudentEnrollmentStatus(UUID batchId, EnrollmentStatus status) {
}
