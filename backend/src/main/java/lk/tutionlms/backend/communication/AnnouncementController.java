package lk.tutionlms.backend.communication;

import lk.tutionlms.backend.identity.TeacherRepository;
import lk.tutionlms.backend.identity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/announcements")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
public class AnnouncementController {

    private final AnnouncementRepository announcementRepository;
    private final TeacherRepository teacherRepository;

    @GetMapping
    public ResponseEntity<List<Announcement>> getAnnouncements(@AuthenticationPrincipal User user) {
        if (user == null) {
            throw new AccessDeniedException("Authentication required");
        }
        if ("STUDENT".equals(user.getUserType())) {
            return ResponseEntity.ok(announcementRepository.findByStudentUserId(user.getId()));
        }
        if ("TEACHER".equals(user.getUserType())) {
            UUID teacherId = teacherRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new AccessDeniedException("Teacher profile required"))
                    .getId();
            return ResponseEntity.ok(announcementRepository.findByTeacherId(teacherId));
        }
        throw new AccessDeniedException("Announcement access is restricted to students and teachers");
    }

    @GetMapping("/batch/{batchId}")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<Announcement>> getBatchAnnouncements(
            @AuthenticationPrincipal User user,
            @PathVariable UUID batchId) {
        if (user == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return ResponseEntity.ok(announcementRepository.findByBatchIdAndStudentUserId(batchId, user.getId()));
    }
}
