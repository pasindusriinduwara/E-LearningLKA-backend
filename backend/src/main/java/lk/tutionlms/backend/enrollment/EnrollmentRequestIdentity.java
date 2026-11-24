package lk.tutionlms.backend.enrollment;

import java.util.UUID;

/** Read immutable keys without caching a decision before the student/request locks. */
public record EnrollmentRequestIdentity(UUID id, UUID studentId, UUID batchId) {
}
