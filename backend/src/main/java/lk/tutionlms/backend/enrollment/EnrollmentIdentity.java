package lk.tutionlms.backend.enrollment;

import java.util.UUID;

/** Immutable keys for a legacy enrollment that predates request-based approvals. */
public record EnrollmentIdentity(UUID id, UUID studentId, UUID batchId) {
}
