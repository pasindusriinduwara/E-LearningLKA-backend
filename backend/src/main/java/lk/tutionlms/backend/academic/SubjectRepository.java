package lk.tutionlms.backend.academic;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    List<Subject> findByActiveTrueAndDeletedFalseOrderByNameAsc();
}
