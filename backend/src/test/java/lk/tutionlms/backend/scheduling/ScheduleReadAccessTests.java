package lk.tutionlms.backend.scheduling;

import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.common.GlobalExceptionHandler;
import lk.tutionlms.backend.config.SecurityConfig;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.StudentRepository;
import lk.tutionlms.backend.identity.Teacher;
import lk.tutionlms.backend.identity.TeacherRepository;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.identity.UserRepository;
import lk.tutionlms.backend.notification.NotificationService;
import lk.tutionlms.backend.security.JwtAuthenticationFilter;
import lk.tutionlms.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Exercises the application's URL and method security without database or external-service access. */
@SpringJUnitWebConfig(ScheduleReadAccessTests.TestConfig.class)
class ScheduleReadAccessTests {
    @Autowired private WebApplicationContext context;
    @Autowired private ScheduleRepository schedules;
    @Autowired private UserRepository users;
    @Autowired private StudentRepository students;
    @Autowired private EnrollmentRepository enrollments;
    @Autowired private TeacherRepository teachers;
    @Autowired private BatchRepository batches;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(schedules, users, students, enrollments, teachers, batches);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void everyScheduleReadRequiresAuthentication() throws Exception {
        for (String path : List.of("", "/upcoming", "/batch/" + UUID.randomUUID(), "/" + UUID.randomUUID())) {
            mvc.perform(get("/api/v1/schedules" + path)).andExpect(status().isForbidden());
        }
        verifyNoInteractions(schedules, users, students, enrollments, teachers, batches);
    }

