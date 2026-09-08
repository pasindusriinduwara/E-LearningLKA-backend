package lk.tutionlms.backend.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduleRepository extends JpaRepository<ScheduleItem, UUID> {

    List<ScheduleItem> findByDeletedFalse();

    @Query("SELECT s FROM ScheduleItem s WHERE s.deleted = false AND s.batchId IN " +
            "(SELECT b.id FROM lk.tutionlms.backend.batch.Batch b WHERE b.teacherId = :teacherId AND b.deleted = false)")
    List<ScheduleItem> findByTeacherId(@Param("teacherId") UUID teacherId);

    /**
     * Finds overlapping schedule slots on the same day of the week.
     * Overlap condition: (s.startTime < :endTime AND s.endTime > :startTime)
     * Scope: Conflicting if either the Batch OR the Teacher is already booked.
     */
    @Query("SELECT s FROM ScheduleItem s WHERE s.deleted = false " +
            "AND s.dayOfWeek = :dayOfWeek " +
            "AND ( " +
            "  s.batchId = :batchId " +
            "  OR s.batchId IN (SELECT b.id FROM lk.tutionlms.backend.batch.Batch b WHERE b.teacherId = :teacherId AND b.deleted = false) "
            +
            ") " +
            "AND (s.startTime < :endTime AND s.endTime > :startTime)")
    List<ScheduleItem> findConflictingSchedules(
            @Param("teacherId") UUID teacherId,
            @Param("batchId") UUID batchId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

    @Query("SELECT s FROM ScheduleItem s WHERE s.deleted = false " +
            "AND s.id != :excludeScheduleId " +
            "AND s.dayOfWeek = :dayOfWeek " +
            "AND ( " +
            "  s.batchId = :batchId " +
            "  OR s.batchId IN (SELECT b.id FROM lk.tutionlms.backend.batch.Batch b WHERE b.teacherId = :teacherId AND b.deleted = false) "
            +
            ") " +
            "AND (s.startTime < :endTime AND s.endTime > :startTime)")
    List<ScheduleItem> findConflictingSchedulesExcluding(
            @Param("excludeScheduleId") UUID excludeScheduleId,
            @Param("teacherId") UUID teacherId,
            @Param("batchId") UUID batchId,
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

}
