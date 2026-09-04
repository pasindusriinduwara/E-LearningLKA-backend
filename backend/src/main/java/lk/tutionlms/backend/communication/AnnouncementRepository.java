package lk.tutionlms.backend.communication;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface AnnouncementRepository extends JpaRepository<Announcement, UUID> {
    List<Announcement> findByDeletedFalse();

    @Query("select a from Announcement a where a.deleted = false and (a.batchId in (select b.id from lk.tutionlms.backend.batch.Batch b where b.teacherId = :teacherId) or a.batchId is null)")
    List<Announcement> findByTeacherId(@Param("teacherId") UUID teacherId);
}
