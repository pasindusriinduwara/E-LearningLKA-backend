package lk.tutionlms.backend.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentRepository extends JpaRepository<Student, UUID> {
    Optional<Student> findByStudentId(String studentId);

    // මේක අලුතින් එකතු කරන්න (User ගේ ID එකෙන් Student ව හොයාගන්න)
    Optional<Student> findByUserId(UUID userId);
}