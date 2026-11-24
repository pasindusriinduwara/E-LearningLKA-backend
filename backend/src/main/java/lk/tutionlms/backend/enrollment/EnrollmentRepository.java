package lk.tutionlms.backend.enrollment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    @Query("""
            SELECT new lk.tutionlms.backend.enrollment.EnrollmentIdentity(e.id, e.studentId, e.batchId)
            FROM Enrollment e WHERE e.id = :id AND e.deleted = false
            """)
    java.util.Optional<EnrollmentIdentity> findIdentityById(@Param("id") UUID id);

    @Query("""
            SELECT new lk.tutionlms.backend.enrollment.TeacherEnrollmentRequestResponse(
                e.id, e.studentId, e.batchId, lk.tutionlms.backend.enrollment.EnrollmentStatus.APPROVED,
                e.createdAt, s.name, s.studentId, b.name, subject.name, false, b.active)
            FROM Enrollment e
            JOIN lk.tutionlms.backend.batch.Batch b ON b.id = e.batchId
            JOIN lk.tutionlms.backend.identity.Student s ON s.id = e.studentId
            JOIN lk.tutionlms.backend.identity.User u ON u.id = s.userId
            LEFT JOIN lk.tutionlms.backend.academic.Subject subject
                ON subject.id = b.subjectId AND subject.deleted = false
            WHERE e.deleted = false AND e.status = 'ACTIVE' AND b.deleted = false
              AND s.deleted = false AND u.deleted = false AND b.teacherId = :teacherId
              AND NOT EXISTS (SELECT r.id FROM EnrollmentRequest r WHERE r.studentId = e.studentId
                  AND r.batchId = e.batchId AND r.isDeleted = false)
            ORDER BY e.createdAt DESC, e.id DESC
            """)
    List<TeacherEnrollmentRequestResponse> findLegacyRequestsByTeacherId(@Param("teacherId") UUID teacherId);

    boolean existsByStudentIdAndBatchId(
            UUID studentId,
            UUID batchId
    );

    List<Enrollment> findByStudentIdAndBatchIdAndDeletedFalse(UUID studentId, UUID batchId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Enrollment e WHERE e.studentId = :studentId AND e.batchId = :batchId AND e.deleted = false ORDER BY e.id")
    List<Enrollment> findByStudentAndBatchForUpdate(@Param("studentId") UUID studentId, @Param("batchId") UUID batchId);

    long countByBatchIdAndStatusAndDeletedFalse(UUID batchId, String status);

    @Query("SELECT e.batchId, COUNT(e) FROM Enrollment e WHERE e.batchId IN :batchIds AND e.status = 'ACTIVE' AND e.deleted = false GROUP BY e.batchId")
    List<Object[]> countActiveEnrollmentsByBatchIds(@Param("batchIds") java.util.Collection<UUID> batchIds);

    @Query("""
            SELECT DISTINCT e.batchId FROM Enrollment e
            JOIN lk.tutionlms.backend.identity.Student s ON s.id = e.studentId
            JOIN lk.tutionlms.backend.identity.User u ON u.id = s.userId
            JOIN lk.tutionlms.backend.batch.Batch b ON b.id = e.batchId
            WHERE e.studentId = :studentId AND e.status = 'ACTIVE' AND e.deleted = false
              AND s.deleted = false AND u.deleted = false AND u.status = 'ACTIVE' AND u.userType = 'STUDENT'
              AND b.deleted = false AND b.active = true
            """)
    List<UUID> findActiveBatchIdsByStudentId(@Param("studentId") UUID studentId);

    @Query("""
            SELECT CASE WHEN COUNT(e) > 0 THEN true ELSE false END FROM Enrollment e
            JOIN lk.tutionlms.backend.identity.Student s ON s.id = e.studentId
            JOIN lk.tutionlms.backend.identity.User u ON u.id = s.userId
            JOIN lk.tutionlms.backend.batch.Batch b ON b.id = e.batchId
            WHERE e.studentId = :studentId AND e.batchId = :batchId
              AND e.status = 'ACTIVE' AND e.deleted = false
              AND s.deleted = false AND u.deleted = false AND u.status = 'ACTIVE' AND u.userType = 'STUDENT'
              AND b.deleted = false AND b.active = true
            """)
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
        JOIN users u ON u.id = s.user_id
        WHERE b.teacher_id = :teacherId
          AND e.status = 'ACTIVE'
          AND e.is_deleted = false
          AND b.is_deleted = false
          AND b.is_active = true
          AND s.is_deleted = false
          AND u.is_deleted = false
          AND u.status = 'ACTIVE'
          AND u.user_type = 'STUDENT'
        ORDER BY s.name
        """, nativeQuery = true)
    List<TeacherStudentView> findStudentsByTeacherId(
            @Param("teacherId") UUID teacherId
    );
}
