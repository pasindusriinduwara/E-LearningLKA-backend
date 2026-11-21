package lk.tutionlms.backend.content;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Repository
public interface MaterialRepository extends JpaRepository<LearningMaterial, UUID> {
    List<LearningMaterial> findByDeletedFalse();

    long countByBatchIdAndDeletedFalse(UUID batchId);

    @Query("SELECT m.batchId, COUNT(m) FROM LearningMaterial m WHERE m.batchId IN :batchIds AND m.deleted = false GROUP BY m.batchId")
    List<Object[]> countMaterialsByBatchIds(@Param("batchIds") java.util.Collection<UUID> batchIds);

    List<LearningMaterial> findByBatchIdAndDeletedFalseOrderByCreatedAtDesc(UUID batchId);

    List<LearningMaterial> findByBatchIdInAndDeletedFalseOrderByCreatedAtDesc(List<UUID> batchIds);

    @Query("select m from LearningMaterial m where m.deleted = false and m.batchId in (select b.id from lk.tutionlms.backend.batch.Batch b where b.teacherId = :teacherId and b.deleted = false) order by m.createdAt desc")
    List<LearningMaterial> findByTeacherId(@Param("teacherId") UUID teacherId);

    @Query("select m from LearningMaterial m where m.deleted = false and m.batchId in (select e.batchId from lk.tutionlms.backend.enrollment.Enrollment e where e.deleted = false and e.status = 'ACTIVE' and e.studentId in (select s.id from lk.tutionlms.backend.identity.Student s where s.userId = :userId and s.deleted = false)) order by m.createdAt desc")
    List<LearningMaterial> findByStudentUserId(@Param("userId") UUID userId);

    @Query("select m from LearningMaterial m where m.deleted = false and m.batchId = :batchId and m.batchId in (select e.batchId from lk.tutionlms.backend.enrollment.Enrollment e where e.deleted = false and e.status = 'ACTIVE' and e.studentId in (select s.id from lk.tutionlms.backend.identity.Student s where s.userId = :userId and s.deleted = false)) order by m.createdAt desc")
    List<LearningMaterial> findByBatchIdAndStudentUserId(@Param("batchId") UUID batchId, @Param("userId") UUID userId);
}
