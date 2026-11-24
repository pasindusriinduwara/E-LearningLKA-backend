package lk.tutionlms.backend.enrollment;

/** Nullable wrapper distinguishes an explicit denial from a missing field. */
public record StudentClassAccessRequest(Boolean allowed) {
}
