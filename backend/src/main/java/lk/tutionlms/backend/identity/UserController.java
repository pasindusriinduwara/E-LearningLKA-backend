package lk.tutionlms.backend.identity;

import lk.tutionlms.backend.identity.dto.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal User user) {
        UserProfileResponse.UserProfileResponseBuilder builder = UserProfileResponse.builder()
                .userId(user.getId().toString())
                .email(user.getEmail())
                .role(user.getUserType());

        if ("STUDENT".equalsIgnoreCase(user.getUserType())) {
            studentRepository.findByUserId(user.getId()).ifPresent(student -> {
                builder.id(student.getId() != null ? student.getId().toString() : null)
                        .name(student.getName())
                        .initials(student.getInitials())
                        .studentId(student.getStudentId())
                        .exam(student.getExam())
                        .stream(student.getStream())
                        .medium(student.getMedium());
            });
        } else if ("TEACHER".equalsIgnoreCase(user.getUserType())) {
            teacherRepository.findByUserId(user.getId()).ifPresent(teacher -> {
                builder.id(teacher.getId() != null ? teacher.getId().toString() : null)
                        .name(teacher.getName())
                        .title(teacher.getTitle())
                        .qualification(teacher.getQualification())
                        .bio(teacher.getBio());
            });
        }

        return ResponseEntity.ok(builder.build());
    }
}
