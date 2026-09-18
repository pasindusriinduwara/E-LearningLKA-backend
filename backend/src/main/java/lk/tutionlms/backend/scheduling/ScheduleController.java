package lk.tutionlms.backend.scheduling;

import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.StudentRepository;
import lk.tutionlms.backend.identity.Teacher;
import lk.tutionlms.backend.identity.TeacherRepository;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.identity.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final TeacherRepository teacherRepository;
    private final BatchRepository batchRepository;

    @GetMapping
    public ResponseEntity<List<ScheduleItem>> getAllSchedules() {
        return ResponseEntity.ok(scheduleRepository.findByDeletedFalse());
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<ScheduleItem>> getUpcomingSchedules(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            User user = userRepository.findByEmail(authentication.getName()).orElse(null);
            if (user != null) {
                Student student = studentRepository.findByUserId(user.getId()).orElse(null);
                if (student != null) {
                    List<UUID> activeBatchIds = enrollmentRepository.findActiveBatchIdsByStudentId(student.getId());
                    if (activeBatchIds.isEmpty()) {
                        return ResponseEntity.ok(Collections.emptyList());
                    }
                    return ResponseEntity.ok(scheduleRepository.findByBatchIdInAndDeletedFalse(activeBatchIds));
                }
            }
        }
        return ResponseEntity.ok(scheduleRepository.findByDeletedFalse());
    }

    @GetMapping("/batch/{batchId}")
    public ResponseEntity<List<ScheduleItem>> getBatchSchedules(@PathVariable UUID batchId) {
        return ResponseEntity.ok(scheduleRepository.findByBatchIdAndDeletedFalse(batchId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScheduleItem> getScheduleById(@PathVariable UUID id) {
        return scheduleRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<ScheduleItem> createSchedule(
            @AuthenticationPrincipal User currentUser,
            @RequestBody ScheduleItem scheduleItem) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (!"ADMIN".equalsIgnoreCase(currentUser.getUserType())) {
            Teacher teacher = teacherRepository.findByUserId(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Teacher profile not found"));
            Batch batch = batchRepository.findById(scheduleItem.getBatchId())
                    .orElseThrow(() -> new IllegalArgumentException("Batch not found"));
            if (!teacher.getId().equals(batch.getTeacherId())) {
                throw new AccessDeniedException("You do not have permission to add schedules to this batch");
            }
        }
        return ResponseEntity.ok(scheduleRepository.save(scheduleItem));
    }
}
