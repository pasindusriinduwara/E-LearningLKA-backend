package lk.tutionlms.backend.batch;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchRepository extends JpaRepository<Batch, UUID> {

    List<Batch> findByTeacherIdAndDeletedFalse(UUID teacherId);

    long countByTeacherIdAndActiveTrueAndDeletedFalse(UUID teacherId);

    boolean existsByIdAndTeacherIdAndDeletedFalse(UUID id, UUID teacherId);

    List<Batch> findByActiveTrueAndDeletedFalse();
}
