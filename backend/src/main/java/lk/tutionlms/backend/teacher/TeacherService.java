package lk.tutionlms.backend.teacher;

import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;

import lk.tutionlms.backend.batch.*;
import lk.tutionlms.backend.communication.*;
import lk.tutionlms.backend.content.*;
import lk.tutionlms.backend.enrollment.*;
import lk.tutionlms.backend.identity.*;
import lk.tutionlms.backend.scheduling.*;
import lk.tutionlms.backend.teacher.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TeacherService {
    private final TeacherRepository teacherRepository;
    private final BatchRepository batchRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final ScheduleRepository scheduleRepository;
    private final MaterialRepository materialRepository;
    private final AnnouncementRepository announcementRepository;

    public Teacher teacher(User user) {
        return teacherRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher profile not found"));
    }

    public List<Batch> batches(User user) {
        return batchRepository.findByTeacherIdAndDeletedFalse(teacher(user).getId());
    }

    public List<ScheduleItem> schedules(User user) {
        return scheduleRepository.findByTeacherId(teacher(user).getId());
    }

    public List<TeacherStudentView> students(User user) {
        return enrollmentRepository.findStudentsByTeacherId(teacher(user).getId());
    }

    public List<LearningMaterial> materials(User user) {
        return materialRepository.findByTeacherId(teacher(user).getId());
    }

    public List<Announcement> announcements(User user) {
        return announcementRepository.findByTeacherId(teacher(user).getId());
    }

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
        return materialRepository
                .save(LearningMaterial.builder().batchId(r.batchId()).title(r.title()).subject(r.subject())
                        .type(r.type()).time(r.time()).size(r.size()).fileUrl(r.fileUrl()).build());
    }

    public Announcement createAnnouncement(User user, CreateAnnouncementRequest r) {
        if (r.batchId() != null)
            verifyBatchOwnership(user, r.batchId());
        return announcementRepository.save(Announcement.builder().batchId(r.batchId()).title(r.title())
                .description(r.description()).type(r.type()).time(r.time()).build());
    }

    public void verifyBatchOwnership(User user, UUID batchId) {
        if (batchId == null || !batchRepository.existsByIdAndTeacherIdAndDeletedFalse(batchId, teacher(user).getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Batch does not belong to this teacher");
    }

    @Transactional
    public ScheduleItem createSchedule(User user, CreateScheduleRequest r) {
        // 1. Input Validation
        if (r.batchId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Batch ID is required");
        }
        if (r.title() == null || r.title().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Class title is required");
        }
        if (r.date() == null || r.date().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Schedule date is required");
        }
        // 2. Verify Batch Ownership
        verifyBatchOwnership(user, r.batchId());
        // 3. Fetch Batch & Teacher Profile
        Batch batch = batchRepository.findById(r.batchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found"));
        Teacher teacher = teacher(user);
        String teacherDisplayName = (teacher.getName() != null && !teacher.getName().isBlank())
                ? teacher.getName()
                : user.getEmail();
        // 4. Robust Date & Day of Week Parsing
        String dayOfWeek = resolveDayOfWeek(r.date());
        // 5. Format Time Range cleanly
        String timeRange = formatTimeRange(r.startTime(), r.endTime());
        LocalTime parsedStartTime = parseTime(r.startTime());
        LocalTime parsedEndTime = parseTime(r.endTime());

        // 6. Delivery Mode Fallback
        String mode = (r.mode() != null && !r.mode().isBlank())
                ? r.mode().toUpperCase(Locale.ROOT)
                : (batch.getDeliveryMode() != null ? batch.getDeliveryMode() : "IN_PERSON");
        // 7. Build and Persist Schedule Item
        ScheduleItem item = ScheduleItem.builder()
                .batchId(batch.getId())
                .title(r.title().trim())
                .subject(batch.getName())
                .teacher(teacherDisplayName)
                .day(dayOfWeek)
                .date(r.date().trim())
                .startTime(parsedStartTime)
                .endTime(parsedEndTime)
                .time(timeRange)
                .location(r.location() != null ? r.location().trim() : "TBD")
                .mode(mode)
                .accent("#2D9F75")
                .build();
        return scheduleRepository.save(item);
    }

    /**
     * Safely resolves the Day of Week from diverse date formats (e.g. "2026-09-04",
     * "09/04/2026")
     */
    private String resolveDayOfWeek(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return LocalDate.now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        }
        try {
            // Standard HTML5 input date format: yyyy-MM-dd
            LocalDate parsedDate = LocalDate.parse(dateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            return parsedDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        } catch (Exception ignored) {
            try {
                // Fallback for MM/dd/yyyy
                LocalDate parsedDate = LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                return parsedDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            } catch (Exception ex) {
                return LocalDate.now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            }
        }
    }

    /**
     * Safely parses time strings into LocalTime (handles HH:mm, HH:mm:ss, hh:mm a)
     */
    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(timeStr.trim());
        } catch (Exception ignored) {
            try {
                return LocalTime.parse(timeStr.trim(), DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH));
            } catch (Exception ex) {
                try {
                    return LocalTime.parse(timeStr.trim(), DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH));
                } catch (Exception e) {
                    return null;
                }
            }
        }
    }

    /**
     * Formats start & end time into standard display string (e.g. "04:30 PM - 06:30
     * PM")
     */
    private String formatTimeRange(String start, String end) {
        if (start != null && end != null && !start.isBlank() && !end.isBlank()) {
            return start.trim() + " - " + end.trim();
        } else if (start != null && !start.isBlank()) {
            return start.trim();
        }
        return "Time not set";
    }

}
