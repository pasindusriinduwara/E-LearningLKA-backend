package lk.tutionlms.backend.teacher;

import lk.tutionlms.backend.academic.*;
import lk.tutionlms.backend.communication.*;
import lk.tutionlms.backend.content.*;
import lk.tutionlms.backend.identity.*;
import lk.tutionlms.backend.sheduling.*;
import lk.tutionlms.backend.teacher.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class TeacherService {
    private final TeacherRepository teacherRepository;
    private final BatchRepository batchRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ScheduleRepository scheduleRepository;
    private final MaterialRepository materialRepository;
    private final AnnouncementRepository announcementRepository;

    public Teacher teacher(User user) {
        return teacherRepository.findByUserId(user.getId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher profile not found"));
    }
    public List<Batch> batches(User user) { return batchRepository.findByTeacherIdAndDeletedFalse(teacher(user).getId()); }
    public List<ScheduleItem> schedules(User user) { return scheduleRepository.findByTeacherId(teacher(user).getId()); }
    public List<TeacherStudentView> students(User user) { return enrollmentRepository.findStudentsByTeacherId(teacher(user).getId()); }
    public List<LearningMaterial> materials(User user) { return materialRepository.findByTeacherId(teacher(user).getId()); }
    public List<Announcement> announcements(User user) { return announcementRepository.findByTeacherId(teacher(user).getId()); }
    public TeacherDashboardResponse dashboard(User user) {
        UUID id = teacher(user).getId();
        return new TeacherDashboardResponse(
                batchRepository.countByTeacherIdAndActiveTrueAndDeletedFalse(id),
                enrollmentRepository.findStudentsByTeacherId(id).size(),
                scheduleRepository.findByTeacherId(id).size(),
                materialRepository.findByTeacherId(id).size());
    }
    public LearningMaterial createMaterial(User user, CreateMaterialRequest r) {
        verifyBatchOwnership(user, r.batchId());
        return materialRepository.save(LearningMaterial.builder().batchId(r.batchId()).title(r.title()).subject(r.subject())
                .type(r.type()).time(r.time()).size(r.size()).fileUrl(r.fileUrl()).build());
    }
    public Announcement createAnnouncement(User user, CreateAnnouncementRequest r) {
        if (r.batchId() != null) verifyBatchOwnership(user, r.batchId());
        return announcementRepository.save(Announcement.builder().batchId(r.batchId()).title(r.title())
                .description(r.description()).type(r.type()).time(r.time()).build());
    }
    public void verifyBatchOwnership(User user, UUID batchId) {
        if (batchId == null || !batchRepository.existsByIdAndTeacherIdAndDeletedFalse(batchId, teacher(user).getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Batch does not belong to this teacher");
    }
}
