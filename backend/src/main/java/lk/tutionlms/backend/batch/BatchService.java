package lk.tutionlms.backend.batch;

import java.util.List;
import java.util.UUID;

import lk.tutionlms.backend.academic.Subject;
import lk.tutionlms.backend.academic.SubjectRepository;
import lk.tutionlms.backend.content.MaterialRepository;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
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
        return batchRepository.findByActiveTrueAndDeletedFalse()
                .stream()
                .map(batch -> mapToAvailableBatchResponse(batch, userEmail))
                .toList();
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
        return mapToAvailableBatchResponse(batch, userEmail);
    }

    private AvailableBatchResponse mapToAvailableBatchResponse(Batch batch, String userEmail) {
        String subjectName = subjectRepository.findById(batch.getSubjectId())
                .map(Subject::getName)
                .orElse("Unknown subject");

        Teacher teacher = teacherRepository.findById(batch.getTeacherId())
                .orElse(null);

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

        List<ScheduleItem> schedules = scheduleRepository.findByBatchIdAndDeletedFalse(batch.getId());
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