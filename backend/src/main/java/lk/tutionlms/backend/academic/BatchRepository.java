package lk.tutionlms.backend.academic;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchRepository extends JpaRepository<Batch, UUID> {

    List<Batch> findByTeacherIdAndDeletedFalse(UUID teacherId);

    long countByTeacherIdAndActiveTrueAndDeletedFalse(UUID teacherId);

    boolean existsByIdAndTeacherIdAndDeletedFalse(UUID id, UUID teacherId);

    List<Batch> findByActiveTrueAndDeletedFalse();
}