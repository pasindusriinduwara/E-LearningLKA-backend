package lk.tutionlms.backend.enrollment;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EnrollmentRequestRepository
        extends JpaRepository<EnrollmentRequest, UUID> {

    /** Serialize decisions so competing approvals/rejections observe the committed status. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM EnrollmentRequest r WHERE r.id = :id AND r.isDeleted = false")
    java.util.Optional<EnrollmentRequest> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            SELECT new lk.tutionlms.backend.enrollment.EnrollmentRequestIdentity(r.id, r.studentId, r.batchId)
            FROM EnrollmentRequest r WHERE r.id = :id AND r.isDeleted = false
            """)
    java.util.Optional<EnrollmentRequestIdentity> findIdentityById(@Param("id") UUID id);

    boolean existsByStudentIdAndBatchId(
            UUID studentId,
            UUID batchId
    );

    java.util.Optional<EnrollmentRequest> findTopByStudentIdAndBatchIdAndIsDeletedFalseOrderByCreatedAtDesc(
            UUID studentId,
            UUID batchId
    );

    List<EnrollmentRequest> findByStudentIdAndIsDeletedFalse(
            UUID studentId
    );

    List<EnrollmentRequest> findByStatus(
            EnrollmentStatus status
    );

    @Query("""
            SELECT new lk.tutionlms.backend.enrollment.TeacherEnrollmentRequestResponse(
                r.id, r.studentId, r.batchId, r.status, r.createdAt,
                s.name, s.studentId, b.name, subject.name, false, b.active)
            FROM EnrollmentRequest r
            JOIN lk.tutionlms.backend.batch.Batch b ON b.id = r.batchId
            JOIN lk.tutionlms.backend.identity.Student s ON s.id = r.studentId
            JOIN lk.tutionlms.backend.identity.User u ON u.id = s.userId
            LEFT JOIN lk.tutionlms.backend.academic.Subject subject
                ON subject.id = b.subjectId AND subject.deleted = false
            WHERE r.status = lk.tutionlms.backend.enrollment.EnrollmentStatus.PENDING
                AND r.isDeleted = false AND b.deleted = false
                AND s.deleted = false AND u.deleted = false
                AND b.teacherId = :teacherId
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<TeacherEnrollmentRequestResponse> findPendingRequestsByTeacherId(@Param("teacherId") UUID teacherId);

    @Query("""
            SELECT new lk.tutionlms.backend.enrollment.TeacherEnrollmentRequestResponse(
                r.id, r.studentId, r.batchId, r.status, r.createdAt,
                s.name, s.studentId, b.name, subject.name, false, b.active)
            FROM EnrollmentRequest r
            JOIN lk.tutionlms.backend.batch.Batch b ON b.id = r.batchId
            JOIN lk.tutionlms.backend.identity.Student s ON s.id = r.studentId
            JOIN lk.tutionlms.backend.identity.User u ON u.id = s.userId
            LEFT JOIN lk.tutionlms.backend.academic.Subject subject
                ON subject.id = b.subjectId AND subject.deleted = false
            WHERE r.isDeleted = false AND b.deleted = false
                AND s.deleted = false AND u.deleted = false
                AND b.teacherId = :teacherId
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<TeacherEnrollmentRequestResponse> findRequestsByTeacherId(@Param("teacherId") UUID teacherId);

    List<EnrollmentRequest> findByBatchIdOrderByCreatedAtDesc(
            UUID batchId
    );
}
