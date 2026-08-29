package lk.tutionlms.backend.academic;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EnrollmentRepository
        extends JpaRepository<Enrollment, UUID> {

    /*
     * Check whether the student is already enrolled in the batch.
     * Spring Data JPA creates this SQL query automatically
     * using the field names in Enrollment entity.
     */
    boolean existsByStudentIdAndBatchId(
            UUID studentId,
            UUID batchId
    );

    /*
     * Get active enrolled students for batches
     * owned by a particular teacher.
     */
    @Query(value = """
        SELECT
            s.id AS id,
            s.name AS name,
            s.student_id AS studentId,
            b.name AS batchName
        FROM students s
        JOIN enrollments e
            ON e.student_id = s.id
        JOIN batches b
            ON b.id = e.batch_id
        WHERE b.teacher_id = :teacherId
          AND e.status = 'ACTIVE'
          AND s.is_deleted = false
        ORDER BY s.name
        """, nativeQuery = true)
    List<TeacherStudentView> findStudentsByTeacherId(
            @Param("teacherId") UUID teacherId
    );
}