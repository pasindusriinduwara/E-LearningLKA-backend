package lk.tutionlms.backend.enrollment;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @GetMapping("/my-status")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<StudentEnrollmentStatus>> getMyStatuses() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return ResponseEntity.ok(
                enrollmentService.getStudentStatuses(
                        authentication.getName()
                )
        );
    }

    @PostMapping("/request")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, String>> requestEnrollment(
            @RequestBody EnrollmentRequestDto dto
    ) {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        enrollmentService.createRequest(
                authentication.getName(),
                dto.getBatchId()
        );

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Enrollment request sent successfully"
                )
        );
    }

    @GetMapping("/teacher/batches/{batchId}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<BatchEnrollmentResponse>>
    getBatchEnrollments(@PathVariable UUID batchId) {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return ResponseEntity.ok(
                enrollmentService.getBatchEnrollments(
                        authentication.getName(),
                        batchId
                )
        );
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<List<EnrollmentRequest>>
    getPendingRequests() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        return ResponseEntity.ok(
                enrollmentService.getPendingRequestsForTeacher(
                        authentication.getName()
                )
        );
    }

    @PutMapping("/teacher/requests/{id}/approve")
@PreAuthorize("hasRole('TEACHER')")
public ResponseEntity<Map<String, String>> approveTeacherRequest(
        @PathVariable UUID id,
        Authentication authentication
) {
    enrollmentService.approveRequest(
            authentication.getName(),
            id
    );

    return ResponseEntity.ok(
            Map.of("message", "Enrollment approved")
    );
}

@PutMapping("/teacher/requests/{id}/reject")
@PreAuthorize("hasRole('TEACHER')")
public ResponseEntity<Map<String, String>> rejectTeacherRequest(
        @PathVariable UUID id,
        Authentication authentication
) {
    enrollmentService.rejectRequest(
            authentication.getName(),
            id
    );

    return ResponseEntity.ok(
            Map.of("message", "Enrollment rejected")
    );
}
        
}