package lk.tutionlms.backend.identity.auth;

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
    private final PasswordEncoder passwordEncoder; // අලුතෙන් add කරපු එක

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
        // 1. Email එක දැනටමත් තියෙනවද කියලා check කරනවා
        if (repository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use");
        }

        var user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // Password Hashing
                .phoneNumber(request.getPhoneNumber())
                .userType(request.getUserType())
                .status("ACTIVE")
                .build();

        repository.save(user);
        if ("STUDENT".equalsIgnoreCase(request.getUserType())) {
            String first = request.getFirstName() == null ? "" : request.getFirstName().trim();
            String last = request.getLastName() == null ? "" : request.getLastName().trim();
            String initials = (first + last).replaceAll("\\s+", "").toUpperCase();
            if (initials.length() > 2) initials = initials.substring(0, 2);
            LocalDate dob = request.getDob() == null || request.getDob().isBlank() ? null : LocalDate.parse(request.getDob());
            studentRepository.save(Student.builder().userId(user.getId()).name((first + " " + last).trim())
                    .initials(initials).studentId("ST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                    .exam(request.getGrade()).stream(request.getStream()).medium(request.getMedium()).dateOfBirth(dob).build());
        } else if ("TEACHER".equalsIgnoreCase(request.getUserType())) {
            String first = request.getFirstName() == null ? "" : request.getFirstName().trim();
            String last = request.getLastName() == null ? "" : request.getLastName().trim();
            teacherRepository.save(Teacher.builder().userId(user.getId())
                    .name((first + " " + last).trim())
                    .qualification(request.getQualification()).bio(request.getExperience()).build());
        }

        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder()
                .token(jwtToken)
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        var user = repository.findByEmail(request.getEmail()).orElseThrow();
        var jwtToken = jwtService.generateToken(user);
        return AuthenticationResponse.builder().token(jwtToken).build();
    }
}
