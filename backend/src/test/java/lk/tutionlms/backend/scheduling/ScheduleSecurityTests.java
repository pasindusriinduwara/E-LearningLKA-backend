package lk.tutionlms.backend.scheduling;

import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.common.GlobalExceptionHandler;
import lk.tutionlms.backend.enrollment.EnrollmentRepository;
import lk.tutionlms.backend.identity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises JSON binding and method security without a database or external services. */
@SpringJUnitWebConfig(ScheduleSecurityTests.TestConfig.class)
class ScheduleSecurityTests {
    @Autowired private WebApplicationContext context;
    @Autowired private ScheduleRepository schedules;
    @Autowired private TeacherRepository teachers;
    @Autowired private BatchRepository batches;
    private MockMvc mvc;
    private User teacherUser;
    private Teacher teacher;
    private Batch batch;

    @BeforeEach
    void setup() {
        reset(schedules, teachers, batches);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        teacherUser = principal("TEACHER");
        teacher = new Teacher();
        teacher.setId(UUID.randomUUID());
        teacher.setUserId(teacherUser.getId());
        batch = new Batch();
        batch.setId(UUID.randomUUID());
        batch.setTeacherId(teacher.getId());
        when(teachers.findByUserId(teacherUser.getId())).thenReturn(Optional.of(teacher));
        when(batches.findById(batch.getId())).thenReturn(Optional.of(batch));
        when(schedules.save(any(ScheduleItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void injectedIdentityAndAuditFieldsCannotOverwriteAnExistingSchedule() throws Exception {
        UUID victimId = UUID.randomUUID();
        String payload = payload(batch.getId(), """
                ,"id":"%s","deleted":true,"createdAt":"2001-01-01T00:00:00",
                "updatedAt":"2001-01-02T00:00:00"
                """.formatted(victimId));

        mvc.perform(post("/api/v1/schedules").with(user(teacherUser))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        ScheduleItem saved = savedSchedule();
        assertNull(saved.getId(), "Persistence must generate a new ID instead of merging the supplied victim ID");
        assertFalse(saved.isDeleted());
        assertNull(saved.getCreatedAt());
        assertNull(saved.getUpdatedAt());
        assertEquals(batch.getId(), saved.getBatchId());
        verify(schedules, never()).findById(any());
    }

    @Test
    void ownedBatchStillAcceptsTheExistingCreatePayload() throws Exception {
        mvc.perform(post("/api/v1/schedules").with(user(teacherUser))
                        .contentType(MediaType.APPLICATION_JSON).content(payload(batch.getId(), "")))
                .andExpect(status().isOk());

        ScheduleItem saved = savedSchedule();
        assertEquals("Physics revision", saved.getTitle());
        assertEquals(DayOfWeek.MONDAY, saved.getDayOfWeek());
        assertEquals(LocalDate.of(2026, 11, 23), saved.getEffectiveDate());
        assertEquals(LocalTime.of(9, 0), saved.getStartTime());
        assertEquals(LocalTime.of(10, 0), saved.getEndTime());
        assertEquals(DeliveryMode.ONLINE, saved.getMode());
        assertEquals(RecurrenceType.WEEKLY, saved.getRecurrence());
    }

    @Test
    void foreignBatchIsForbiddenEvenWithAnInjectedScheduleId() throws Exception {
        batch.setTeacherId(UUID.randomUUID());
        mvc.perform(post("/api/v1/schedules").with(user(teacherUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(batch.getId(), ",\"id\":\"" + UUID.randomUUID() + "\"")))
                .andExpect(status().isForbidden());
        verify(schedules, never()).save(any());
    }

    @Test
    void deletedBatchCannotReceiveNewSchedules() throws Exception {
        batch.setDeleted(true);
        mvc.perform(post("/api/v1/schedules").with(user(teacherUser))
                        .contentType(MediaType.APPLICATION_JSON).content(payload(batch.getId(), "")))
                .andExpect(status().isNotFound());
        verify(schedules, never()).save(any());
    }

    @Test
    void administratorAlsoCreatesAFreshEntity() throws Exception {
        mvc.perform(post("/api/v1/schedules").with(user(principal("ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(batch.getId(), ",\"id\":\"" + UUID.randomUUID() + "\",\"deleted\":true")))
                .andExpect(status().isOk());
        ScheduleItem saved = savedSchedule();
        assertNull(saved.getId());
        assertFalse(saved.isDeleted());
        verifyNoInteractions(teachers);
    }

    @Test
    void studentsCannotCreateSchedules() throws Exception {
        mvc.perform(post("/api/v1/schedules").with(user(principal("STUDENT")))
                        .contentType(MediaType.APPLICATION_JSON).content(payload(batch.getId(), "")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(schedules, batches, teachers);
    }

    @Test
    void requiredCreationFieldsAreValidatedBeforePersistence() throws Exception {
        mvc.perform(post("/api/v1/schedules").with(user(teacherUser))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(schedules, batches, teachers);
    }

    private ScheduleItem savedSchedule() {
        ArgumentCaptor<ScheduleItem> captor = ArgumentCaptor.forClass(ScheduleItem.class);
        verify(schedules).save(captor.capture());
        return captor.getValue();
    }

    private static User principal(String role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUserType(role);
        return user;
    }

    private static String payload(UUID batchId, String extraFields) {
        return """
                {"batchId":"%s","title":"Physics revision","dayOfWeek":"MONDAY",
                "effectiveDate":"2026-11-23","startTime":"09:00:00","endTime":"10:00:00",
                "mode":"ONLINE","location":"Virtual classroom"%s}
                """.formatted(batchId, extraFields);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfig {
        @Bean ScheduleRepository scheduleRepository() { return mock(ScheduleRepository.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean StudentRepository studentRepository() { return mock(StudentRepository.class); }
        @Bean EnrollmentRepository enrollmentRepository() { return mock(EnrollmentRepository.class); }
        @Bean TeacherRepository teacherRepository() { return mock(TeacherRepository.class); }
        @Bean BatchRepository batchRepository() { return mock(BatchRepository.class); }
        @Bean GlobalExceptionHandler exceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean ScheduleController scheduleController(ScheduleRepository schedules, UserRepository users,
                StudentRepository students, EnrollmentRepository enrollments, TeacherRepository teachers,
                BatchRepository batches) {
            return new ScheduleController(schedules, users, students, enrollments, teachers, batches,
                    mock(lk.tutionlms.backend.notification.NotificationService.class));
        }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(requests -> requests.anyRequest().authenticated())
                    .build();
        }
    }
}