    @Test
    void unrelatedRolesCannotReadSchedules() throws Exception {
        User administrator = principal("ADMIN");
        for (String path : List.of("", "/upcoming", "/batch/" + UUID.randomUUID(), "/" + UUID.randomUUID())) {
            mvc.perform(get("/api/v1/schedules" + path).with(user(administrator)))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(schedules, users, students, enrollments, teachers, batches);
    }

    @Test
    void studentFeedsUseOnlyCanonicalActiveEnrollmentsOfThePrincipal() throws Exception {
        User studentUser = principal("STUDENT");
        Student student = student(studentUser);
        UUID ownBatchId = UUID.randomUUID();
        ScheduleItem own = schedule(ownBatchId, "Approved class schedule");
        when(students.findByUserId(studentUser.getId())).thenReturn(Optional.of(student));
        when(enrollments.findActiveBatchIdsByStudentId(student.getId())).thenReturn(List.of(ownBatchId));
        when(schedules.findByBatchIdInAndDeletedFalse(List.of(ownBatchId))).thenReturn(List.of(own));

        for (String path : List.of("", "/upcoming")) {
            mvc.perform(get("/api/v1/schedules" + path).with(user(studentUser))
                            .param("studentId", UUID.randomUUID().toString())
                            .param("batchId", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(own.getId().toString()));
        }

        verify(enrollments, times(2)).findActiveBatchIdsByStudentId(student.getId());
        verify(schedules, times(2)).findByBatchIdInAndDeletedFalse(List.of(ownBatchId));
        verifyNoMoreInteractions(schedules);
        verifyNoInteractions(users, teachers, batches);
    }

    @Test
    void studentWithoutAccessGetsEmptyFeedsWithoutFallingBackToEverySchedule() throws Exception {
        User studentUser = principal("STUDENT");
        Student student = student(studentUser);
        when(students.findByUserId(studentUser.getId())).thenReturn(Optional.of(student));
        when(enrollments.findActiveBatchIdsByStudentId(student.getId())).thenReturn(List.of());

        for (String path : List.of("", "/upcoming")) {
            mvc.perform(get("/api/v1/schedules" + path).with(user(studentUser)))
                    .andExpect(status().isOk()).andExpect(content().json("[]"));
        }

        verifyNoInteractions(schedules, users, teachers, batches);
    }

    @Test
    void allowedStudentCanReadBatchAndIndividualSchedule() throws Exception {
        User studentUser = principal("STUDENT");
        Student student = student(studentUser);
        Batch batch = batch(UUID.randomUUID());
        ScheduleItem item = schedule(batch.getId(), "Allowed class");
        when(students.findByUserId(studentUser.getId())).thenReturn(Optional.of(student));
        when(batches.findById(batch.getId())).thenReturn(Optional.of(batch));
        when(enrollments.isStudentActiveInBatch(student.getId(), batch.getId())).thenReturn(true);
        when(schedules.findByBatchIdAndDeletedFalse(batch.getId())).thenReturn(List.of(item));
        when(schedules.findById(item.getId())).thenReturn(Optional.of(item));

        mvc.perform(get("/api/v1/schedules/batch/" + batch.getId()).with(user(studentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(item.getId().toString()));
        mvc.perform(get("/api/v1/schedules/" + item.getId()).with(user(studentUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(item.getId().toString()));

        verify(enrollments, times(2)).isStudentActiveInBatch(student.getId(), batch.getId());
    }

    @Test
    void revokingAccessImmediatelyBlocksPreviouslyAllowedStudentReads() throws Exception {
        User studentUser = principal("STUDENT");
        Student student = student(studentUser);
        Batch batch = batch(UUID.randomUUID());
        ScheduleItem item = schedule(batch.getId(), "Previously approved class");
        when(students.findByUserId(studentUser.getId())).thenReturn(Optional.of(student));
        when(batches.findById(batch.getId())).thenReturn(Optional.of(batch));
        when(schedules.findById(item.getId())).thenReturn(Optional.of(item));
        when(schedules.findByBatchIdAndDeletedFalse(batch.getId())).thenReturn(List.of(item));
        when(enrollments.isStudentActiveInBatch(student.getId(), batch.getId())).thenReturn(true);

        mvc.perform(get("/api/v1/schedules/batch/" + batch.getId()).with(user(studentUser)))
                .andExpect(status().isOk());

        // The same authenticated session loses access when the teacher drops its active enrollment.
        when(enrollments.isStudentActiveInBatch(student.getId(), batch.getId())).thenReturn(false);
        when(enrollments.findActiveBatchIdsByStudentId(student.getId())).thenReturn(List.of());
        clearInvocations(schedules);

        mvc.perform(get("/api/v1/schedules/batch/" + batch.getId()).with(user(studentUser)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/schedules/" + item.getId()).with(user(studentUser)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/schedules").with(user(studentUser)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/schedules/upcoming").with(user(studentUser)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));

        verify(schedules).findById(item.getId());
        verifyNoMoreInteractions(schedules);
    }

    @Test
    void inactiveClassCannotBeReadEvenWithAnActiveEnrollment() throws Exception {
        User studentUser = principal("STUDENT");
        Student student = student(studentUser);
        Batch batch = batch(UUID.randomUUID());
        batch.setActive(false);
        when(students.findByUserId(studentUser.getId())).thenReturn(Optional.of(student));
        when(batches.findById(batch.getId())).thenReturn(Optional.of(batch));

        mvc.perform(get("/api/v1/schedules/batch/" + batch.getId()).with(user(studentUser)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(schedules, enrollments);
    }

    @Test
    void deletedStudentProfileCannotReadFeedsOrClassContent() throws Exception {
        User studentUser = principal("STUDENT");
        Student student = student(studentUser);
        student.setDeleted(true);
        Batch batch = batch(UUID.randomUUID());
        when(students.findByUserId(studentUser.getId())).thenReturn(Optional.of(student));
        when(batches.findById(batch.getId())).thenReturn(Optional.of(batch));

        mvc.perform(get("/api/v1/schedules").with(user(studentUser))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/schedules/upcoming").with(user(studentUser))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/schedules/batch/" + batch.getId()).with(user(studentUser)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(schedules, enrollments);
    }

    @Test
    void disabledAccountCannotReuseAnAuthenticatedSessionToReadSchedules() throws Exception {
        User studentUser = principal("STUDENT");
        studentUser.setStatus("INACTIVE");

        mvc.perform(get("/api/v1/schedules").with(user(studentUser))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/schedules/upcoming").with(user(studentUser))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/schedules/batch/" + UUID.randomUUID()).with(user(studentUser)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(schedules, users, students, enrollments, teachers, batches);
    }

    @Test
    void teacherFeedsUseOnlyOwnedBatchesRegardlessOfSuppliedTeacherId() throws Exception {
        User teacherUser = principal("TEACHER");
        Teacher teacher = teacher(teacherUser);
        ScheduleItem own = schedule(UUID.randomUUID(), "Own class schedule");
        when(teachers.findByUserId(teacherUser.getId())).thenReturn(Optional.of(teacher));
        when(schedules.findByTeacherId(teacher.getId())).thenReturn(List.of(own));

        for (String path : List.of("", "/upcoming")) {
            mvc.perform(get("/api/v1/schedules" + path).with(user(teacherUser))
                            .param("teacherId", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(own.getId().toString()));
        }

        verify(schedules, times(2)).findByTeacherId(teacher.getId());
        verifyNoMoreInteractions(schedules);
        verifyNoInteractions(users, students, enrollments, batches);
    }

    @Test
    void teacherCanReadOwnedBatchAndScheduleWithoutStudentEnrollment() throws Exception {
        User teacherUser = principal("TEACHER");
        Teacher teacher = teacher(teacherUser);
        Batch batch = batch(teacher.getId());
        ScheduleItem own = schedule(batch.getId(), "Own class schedule");
        when(teachers.findByUserId(teacherUser.getId())).thenReturn(Optional.of(teacher));
        when(batches.findById(batch.getId())).thenReturn(Optional.of(batch));
        when(schedules.findByBatchIdAndDeletedFalse(batch.getId())).thenReturn(List.of(own));
        when(schedules.findById(own.getId())).thenReturn(Optional.of(own));

        mvc.perform(get("/api/v1/schedules/batch/" + batch.getId()).with(user(teacherUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(own.getId().toString()));
        mvc.perform(get("/api/v1/schedules/" + own.getId()).with(user(teacherUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(own.getId().toString()));

        verifyNoInteractions(students, enrollments);
    }

    @Test
    void teacherCannotReadAnotherTeachersBatchOrIndividualSchedule() throws Exception {
        User teacherUser = principal("TEACHER");
        Teacher teacher = teacher(teacherUser);
        Batch foreign = batch(UUID.randomUUID());
        ScheduleItem item = schedule(foreign.getId(), "Foreign class schedule");
        when(teachers.findByUserId(teacherUser.getId())).thenReturn(Optional.of(teacher));
        when(batches.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        when(schedules.findById(item.getId())).thenReturn(Optional.of(item));

        mvc.perform(get("/api/v1/schedules/batch/" + foreign.getId()).with(user(teacherUser)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/schedules/" + item.getId()).with(user(teacherUser)))
                .andExpect(status().isForbidden());

        verify(schedules).findById(item.getId());
        verifyNoMoreInteractions(schedules);
        verifyNoInteractions(students, enrollments);
    }

    @Test
    void missingTeacherProfileDoesNotFallBackToAllSchedules() throws Exception {
        User teacherUser = principal("TEACHER");

        mvc.perform(get("/api/v1/schedules").with(user(teacherUser))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/schedules/upcoming").with(user(teacherUser))).andExpect(status().isForbidden());

        verifyNoInteractions(schedules, students, enrollments);
    }

    private static User principal(String role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUserType(role);
        user.setEmail(role.toLowerCase() + "@example.test");
        return user;
    }

    private static Student student(User user) {
        Student student = new Student();
        student.setId(UUID.randomUUID());
        student.setUserId(user.getId());
        return student;
    }

    private static Teacher teacher(User user) {
        Teacher teacher = new Teacher();
        teacher.setId(UUID.randomUUID());
        teacher.setUserId(user.getId());
        return teacher;
    }

    private static Batch batch(UUID teacherId) {
        Batch batch = new Batch();
        batch.setId(UUID.randomUUID());
        batch.setTeacherId(teacherId);
        batch.setActive(true);
        return batch;
    }

    private static ScheduleItem schedule(UUID batchId, String title) {
        ScheduleItem item = ScheduleItem.builder().batchId(batchId).title(title).build();
        item.setId(UUID.randomUUID());
        return item;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean ScheduleRepository scheduleRepository() { return mock(ScheduleRepository.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean StudentRepository studentRepository() { return mock(StudentRepository.class); }
        @Bean EnrollmentRepository enrollmentRepository() { return mock(EnrollmentRepository.class); }
        @Bean TeacherRepository teacherRepository() { return mock(TeacherRepository.class); }
        @Bean BatchRepository batchRepository() { return mock(BatchRepository.class); }
        @Bean ScheduleController scheduleController(ScheduleRepository schedules, UserRepository users,
                StudentRepository students, EnrollmentRepository enrollments, TeacherRepository teachers,
                BatchRepository batches) {
            return new ScheduleController(schedules, users, students, enrollments, teachers, batches,
                    mock(NotificationService.class));
        }
        @Bean GlobalExceptionHandler exceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean AuthenticationProvider authenticationProvider() { return mock(AuthenticationProvider.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserDetailsService userDetailsService() { return mock(UserDetailsService.class); }
        @Bean JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwt, UserDetailsService users) {
            return new JwtAuthenticationFilter(jwt, users);
        }
    }
}
