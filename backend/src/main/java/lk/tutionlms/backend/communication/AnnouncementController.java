package lk.tutionlms.backend.communication;

import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.StudentRepository;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;

    @GetMapping
    public ResponseEntity<List<Announcement>> getAnnouncements(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            User user = userRepository.findByEmail(authentication.getName()).orElse(null);
            if (user != null) {
                Student student = studentRepository.findByUserId(user.getId()).orElse(null);
                if (student != null) {
                    List<UUID> activeBatchIds = enrollmentRepository.findActiveBatchIdsByStudentId(student.getId());
                    if (activeBatchIds.isEmpty()) {
                        return ResponseEntity.ok(Collections.emptyList());
                    }
                    return ResponseEntity.ok(announcementRepository.findByBatchIdInAndDeletedFalseOrderByCreatedAtDesc(activeBatchIds));
                }
            }
        }
        return ResponseEntity.ok(announcementRepository.findByDeletedFalse());
    }

    @GetMapping("/batch/{batchId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<Announcement>> getBatchAnnouncements(
            @AuthenticationPrincipal User user,
            @PathVariable UUID batchId) {
        return ResponseEntity.ok(announcementRepository.findByBatchIdAndStudentUserId(batchId, user.getId()));
    }
}
