package lk.tutionlms.backend.batch;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BatchRepository extends JpaRepository<Batch, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from Batch b where b.id = :id and b.deleted = false")
    java.util.Optional<Batch> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    List<Batch> findByTeacherIdAndDeletedFalse(UUID teacherId);

    long countByTeacherIdAndActiveTrueAndDeletedFalse(UUID teacherId);

    boolean existsByIdAndTeacherIdAndDeletedFalse(UUID id, UUID teacherId);

    List<Batch> findByActiveTrueAndDeletedFalse();
}
