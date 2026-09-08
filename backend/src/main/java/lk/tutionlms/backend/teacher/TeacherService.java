package lk.tutionlms.backend.teacher;

import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;

import lk.tutionlms.backend.batch.*;
import lk.tutionlms.backend.common.ScheduleConflictException;
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

    @Transactional(readOnly = true)
    public List<LearningMaterial> batchMaterials(User user, UUID batchId) {
        verifyBatchOwnership(user, batchId);
        return materialRepository.findByBatchIdAndDeletedFalseOrderByCreatedAtDesc(batchId);
    }

    @Transactional
    public LearningMaterial createMaterial(User user, CreateMaterialRequest r) {
        verifyBatchOwnership(user, r.batchId());
        return materialRepository
                .save(LearningMaterial.builder().batchId(r.batchId()).title(r.title()).subject(r.subject())
                        .type(r.type()).time(r.time()).size(r.size()).fileUrl(r.fileUrl()).build());
    }

    @Transactional
    public void deleteMaterial(User user, UUID materialId) {
        LearningMaterial material = materialRepository.findById(materialId)
                .filter(m -> !m.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Material not found"));
        verifyBatchOwnership(user, material.getBatchId());
        material.setDeleted(true);
        materialRepository.save(material);
    }

    @Transactional(readOnly = true)
    public List<Announcement> batchAnnouncements(User user, UUID batchId) {
        verifyBatchOwnership(user, batchId);
        return announcementRepository.findByBatchIdAndDeletedFalseOrderByCreatedAtDesc(batchId);
    }

    @Transactional
    public Announcement createAnnouncement(User user, CreateAnnouncementRequest r) {
        if (r.batchId() != null)
            verifyBatchOwnership(user, r.batchId());
        return announcementRepository.save(Announcement.builder().batchId(r.batchId()).title(r.title())
                .description(r.description()).type(r.type()).time(r.time()).build());
    }

    @Transactional
    public void deleteAnnouncement(User user, UUID announcementId) {
        Announcement announcement = announcementRepository.findById(announcementId)
                .filter(a -> !a.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Announcement not found"));
        if (announcement.getBatchId() != null) {
            verifyBatchOwnership(user, announcement.getBatchId());
        }
        announcement.setDeleted(true);
        announcementRepository.save(announcement);
    }

    public void verifyBatchOwnership(User user, UUID batchId) {
        if (batchId == null || !batchRepository.existsByIdAndTeacherIdAndDeletedFalse(batchId, teacher(user).getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Batch does not belong to this teacher");
    }

    /**
     * Safely parses standard date string to LocalDate
     */
    private LocalDate parseLocalDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignored) {
            try {
                return LocalDate.parse(dateStr.trim(), DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            } catch (Exception ex) {
                return null;
            }
        }
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

    @Transactional
    public ScheduleItem createSchedule(User user, CreateScheduleRequest r) {
        // 1. Verify batch ownership and resolve teacher
        verifyBatchOwnership(user, r.batchId());
        Batch batch = batchRepository.findById(r.batchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found"));
        Teacher teacher = teacher(user);

        // 2. Parse and Validate Times (startTime must be strictly before endTime)
        LocalTime parsedStartTime = parseTime(r.startTime());
        LocalTime parsedEndTime = parseTime(r.endTime());

        if (parsedStartTime == null || parsedEndTime == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid start or end time format");
        }

        if (!parsedStartTime.isBefore(parsedEndTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time must be strictly before end time");
        }

        // 3. Resolve the Day of Week from the selected date
        LocalDate baseDate = parseLocalDate(r.date());
        if (baseDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Expected yyyy-MM-dd");
        }
        DayOfWeek dayOfWeek = baseDate.getDayOfWeek();

        // 4. Validate Overlap Conflicts on that Day of Week
        List<ScheduleItem> conflicts = scheduleRepository.findConflictingSchedules(
                teacher.getId(),
                batch.getId(),
                dayOfWeek,
                parsedStartTime,
                parsedEndTime);

        if (!conflicts.isEmpty()) {
            ScheduleItem conflict = conflicts.get(0);
            throw new ScheduleConflictException(
                    String.format("Schedule conflict on %s (%s - %s). Overlaps with '%s'.",
                            dayOfWeek, r.startTime(), r.endTime(), conflict.getTitle()));
        }

        // 5. Parse Delivery Mode & Recurrence with safe fallbacks
        DeliveryMode mode = DeliveryMode.IN_PERSON;
        if (r.mode() != null) {
            try {
                mode = DeliveryMode.valueOf(r.mode().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        RecurrenceType recurrence = RecurrenceType.WEEKLY;
        if (r.repeat() != null) {
            try {
                recurrence = RecurrenceType.valueOf(r.repeat().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        // 6. Build and persist EXACTLY ONE master record for this weekly slot
        ScheduleItem item = ScheduleItem.builder()
                .batchId(batch.getId())
                .title(r.title().trim())
                .subject(batch.getName())
                .teacher(teacher.getName() != null ? teacher.getName() : user.getEmail())
                .dayOfWeek(dayOfWeek)
                .effectiveDate(baseDate)
                .startTime(parsedStartTime)
                .endTime(parsedEndTime)
                .time(formatTimeRange(r.startTime(), r.endTime()))
                .location(r.location() != null ? r.location().trim() : "TBD")
                .mode(mode)
                .recurrence(recurrence)
                .accent("#2D9F75")
                .build();

        return scheduleRepository.save(item);
    }

    @Transactional
    public ScheduleItem updateSchedule(User user, UUID scheduleId, UpdateScheduleRequest r) {
        Teacher currentTeacher = teacher(user);

        // 1. Fetch Schedule & Verify Existence
        ScheduleItem schedule = scheduleRepository.findById(scheduleId)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        // 2. IDOR Prevention: Verify current schedule belongs to current teacher
        verifyBatchOwnership(user, schedule.getBatchId());

        // 3. Verify target batch belongs to current teacher (in case they change the
        // batch)
        verifyBatchOwnership(user, r.batchId());
        Batch targetBatch = batchRepository.findById(r.batchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Batch not found"));

        // 4. Validate time integrity
        LocalTime parsedStartTime = parseTime(r.startTime());
        LocalTime parsedEndTime = parseTime(r.endTime());

        if (parsedStartTime == null || parsedEndTime == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time format. Expected HH:mm");
        }
        if (!parsedStartTime.isBefore(parsedEndTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time must be strictly before end time");
        }

        LocalDate baseDate = parseLocalDate(r.date());
        if (baseDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date format. Expected yyyy-MM-dd");
        }
        DayOfWeek dayOfWeek = baseDate.getDayOfWeek();

        // 5. Overlap Conflict Check (ignoring current schedule)
        List<ScheduleItem> conflicts = scheduleRepository.findConflictingSchedulesExcluding(
                scheduleId,
                currentTeacher.getId(),
                targetBatch.getId(),
                dayOfWeek,
                parsedStartTime,
                parsedEndTime);

        if (!conflicts.isEmpty()) {
            ScheduleItem conflict = conflicts.get(0);
            throw new ScheduleConflictException(
                    String.format("Schedule conflict on %s (%s - %s). Overlaps with '%s'.",
                            dayOfWeek, r.startTime(), r.endTime(), conflict.getTitle()));
        }

        // 6. Parse Enums safely
        DeliveryMode mode = DeliveryMode.IN_PERSON;
        if (r.mode() != null) {
            try {
                mode = DeliveryMode.valueOf(r.mode().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        RecurrenceType recurrence = RecurrenceType.WEEKLY;
        if (r.repeat() != null) {
            try {
                recurrence = RecurrenceType.valueOf(r.repeat().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        // 7. Mutate Entity Fields
        schedule.setBatchId(targetBatch.getId());
        schedule.setTitle(r.title().trim());
        schedule.setSubject(targetBatch.getName());
        schedule.setTeacher(currentTeacher.getName() != null ? currentTeacher.getName() : user.getEmail());
        schedule.setDayOfWeek(dayOfWeek);
        schedule.setEffectiveDate(baseDate);
        schedule.setStartTime(parsedStartTime);
        schedule.setEndTime(parsedEndTime);
        schedule.setTime(formatTimeRange(r.startTime(), r.endTime()));
        schedule.setLocation(r.location() != null ? r.location().trim() : "TBD");
        schedule.setMode(mode);
        schedule.setRecurrence(recurrence);

        return scheduleRepository.save(schedule);
    }

    @Transactional
    public void deleteSchedule(User user, UUID scheduleId) {
        ScheduleItem schedule = scheduleRepository.findById(scheduleId)
                .filter(s -> !s.isDeleted())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not found"));

        // IDOR check: verify ownership
        verifyBatchOwnership(user, schedule.getBatchId());

        // Soft delete: keep historical records intact
        schedule.setDeleted(true);
        scheduleRepository.save(schedule);
    }

}
