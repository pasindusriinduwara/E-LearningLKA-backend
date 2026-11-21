package lk.tutionlms.backend.academic;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/subjects")
@RequiredArgsConstructor
public class SubjectController {
    private final SubjectRepository subjectRepository;

    @GetMapping
    public List<Subject> list() {
        return subjectRepository.findByActiveTrueAndDeletedFalseOrderByNameAsc();
    }

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public Subject create(@RequestBody Subject request) {
        String name = request.getName() == null ? "" : request.getName().trim().replaceAll("\\s+", " ");
        if (name.isBlank()) throw new IllegalArgumentException("Subject name is required");
        if (name.length() > 100) throw new IllegalArgumentException("Subject name cannot exceed 100 characters");
        return subjectRepository.findByNameIgnoreCaseAndDeletedFalse(name)
                .orElseGet(() -> subjectRepository.save(Subject.builder().name(name).active(true).build()));
    }
}
