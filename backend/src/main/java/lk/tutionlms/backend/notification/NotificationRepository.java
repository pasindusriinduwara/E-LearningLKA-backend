package lk.tutionlms.backend.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByRecipientId(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :readAt WHERE n.id = :id AND n.recipientId = :recipientId AND n.readAt IS NULL")
    int markRead(@Param("id") UUID id, @Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notification n SET n.readAt = :readAt WHERE n.recipientId = :recipientId AND n.readAt IS NULL")
    int markAllRead(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);

    @Query(value = """
            SELECT DISTINCT u.id
            FROM users u
            JOIN students s ON s.user_id = u.id
            JOIN enrollments e ON e.student_id = s.id
            JOIN batches b ON b.id = e.batch_id
            WHERE b.id = :batchId AND b.is_deleted = false AND b.is_active = true
              AND e.is_deleted = false AND e.status = 'ACTIVE'
              AND s.is_deleted = false AND u.is_deleted = false
              AND UPPER(u.status) = 'ACTIVE' AND u.user_type = 'STUDENT'
            """, nativeQuery = true)
    List<UUID> findActiveStudentRecipientIds(@Param("batchId") UUID batchId);

    @Query(value = """
            SELECT u.id
            FROM users u
            JOIN teachers t ON t.user_id = u.id
            JOIN batches b ON b.teacher_id = t.id
            WHERE b.id = :batchId AND b.is_deleted = false AND b.is_active = true
              AND u.is_deleted = false AND UPPER(u.status) = 'ACTIVE'
              AND u.user_type = 'TEACHER'
            """, nativeQuery = true)
    Optional<UUID> findActiveTeacherRecipientId(@Param("batchId") UUID batchId);
}
