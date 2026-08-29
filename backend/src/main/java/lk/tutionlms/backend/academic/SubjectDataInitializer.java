package lk.tutionlms.backend.academic;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubjectDataInitializer implements CommandLineRunner {
    private final SubjectRepository subjectRepository;

    @Override
    public void run(String... args) {
        if (subjectRepository.count() > 0) return;
        subjectRepository.saveAll(List.of(
            Subject.builder().name("Combined Mathematics").build(),
            Subject.builder().name("Pure Mathematics").build(),
            Subject.builder().name("Mathematics").build(),
            Subject.builder().name("Physics").build(),
            Subject.builder().name("Chemistry").build()
        ));
    }
}
