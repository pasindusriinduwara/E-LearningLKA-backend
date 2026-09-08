package lk.tutionlms.backend.communication;

import lombok.RequiredArgsConstructor;
import lk.tutionlms.backend.identity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
public class AnnouncementController {

    private final AnnouncementRepository announcementRepository;

    @GetMapping
    public ResponseEntity<List<Announcement>> getAnnouncements() {
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
