package lk.tutionlms.backend.academic;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {
    @Query(value = """
        SELECT s.id AS id, s.name AS name, s.student_id AS studentId, b.name AS batchName
        FROM students s JOIN enrollments e ON e.student_id = s.id
        JOIN batches b ON b.id = e.batch_id
        WHERE b.teacher_id = :teacherId AND e.status = 'ACTIVE' AND s.is_deleted = false
        ORDER BY s.name
        """, nativeQuery = true)
    List<TeacherStudentView> findStudentsByTeacherId(@Param("teacherId") UUID teacherId);
}
