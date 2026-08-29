package lk.tutionlms.backend.teacher;

import lk.tutionlms.backend.academic.*;
import lk.tutionlms.backend.communication.Announcement;
import lk.tutionlms.backend.content.LearningMaterial;
import lk.tutionlms.backend.identity.*;
import lk.tutionlms.backend.sheduling.ScheduleItem;
import lk.tutionlms.backend.teacher.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;
import java.util.List;

@RestController @RequestMapping("/api/v1/teacher") @RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class TeacherController {
    private final TeacherService service;
    private final MaterialUploadService materialUploadService;
    @GetMapping("/profile") public Teacher profile(@AuthenticationPrincipal User u) { return service.teacher(u); }
    @GetMapping("/dashboard") public TeacherDashboardResponse dashboard(@AuthenticationPrincipal User u) { return service.dashboard(u); }
    @GetMapping("/batches") public List<Batch> batches(@AuthenticationPrincipal User u) { return service.batches(u); }
    @GetMapping("/schedules") public List<ScheduleItem> schedules(@AuthenticationPrincipal User u) { return service.schedules(u); }
    @GetMapping("/students") public List<TeacherStudentView> students(@AuthenticationPrincipal User u) { return service.students(u); }
    @GetMapping("/materials") public List<LearningMaterial> materials(@AuthenticationPrincipal User u) { return service.materials(u); }
    @PostMapping("/materials") public LearningMaterial createMaterial(@AuthenticationPrincipal User u, @RequestBody CreateMaterialRequest r) { return service.createMaterial(u, r); }
    @PostMapping(value = "/materials/upload", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public MaterialUploadResponse uploadMaterial(
            @AuthenticationPrincipal User user,
            @RequestParam("batchId") UUID batchId,
            @RequestParam("title") String title,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestPart("file") MultipartFile file) {
        return materialUploadService.upload(user, batchId, title, subject, file);
    }
    @GetMapping("/announcements") public List<Announcement> announcements(@AuthenticationPrincipal User u) { return service.announcements(u); }
    @PostMapping("/announcements") public Announcement createAnnouncement(@AuthenticationPrincipal User u, @RequestBody CreateAnnouncementRequest r) { return service.createAnnouncement(u, r); }
}
