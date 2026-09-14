package lk.tutionlms.backend.enrollment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    boolean existsByStudentIdAndBatchId(
            UUID studentId,
            UUID batchId
    );

    List<Enrollment> findByStudentIdAndBatchIdAndDeletedFalse(UUID studentId, UUID batchId);

    long countByBatchIdAndStatusAndDeletedFalse(UUID batchId, String status);

    @Query("SELECT e.batchId FROM Enrollment e WHERE e.studentId = :studentId AND e.status = 'ACTIVE' AND e.deleted = false")
    List<UUID> findActiveBatchIdsByStudentId(@Param("studentId") UUID studentId);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM Enrollment e WHERE e.studentId = :studentId AND e.batchId = :batchId AND e.status = 'ACTIVE' AND e.deleted = false")
    boolean isStudentActiveInBatch(@Param("studentId") UUID studentId, @Param("batchId") UUID batchId);

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
