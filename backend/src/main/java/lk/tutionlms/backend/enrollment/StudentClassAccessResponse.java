package lk.tutionlms.backend.enrollment;

public record StudentClassAccessResponse(String message, boolean accessGranted, EnrollmentStatus status) {
}
