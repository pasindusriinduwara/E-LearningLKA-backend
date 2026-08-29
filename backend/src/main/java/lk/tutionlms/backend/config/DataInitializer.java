package lk.tutionlms.backend.config;

import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final StudentRepository studentRepository;

    @Override
    public void run(String... args) {
        // Seed Default Student data if table is empty
        try {
            if (studentRepository.count() == 0) {
                log.info("⚡ Seeding initial student data into PostgreSQL...");

                Student defaultStudent = Student.builder()
                        .name("pasinduaa")
                        .initials("SA")
                        .studentId("24081")
                        .exam("A/L 2026")
                        .stream("Physical Science")
                        .medium("Sinhala medium")
                        .build();

                studentRepository.save(defaultStudent);
                log.info("✅ Student data seeded successfully with ID: {}", defaultStudent.getStudentId());
            }
        } catch (Exception e) {
            log.warn("⚠️ Student data initialization note: {}", e.getMessage());
        }
    }
}
