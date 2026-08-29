package lk.tutionlms.backend.batch;

import java.util.List;

import lk.tutionlms.backend.academic.Batch;
import lk.tutionlms.backend.academic.BatchRepository;
import lk.tutionlms.backend.academic.Subject;
import lk.tutionlms.backend.academic.SubjectRepository;
import lk.tutionlms.backend.identity.Teacher;
import lk.tutionlms.backend.identity.TeacherRepository;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.identity.UserRepository;
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
        return batchRepository.findByActiveTrueAndDeletedFalse()
                .stream()
                .map(batch -> {
                    String subjectName = subjectRepository.findById(batch.getSubjectId())
                            .map(Subject::getName)
                            .orElse("Unknown subject");

                    String teacherName = teacherRepository.findById(batch.getTeacherId())
                            .map(Teacher::getName)
                            .orElse("Unknown teacher");

                    return new AvailableBatchResponse(
                            batch.getId(),
                            batch.getName(),
                            subjectName,
                            teacherName,
                            "Schedule not set",
                            "AVAILABLE",
                            batch.getExamYear(),
                            batch.getMonthlyFee(),
                            batch.getDeliveryMode()
                    );
                })
                .toList();
    }
}