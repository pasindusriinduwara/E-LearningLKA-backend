package lk.tutionlms.backend.content;

import lk.tutionlms.backend.academic.Subject;
import lk.tutionlms.backend.academic.SubjectRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private final MaterialRepository materialRepository;
    private final BatchRepository batchRepository;
    private final TeacherRepository teacherRepository;
    private final SubjectRepository subjectRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Transactional(readOnly = true)
    public List<MaterialResponse> getStudentRecentMaterials(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            return Collections.emptyList();
        }

        User user = userRepository.findByEmail(userEmail).orElse(null);
        if (user == null) {
            return Collections.emptyList();
        }

        Student student = studentRepository.findByUserId(user.getId()).orElse(null);
        if (student == null) {
            return Collections.emptyList();
        }

        List<UUID> activeBatchIds = enrollmentRepository.findActiveBatchIdsByStudentId(student.getId());
        if (activeBatchIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<LearningMaterial> materials = materialRepository
                .findByBatchIdInAndDeletedFalseOrderByCreatedAtDesc(activeBatchIds);

        return enrichMaterials(materials);
    }

    @Transactional(readOnly = true)
    public List<MaterialResponse> getStudentRecentMaterials(User user) {
        if (user == null) {
            return Collections.emptyList();
        }
        return getStudentRecentMaterials(user.getEmail());
    }

    @Transactional(readOnly = true)
    public List<MaterialResponse> getStudentBatchMaterials(String userEmail, UUID batchId) {
        if (userEmail == null || userEmail.isBlank() || batchId == null) {
            return Collections.emptyList();
        }

        User user = userRepository.findByEmail(userEmail).orElse(null);
        if (user == null) {
            return Collections.emptyList();
        }

        Student student = studentRepository.findByUserId(user.getId()).orElse(null);
        if (student == null) {
            return Collections.emptyList();
        }

        boolean isActive = enrollmentRepository.isStudentActiveInBatch(student.getId(), batchId);
        if (!isActive) {
            return Collections.emptyList();
        }

        List<LearningMaterial> materials = materialRepository
                .findByBatchIdAndDeletedFalseOrderByCreatedAtDesc(batchId);

        return enrichMaterials(materials);
    }

    @Transactional(readOnly = true)
    public List<MaterialResponse> getStudentBatchMaterials(User user, UUID batchId) {
        if (user == null) {
            return Collections.emptyList();
        }
        return getStudentBatchMaterials(user.getEmail(), batchId);
    }

    private List<MaterialResponse> enrichMaterials(List<LearningMaterial> materials) {
        if (materials == null || materials.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> batchIds = materials.stream()
                .map(LearningMaterial::getBatchId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Batch> batchMap = batchRepository.findAllById(batchIds).stream()
                .collect(Collectors.toMap(Batch::getId, b -> b, (b1, b2) -> b1));

        Set<UUID> teacherIds = batchMap.values().stream()
                .map(Batch::getTeacherId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Teacher> teacherMap = teacherRepository.findAllById(teacherIds).stream()
                .collect(Collectors.toMap(Teacher::getId, t -> t, (t1, t2) -> t1));

        Set<UUID> subjectIds = batchMap.values().stream()
                .map(Batch::getSubjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, Subject> subjectMap = subjectRepository.findAllById(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, s -> s, (s1, s2) -> s1));

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd MMM yyyy");

        return materials.stream().map(m -> {
            Batch batch = batchMap.get(m.getBatchId());
            String batchName = batch != null ? batch.getName() : "Class Resource";

            String teacherName = "Instructor";
            if (batch != null && batch.getTeacherId() != null) {
                Teacher teacher = teacherMap.get(batch.getTeacherId());
                if (teacher != null) {
                    String prefix = (teacher.getTitle() != null && !teacher.getTitle().isBlank())
                            ? teacher.getTitle().trim() + " "
                            : "";
                    teacherName = prefix + (teacher.getName() != null ? teacher.getName().trim() : "");
                }
            }

            String subjectName = (m.getSubject() != null && !m.getSubject().isBlank())
                    ? m.getSubject().trim()
                    : (batch != null && batch.getSubjectId() != null && subjectMap.containsKey(batch.getSubjectId()))
                            ? subjectMap.get(batch.getSubjectId()).getName()
                            : "General";

            String formattedDate = m.getCreatedAt() != null
                    ? m.getCreatedAt().format(dtf)
                    : (m.getTime() != null ? m.getTime() : "Recent");

            return new MaterialResponse(
                    m.getId(),
                    m.getBatchId(),
                    batchName,
                    teacherName,
                    m.getTitle(),
                    subjectName,
                    m.getType() != null ? m.getType() : "PDF",
                    formattedDate,
                    m.getSize() != null ? m.getSize() : "File",
                    m.getFileUrl(),
                    m.getCreatedAt()
            );
        }).collect(Collectors.toList());
    }
}
