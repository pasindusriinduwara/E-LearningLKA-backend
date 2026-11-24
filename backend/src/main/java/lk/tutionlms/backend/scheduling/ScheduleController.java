package lk.tutionlms.backend.scheduling;

import jakarta.validation.Valid;
import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.StudentRepository;
import lk.tutionlms.backend.identity.Teacher;
import lk.tutionlms.backend.identity.TeacherRepository;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.identity.UserRepository;
import lk.tutionlms.backend.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    private final NotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ResponseEntity<List<ScheduleItem>> getAllSchedules(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(visibleSchedules(user));
    }

    @GetMapping("/upcoming")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ResponseEntity<List<ScheduleItem>> getUpcomingSchedules(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) throw new AccessDeniedException("Authentication required");
        User user = authentication.getPrincipal() instanceof User principal ? principal
                : userRepository.findByEmail(authentication.getName()).orElseThrow(() -> new AccessDeniedException("Account not found"));
        return ResponseEntity.ok(visibleSchedules(user));
    }

    @GetMapping("/batch/{batchId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ResponseEntity<List<ScheduleItem>> getBatchSchedules(@PathVariable UUID batchId, @AuthenticationPrincipal User user) {
        requireBatchAccess(user, batchId);
        return ResponseEntity.ok(scheduleRepository.findByBatchIdAndDeletedFalse(batchId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ResponseEntity<ScheduleItem> getScheduleById(@PathVariable UUID id, @AuthenticationPrincipal User user) {
        ScheduleItem schedule = scheduleRepository.findById(id).filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));
        requireBatchAccess(user, schedule.getBatchId());
        return ResponseEntity.ok(schedule);
    }

    private List<ScheduleItem> visibleSchedules(User user) {
        requireAccount(user);
        if ("TEACHER".equals(user.getUserType())) {
            Teacher teacher = teacherRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new AccessDeniedException("Teacher profile not found"));
            return scheduleRepository.findByTeacherId(teacher.getId());
        }
        Student student = studentRepository.findByUserId(user.getId()).filter(profile -> !profile.isDeleted())
                .orElseThrow(() -> new AccessDeniedException("Student profile not found"));
        List<UUID> batches = enrollmentRepository.findActiveBatchIdsByStudentId(student.getId());
        return batches.isEmpty() ? Collections.emptyList() : scheduleRepository.findByBatchIdInAndDeletedFalse(batches);
    }

    private void requireBatchAccess(User user, UUID batchId) {
        requireAccount(user);
        Batch batch = batchRepository.findById(batchId).filter(item -> !item.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
        if ("TEACHER".equals(user.getUserType())) {
            Teacher teacher = teacherRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new AccessDeniedException("Teacher profile not found"));
            if (!teacher.getId().equals(batch.getTeacherId())) throw new AccessDeniedException("This class does not belong to you");
        } else {
            Student student = studentRepository.findByUserId(user.getId()).filter(profile -> !profile.isDeleted())
                    .orElseThrow(() -> new AccessDeniedException("Student profile not found"));
            if (!batch.isActive() || !enrollmentRepository.isStudentActiveInBatch(student.getId(), batchId)) {
                throw new AccessDeniedException("Your teacher must allow access to this class");
            }
        }
    }

    private void requireAccount(User user) {
        if (user == null || !user.isEnabled() || !("STUDENT".equals(user.getUserType()) || "TEACHER".equals(user.getUserType()))) {
            throw new AccessDeniedException("An active student or teacher account is required");
        }
    }

    @PostMapping
    @Transactional
    @PreAuthorize("hasRole('TEACHER') or hasRole('ADMIN')")
    public ResponseEntity<ScheduleItem> createSchedule(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateScheduleItemRequest request) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Batch batch = batchRepository.findById(request.batchId())
                .filter(b -> !b.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found"));
        if (!"ADMIN".equalsIgnoreCase(currentUser.getUserType())) {
            Teacher teacher = teacherRepository.findByUserId(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Teacher profile not found"));
            if (!teacher.getId().equals(batch.getTeacherId())) {
                throw new AccessDeniedException("You do not have permission to add schedules to this batch");
            }
        }

        // Always create a new entity. Request-supplied IDs or deletion/audit fields
        // must never turn this create endpoint into an update of another schedule.
        ScheduleItem scheduleItem = ScheduleItem.builder()
                .batchId(batch.getId())
                .dayOfWeek(request.dayOfWeek())
                .effectiveDate(request.effectiveDate())
                .title(request.title().trim())
                .subject(request.subject())
                .teacher(request.teacher())
                .startTime(request.startTime())
                .endTime(request.endTime())
                .time(request.time())
                .location(request.location())
                .mode(request.mode())
                .recurrence(request.recurrence() != null ? request.recurrence() : RecurrenceType.WEEKLY)
                .accent(request.accent())
                .build();
        ScheduleItem saved = scheduleRepository.save(scheduleItem);
        notificationService.notifyBatchStudents(batch.getId(), "SCHEDULE_CREATED", "New class schedule",
                scheduleItem.getTitle() + " was added to your class schedule.", "/schedule");
        return ResponseEntity.ok(saved);
    }
}
