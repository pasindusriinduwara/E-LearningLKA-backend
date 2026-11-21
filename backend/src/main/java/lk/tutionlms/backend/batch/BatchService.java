package lk.tutionlms.backend.batch;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import lk.tutionlms.backend.academic.Subject;
import lk.tutionlms.backend.academic.SubjectRepository;
import lk.tutionlms.backend.content.MaterialRepository;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.enrollment.EnrollmentRequest;
import lk.tutionlms.backend.enrollment.EnrollmentRequestRepository;
import lk.tutionlms.backend.identity.StudentRepository;
import lk.tutionlms.backend.identity.Teacher;
import lk.tutionlms.backend.identity.TeacherRepository;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.identity.UserRepository;
import lk.tutionlms.backend.scheduling.ScheduleItem;
import lk.tutionlms.backend.scheduling.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BatchService {

    private final BatchRepository batchRepository;
    private final UserRepository userRepository;
    private final TeacherRepository teacherRepository;
    private final SubjectRepository subjectRepository;
    private final ScheduleRepository scheduleRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final EnrollmentRequestRepository enrollmentRequestRepository;
    private final StudentRepository studentRepository;
    private final MaterialRepository materialRepository;

    @Transactional
    public Batch createBatch(String teacherEmail, CreateBatchDto dto) {
        User user = userRepository.findByEmail(teacherEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Teacher teacher = teacherRepository.findByUserId(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Teacher profile not found"));

        Subject subject = subjectRepository.findById(dto.getSubjectId())
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        Batch batch = new Batch();
        batch.setName(dto.getName().trim());
        batch.setExamYear(dto.getExamYear().trim());
        batch.setMonthlyFee(dto.getMonthlyFee());
        batch.setDeliveryMode(dto.getDeliveryMode());
        batch.setSubjectId(subject.getId());
        batch.setTeacherId(teacher.getId());
        batch.setActive(true);

        return batchRepository.save(batch);
    }

    @Transactional(readOnly = true)
    public List<AvailableBatchResponse> getAvailableBatches() {
        return getAvailableBatches(null);
    }

    @Transactional(readOnly = true)
    public List<AvailableBatchResponse> getAvailableBatches(String userEmail) {
        List<Batch> batches = batchRepository.findByActiveTrueAndDeletedFalse();
        if (batches.isEmpty()) {
            return List.of();
        }

        List<UUID> batchIds = batches.stream().map(Batch::getId).toList();
        Set<UUID> subjectIds = batches.stream().map(Batch::getSubjectId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<UUID> teacherIds = batches.stream().map(Batch::getTeacherId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<UUID, Subject> subjectMap = subjectRepository.findAllById(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Function.identity(), (a, b) -> a));

        Map<UUID, Teacher> teacherMap = teacherRepository.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, Function.identity(), (a, b) -> a));

        Map<UUID, List<ScheduleItem>> scheduleMap = scheduleRepository.findByBatchIdInAndDeletedFalse(batchIds).stream()
                .collect(Collectors.groupingBy(ScheduleItem::getBatchId));

        Map<UUID, Long> enrolledCounts = new HashMap<>();
        for (Object[] row : enrollmentRepository.countActiveEnrollmentsByBatchIds(batchIds)) {
            if (row != null && row.length >= 2 && row[0] instanceof UUID bid && row[1] instanceof Number count) {
                enrolledCounts.put(bid, count.longValue());
            }
        }

        Map<UUID, Long> materialCounts = new HashMap<>();
        for (Object[] row : materialRepository.countMaterialsByBatchIds(batchIds)) {
            if (row != null && row.length >= 2 && row[0] instanceof UUID bid && row[1] instanceof Number count) {
                materialCounts.put(bid, count.longValue());
            }
        }

        Set<UUID> approvedBatchIds = new HashSet<>();
        Map<UUID, EnrollmentRequest> requestMap = new HashMap<>();

        if (userEmail != null && !userEmail.isBlank()) {
            userRepository.findByEmail(userEmail)
                    .flatMap(u -> studentRepository.findByUserId(u.getId()))
                    .ifPresent(student -> {
                        approvedBatchIds.addAll(enrollmentRepository.findActiveBatchIdsByStudentId(student.getId()));
                        enrollmentRequestRepository.findByStudentIdAndIsDeletedFalse(student.getId()).stream()
                                .sorted(Comparator.comparing(EnrollmentRequest::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                                .forEach(req -> requestMap.putIfAbsent(req.getBatchId(), req));
                    });
        }

        return batches.stream().map(batch -> {
            Subject subject = subjectMap.get(batch.getSubjectId());
            Teacher teacher = teacherMap.get(batch.getTeacherId());
            List<ScheduleItem> schedules = scheduleMap.getOrDefault(batch.getId(), List.of());
            long enrolledCount = enrolledCounts.getOrDefault(batch.getId(), 0L);
            long materialsCount = materialCounts.getOrDefault(batch.getId(), 0L);

            String status = "AVAILABLE";
            UUID requestId = null;

            if (approvedBatchIds.contains(batch.getId())) {
                status = "APPROVED";
            } else if (requestMap.containsKey(batch.getId())) {
                EnrollmentRequest req = requestMap.get(batch.getId());
                status = req.getStatus() != null ? req.getStatus().name() : "PENDING";
                requestId = req.getId();
            }

            return buildAvailableBatchResponse(batch, subject, teacher, schedules, enrolledCount, materialsCount, status, requestId);
        }).toList();
    }

    @Transactional(readOnly = true)
    public AvailableBatchResponse getBatchById(UUID id) {
        return getBatchById(id, null);
    }

    @Transactional(readOnly = true)
    public AvailableBatchResponse getBatchById(UUID id, String userEmail) {
        Batch batch = batchRepository.findById(id)
                .filter(b -> b.isActive() && !b.isDeleted())
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));

        Subject subject = batch.getSubjectId() != null
                ? subjectRepository.findById(batch.getSubjectId()).orElse(null)
                : null;
        Teacher teacher = batch.getTeacherId() != null
                ? teacherRepository.findById(batch.getTeacherId()).orElse(null)
                : null;
        List<ScheduleItem> schedules = scheduleRepository.findByBatchIdAndDeletedFalse(batch.getId());
        long enrolledCount = enrollmentRepository.countByBatchIdAndStatusAndDeletedFalse(batch.getId(), "ACTIVE");
        long materialsCount = materialRepository.countByBatchIdAndDeletedFalse(batch.getId());

        String status = "AVAILABLE";
        UUID requestId = null;

        if (userEmail != null && !userEmail.isBlank()) {
            var userOpt = userRepository.findByEmail(userEmail);
            if (userOpt.isPresent()) {
                var studentOpt = studentRepository.findByUserId(userOpt.get().getId());
                if (studentOpt.isPresent()) {
                    UUID studentId = studentOpt.get().getId();
                    if (enrollmentRepository.isStudentActiveInBatch(studentId, batch.getId())) {
                        status = "APPROVED";
                    } else {
                        var reqOpt = enrollmentRequestRepository
                                .findTopByStudentIdAndBatchIdAndIsDeletedFalseOrderByCreatedAtDesc(studentId, batch.getId());
                        if (reqOpt.isPresent()) {
                            status = reqOpt.get().getStatus().name();
                            requestId = reqOpt.get().getId();
                        }
                    }
                }
            }
        }

        return buildAvailableBatchResponse(batch, subject, teacher, schedules, enrolledCount, materialsCount, status, requestId);
    }

    private AvailableBatchResponse buildAvailableBatchResponse(
            Batch batch,
            Subject subject,
            Teacher teacher,
            List<ScheduleItem> schedules,
            long enrolledCount,
            long materialsCount,
            String status,
            UUID requestId
    ) {
        String subjectName = subject != null ? subject.getName() : "Unknown subject";

        String teacherName = "Unknown teacher";
        String teacherTitle = "Mr.";
        String teacherQualification = null;
        String teacherBio = null;
        UUID teacherId = null;

        if (teacher != null) {
            teacherId = teacher.getId();
            teacherName = (teacher.getName() != null && !teacher.getName().isBlank())
                    ? teacher.getName().trim()
                    : "Unknown teacher";
            teacherTitle = (teacher.getTitle() != null && !teacher.getTitle().isBlank())
                    ? teacher.getTitle().trim()
                    : "Mr.";
            teacherQualification = teacher.getQualification();
            teacherBio = teacher.getBio();
        }

        String displayTeacher = teacherName;
        if (!teacherTitle.isBlank() && !teacherName.toLowerCase().startsWith(teacherTitle.toLowerCase())) {
            displayTeacher = teacherTitle + " " + teacherName;
        }

        List<String> scheduleList = schedules.stream()
                .map(s -> {
                    String day = s.getDayOfWeek() != null ? s.getDayOfWeek().toString() : "";
                    if (!day.isEmpty()) {
                        day = day.substring(0, 1).toUpperCase() + day.substring(1).toLowerCase();
                    }
                    String time = (s.getTime() != null && !s.getTime().isBlank())
                            ? s.getTime()
                            : (s.getStartTime() != null && s.getEndTime() != null
                            ? s.getStartTime() + " - " + s.getEndTime()
                            : "");
                    return day + (time.isEmpty() ? "" : ": " + time);
                })
                .filter(str -> !str.isBlank())
                .toList();

        String scheduleSummary = scheduleList.isEmpty()
                ? "Schedule not set"
                : String.join(", ", scheduleList);

        List<ScheduleSummaryDto> scheduleSummaryDtos = schedules.stream()
                .map(s -> new ScheduleSummaryDto(
                        s.getId(),
                        s.getDayOfWeek(),
                        s.getTime(),
                        s.getStartTime(),
                        s.getEndTime(),
                        s.getLocation(),
                        s.getMode() != null ? s.getMode().name() : null
                ))
                .toList();

        return new AvailableBatchResponse(
                batch.getId(),
                batch.getName(),
                subjectName,
                displayTeacher,
                teacherTitle,
                teacherQualification,
                teacherBio,
                teacherId,
                scheduleSummary,
                scheduleList,
                status,
                batch.getExamYear(),
                batch.getMonthlyFee(),
                batch.getDeliveryMode(),
                enrolledCount,
                materialsCount,
                scheduleSummaryDtos,
                requestId
        );
    }
}