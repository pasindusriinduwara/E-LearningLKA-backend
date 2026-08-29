package lk.tutionlms.backend.identity;

import lk.tutionlms.backend.identity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface TeacherRepository extends JpaRepository<Teacher, UUID> {
}