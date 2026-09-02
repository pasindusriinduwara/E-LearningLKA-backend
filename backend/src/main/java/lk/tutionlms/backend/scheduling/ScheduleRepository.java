package lk.tutionlms.backend.scheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface ScheduleRepository extends JpaRepository<ScheduleItem, UUID> {

    List<ScheduleItem> findByDeletedFalse();

    @Query("select s from ScheduleItem s where s.deleted = false and s.batchId in (select b.id from lk.tutionlms.backend.batch.Batch b where b.teacherId = :teacherId)")
    List<ScheduleItem> findByTeacherId(@Param("teacherId") UUID teacherId);
}
