package lk.tutionlms.backend.identity;

import jakarta.validation.Valid;
import lk.tutionlms.backend.identity.dto.StudentProfileResponse;
import lk.tutionlms.backend.identity.dto.UpdateStudentProfileRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    @GetMapping("/profile")
    public ResponseEntity<StudentProfileResponse> getProfile(@AuthenticationPrincipal User user) {
        return studentRepository.findByUserId(user.getId())
                .map(student -> ResponseEntity.ok(toResponse(student, user)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/profile")
    @Transactional
    public ResponseEntity<StudentProfileResponse> updateProfile(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody UpdateStudentProfileRequest request) {

        Student student = studentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Student profile not found"));

        student.setName(request.getName().trim());

        if (request.getInitials() != null && !request.getInitials().isBlank()) {
            student.setInitials(request.getInitials().trim().toUpperCase());
        } else {
            student.setInitials(generateInitials(request.getName()));
        }

        student.setExam(request.getExam());
        student.setStream(request.getStream());
        student.setMedium(request.getMedium());
        student.setDateOfBirth(request.getDateOfBirth());

        studentRepository.save(student);

        if (request.getPhoneNumber() != null) {
            user = userRepository.findById(user.getId()).map(u -> {
                u.setPhoneNumber(request.getPhoneNumber().trim());
                return userRepository.save(u);
            }).orElse(user);
        }

        return ResponseEntity.ok(toResponse(student, user));
    }

    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Student>> getAllStudents() {
        return ResponseEntity.ok(studentRepository.findAll());
    }

    private StudentProfileResponse toResponse(Student student, User user) {
        return StudentProfileResponse.builder()
                .id(student.getId())
                .userId(student.getUserId())
                .studentId(student.getStudentId())
                .name(student.getName())
                .initials(student.getInitials())
                .email(user != null ? user.getEmail() : null)
                .phoneNumber(user != null ? user.getPhoneNumber() : null)
                .exam(student.getExam())
                .stream(student.getStream())
                .medium(student.getMedium())
                .dateOfBirth(student.getDateOfBirth())
                .build();
    }

    private String generateInitials(String name) {
        if (name == null || name.isBlank()) return "ST";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}
