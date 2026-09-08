package lk.tutionlms.backend.teacher;

import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.communication.Announcement;
import lk.tutionlms.backend.content.LearningMaterial;
import lk.tutionlms.backend.enrollment.TeacherStudentView;
import lk.tutionlms.backend.identity.*;
import lk.tutionlms.backend.scheduling.ScheduleItem;
import lk.tutionlms.backend.teacher.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/teacher")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TEACHER')")
public class TeacherController {
    private final TeacherService service;
    private final MaterialUploadService materialUploadService;

    @GetMapping("/profile")
    public Teacher profile(@AuthenticationPrincipal User u) {
        return service.teacher(u);
    }

    @GetMapping("/dashboard")
    public TeacherDashboardResponse dashboard(@AuthenticationPrincipal User u) {
        return service.dashboard(u);
    }

    @GetMapping("/batches")
    public List<Batch> batches(@AuthenticationPrincipal User u) {
        return service.batches(u);
    }

    @GetMapping("/schedules")
    public List<ScheduleItem> schedules(@AuthenticationPrincipal User u) {
        return service.schedules(u);
    }

    @GetMapping("/students")
    public List<TeacherStudentView> students(@AuthenticationPrincipal User u) {
        return service.students(u);
    }

    @GetMapping("/materials")
    public List<LearningMaterial> materials(@AuthenticationPrincipal User u) {
        return service.materials(u);
    }

    @GetMapping("/batches/{batchId}/materials")
    public List<LearningMaterial> batchMaterials(
            @AuthenticationPrincipal User u,
            @PathVariable UUID batchId) {
        return service.batchMaterials(u, batchId);
    }

    @PostMapping("/materials")
    public LearningMaterial createMaterial(@AuthenticationPrincipal User u, @Valid @RequestBody CreateMaterialRequest r) {
        return service.createMaterial(u, r);
    }

    @DeleteMapping("/materials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMaterial(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        service.deleteMaterial(user, id);
    }

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

    @GetMapping("/announcements")
    public List<Announcement> announcements(@AuthenticationPrincipal User u) {
        return service.announcements(u);
    }

    @GetMapping("/batches/{batchId}/announcements")
    public List<Announcement> batchAnnouncements(
            @AuthenticationPrincipal User u,
            @PathVariable UUID batchId) {
        return service.batchAnnouncements(u, batchId);
    }

    @PostMapping("/announcements")
    public Announcement createAnnouncement(@AuthenticationPrincipal User u, @Valid @RequestBody CreateAnnouncementRequest r) {
        return service.createAnnouncement(u, r);
    }

    @DeleteMapping("/announcements/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAnnouncement(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        service.deleteAnnouncement(user, id);
    }

    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleItem createSchedule(
            @AuthenticationPrincipal User user,
            @RequestBody CreateScheduleRequest request) {
        return service.createSchedule(user, request);
    }

    @PutMapping("/schedules/{id}")
    public ResponseEntity<ScheduleItem> updateSchedule(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateScheduleRequest request) {
        ScheduleItem updated = service.updateSchedule(user, id, request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/schedules/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSchedule(
            @AuthenticationPrincipal User user,
            @PathVariable UUID id) {
        service.deleteSchedule(user, id);
    }

}
