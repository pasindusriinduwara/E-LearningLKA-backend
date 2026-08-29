// src/main/java/lk/tutionlms/backend/enrollment/EnrollmentController.java
package lk.tutionlms.backend.enrollment;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping("/request")
    public ResponseEntity<String> requestEnrollment(@RequestBody EnrollmentRequestDto dto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String studentEmail = auth.getName();
        
        enrollmentService.createRequest(studentEmail, dto.getBatchId());
        return ResponseEntity.ok("Enrollment request sent successfully");
    }

    @GetMapping("/pending")
    public ResponseEntity<List<EnrollmentRequest>> getPendingRequests() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String teacherEmail = auth.getName();
        
        return ResponseEntity.ok(enrollmentService.getPendingRequestsForTeacher(teacherEmail));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<String> approveEnrollment(@PathVariable UUID id) {
        enrollmentService.approveRequest(id);
        return ResponseEntity.ok("Enrollment approved");
    }
}