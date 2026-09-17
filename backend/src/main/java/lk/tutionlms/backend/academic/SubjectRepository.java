package lk.tutionlms.backend.academic;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    List<Subject> findByActiveTrueAndDeletedFalseOrderByNameAsc();
    Optional<Subject> findByNameIgnoreCaseAndDeletedFalse(String name);
    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);
}
