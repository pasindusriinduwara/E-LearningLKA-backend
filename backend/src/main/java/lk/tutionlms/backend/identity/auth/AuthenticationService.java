package lk.tutionlms.backend.identity.auth;

import lk.tutionlms.backend.common.DuplicateResourceException;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.identity.UserRepository;
import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.StudentRepository;
import lk.tutionlms.backend.identity.Teacher;
import lk.tutionlms.backend.identity.TeacherRepository;
import lk.tutionlms.backend.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class AuthenticationService {

    private final UserRepository repository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthenticationService(UserRepository repository, JwtService jwtService,
            AuthenticationManager authenticationManager, PasswordEncoder passwordEncoder,
            StudentRepository studentRepository, TeacherRepository teacherRepository) {
        this.repository = repository;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
    }

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        if (request.getEmail() == null || request.getEmail().trim().isBlank()) {
            throw new IllegalArgumentException("Email address is required.");
        }

        String email = request.getEmail().trim().toLowerCase();

        if (repository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account with this email address already exists. Please sign in instead.");
        }

        String rawRole = request.getUserType() == null ? "STUDENT" : request.getUserType().trim().toUpperCase();
        if (!"STUDENT".equals(rawRole) && !"TEACHER".equals(rawRole)) {
            throw new IllegalArgumentException("Registration role must be STUDENT or TEACHER.");
        }

        String phone = (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isBlank())
                ? request.getPhoneNumber().trim()
                : null;

        var user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(phone)
                .userType(rawRole)
                .status("ACTIVE")
                .build();

        user = repository.save(user);

        String first = request.getFirstName() == null ? "" : request.getFirstName().trim();
        String last = request.getLastName() == null ? "" : request.getLastName().trim();
        String fullName = (first + " " + last).trim();
        if (fullName.isBlank()) {
            fullName = email.split("@")[0];
        }

        if ("STUDENT".equalsIgnoreCase(rawRole)) {
            String initials = (first + last).replaceAll("\\s+", "").toUpperCase();
            if (initials.isEmpty()) {
                initials = fullName.length() >= 2 ? fullName.substring(0, 2).toUpperCase() : "ST";
            } else if (initials.length() > 2) {
                initials = initials.substring(0, 2);
            }

            LocalDate dob = null;
            if (request.getDob() != null && !request.getDob().trim().isBlank()) {
                try {
                    dob = LocalDate.parse(request.getDob().trim());
                } catch (Exception ignored) {
                }
            }

            String studentCode = "ST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            while (studentRepository.findByStudentId(studentCode).isPresent()) {
                studentCode = "ST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            }

            studentRepository.save(Student.builder()
                    .userId(user.getId())
                    .name(fullName)
                    .initials(initials)
                    .studentId(studentCode)
                    .exam(request.getGrade())
                    .stream(request.getStream())
                    .medium(request.getMedium())
                    .dateOfBirth(dob)
                    .build());
        } else if ("TEACHER".equalsIgnoreCase(rawRole)) {
            teacherRepository.save(Teacher.builder()
                    .userId(user.getId())
                    .name(fullName)
                    .qualification(request.getQualification())
                    .bio(request.getExperience())
                    .build());
        }

        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .role(user.getUserType())
                .email(user.getEmail())
                .name(fullName)
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        var user = repository.findByEmail(request.getEmail()).orElseThrow();
        var jwtToken = jwtService.generateToken(user);

        String fullName = "";
        if ("STUDENT".equalsIgnoreCase(user.getUserType())) {
            fullName = studentRepository.findByUserId(user.getId()).map(Student::getName).orElse("");
        } else if ("TEACHER".equalsIgnoreCase(user.getUserType())) {
            fullName = teacherRepository.findByUserId(user.getId()).map(Teacher::getName).orElse("");
        }

        return AuthenticationResponse.builder()
                .token(jwtToken)
                .role(user.getUserType())
                .email(user.getEmail())
                .name(fullName)
                .build();
    }
}
