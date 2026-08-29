package lk.tutionlms.backend.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {

    // Spring Data JPA automatically generates the SQL query:
    // SELECT * FROM students WHERE student_id = ? AND is_deleted = false
    Optional<Student> findByStudentId(String studentId);
}