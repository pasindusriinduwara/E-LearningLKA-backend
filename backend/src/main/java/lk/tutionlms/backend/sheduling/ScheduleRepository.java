package lk.tutionlms.backend.sheduling;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ScheduleRepository extends JpaRepository<ScheduleItem, UUID> {

    // Retrieve all active (not soft-deleted) schedules
    List<ScheduleItem> findByDeletedFalse();
}
