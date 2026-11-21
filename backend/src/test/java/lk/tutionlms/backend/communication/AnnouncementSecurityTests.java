package lk.tutionlms.backend.communication;

import lk.tutionlms.backend.common.GlobalExceptionHandler;
import lk.tutionlms.backend.config.SecurityConfig;
import lk.tutionlms.backend.identity.Teacher;
import lk.tutionlms.backend.identity.TeacherRepository;
import lk.tutionlms.backend.identity.User;
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

/** Uses the application's real URL and method security without database or external-service access. */
@SpringJUnitWebConfig(AnnouncementSecurityTests.TestConfig.class)
class AnnouncementSecurityTests {
    @Autowired private WebApplicationContext context;
    @Autowired private AnnouncementRepository announcements;
    @Autowired private TeacherRepository teachers;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(announcements, teachers);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymousRequestsCannotReadGeneralOrBatchAnnouncements() throws Exception {
        mvc.perform(get("/api/v1/announcements")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/announcements/batch/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(announcements, teachers);
    }

    @Test
    void studentFeedUsesThePrincipalEvenWhenAnotherStudentAndBatchAreSupplied() throws Exception {
        User student = principal("STUDENT");
        Announcement own = announcement(UUID.randomUUID(), "Enrolled batch announcement");
        when(announcements.findByStudentUserId(student.getId())).thenReturn(List.of(own));

        mvc.perform(get("/api/v1/announcements").with(user(student))
                        .param("studentId", UUID.randomUUID().toString())
                        .param("batchId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(own.getId().toString()));

        verify(announcements).findByStudentUserId(student.getId());
        verifyNoMoreInteractions(announcements);
        verifyNoInteractions(teachers);
    }

    @Test
    void studentWithoutVisibleEnrollmentsGetsAnEmptyFeedInsteadOfAllAnnouncements() throws Exception {
        User student = principal("STUDENT");
        when(announcements.findByStudentUserId(student.getId())).thenReturn(List.of());

        mvc.perform(get("/api/v1/announcements").with(user(student)))
                .andExpect(status().isOk()).andExpect(content().json("[]"));

        verify(announcements).findByStudentUserId(student.getId());
        verifyNoMoreInteractions(announcements);
        verifyNoInteractions(teachers);
    }

    @Test
    void teacherFeedUsesOwnedBatchesAndPreservesGlobalAnnouncements() throws Exception {
        User teacherUser = principal("TEACHER");
        Teacher teacher = new Teacher();
        teacher.setId(UUID.randomUUID());
        teacher.setUserId(teacherUser.getId());
        Announcement own = announcement(UUID.randomUUID(), "Own batch");
        Announcement global = announcement(null, "Global");
        when(teachers.findByUserId(teacherUser.getId())).thenReturn(Optional.of(teacher));
        when(announcements.findByTeacherId(teacher.getId())).thenReturn(List.of(own, global));

        mvc.perform(get("/api/v1/announcements").with(user(teacherUser))
                        .param("teacherId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(own.getId().toString()))
                .andExpect(jsonPath("$[1].id").value(global.getId().toString()));

        verify(teachers).findByUserId(teacherUser.getId());
        verify(announcements).findByTeacherId(teacher.getId());
        verifyNoMoreInteractions(announcements, teachers);
    }

    @Test
    void missingTeacherProfileDoesNotFallBackToAllAnnouncements() throws Exception {
        User teacher = principal("TEACHER");
        when(teachers.findByUserId(teacher.getId())).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/announcements").with(user(teacher)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(announcements);
    }

    @Test
    void requestingAForeignBatchStillScopesTheReadToTheAuthenticatedStudent() throws Exception {
        User student = principal("STUDENT");
        UUID foreignBatch = UUID.randomUUID();
        when(announcements.findByBatchIdAndStudentUserId(foreignBatch, student.getId()))
                .thenReturn(List.of());

        mvc.perform(get("/api/v1/announcements/batch/" + foreignBatch).with(user(student))
                        .param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));

        verify(announcements).findByBatchIdAndStudentUserId(foreignBatch, student.getId());
        verifyNoMoreInteractions(announcements);
        verifyNoInteractions(teachers);
    }

    @Test
    void unrelatedRolesCannotUseGeneralOrStudentBatchFeeds() throws Exception {
        User administrator = principal("ADMIN");
        mvc.perform(get("/api/v1/announcements").with(user(administrator)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/announcements/batch/" + UUID.randomUUID()).with(user(administrator)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/announcements/batch/" + UUID.randomUUID()).with(user(principal("TEACHER"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(announcements, teachers);
    }

    private static User principal(String role) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUserType(role);
        user.setEmail(role.toLowerCase() + "@example.test");
        return user;
    }

    private static Announcement announcement(UUID batchId, String title) {
        Announcement announcement = Announcement.builder().batchId(batchId).title(title).build();
        announcement.setId(UUID.randomUUID());
        return announcement;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean AnnouncementRepository announcementRepository() { return mock(AnnouncementRepository.class); }
        @Bean TeacherRepository teacherRepository() { return mock(TeacherRepository.class); }
        @Bean AnnouncementController announcementController(AnnouncementRepository announcements, TeacherRepository teachers) {
            return new AnnouncementController(announcements, teachers);
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
