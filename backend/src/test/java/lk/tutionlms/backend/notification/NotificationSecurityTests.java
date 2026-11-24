package lk.tutionlms.backend.notification;

import lk.tutionlms.backend.common.GlobalExceptionHandler;
import lk.tutionlms.backend.config.SecurityConfig;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.security.JwtAuthenticationFilter;
import lk.tutionlms.backend.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real application URL and method security; no database or external service is used. */
@SpringJUnitWebConfig(NotificationSecurityTests.TestConfig.class)
class NotificationSecurityTests {
    @Autowired private WebApplicationContext context;
    @Autowired private NotificationService notifications;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(notifications);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymousUsersCannotListCountOrChangeReadState() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/notifications/" + UUID.randomUUID() + "/read")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/notifications/read-all")).andExpect(status().isForbidden());
        verifyNoInteractions(notifications);
    }

    @Test
    void bothRolesGetOnlyThePrincipalInboxIgnoringSuppliedRecipients() throws Exception {
        for (String role : List.of("STUDENT", "TEACHER")) {
            User principal = principal(role);
            NotificationResponse item = item();
            when(notifications.list(principal.getId(), 0, 20))
                    .thenReturn(new NotificationPageResponse(List.of(item), 1, 0, 20, 1, 1));

            mvc.perform(get("/api/v1/notifications").with(user(principal))
                            .param("recipientId", UUID.randomUUID().toString()))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.items[0].id").value(item.id().toString()))
                    .andExpect(jsonPath("$.items[0].read").value(false))
                    .andExpect(jsonPath("$.items[0].createdAt").value("2026-11-21T05:00:00Z"))
                    .andExpect(jsonPath("$.items[0].recipientId").doesNotExist())
                    .andExpect(jsonPath("$.unreadCount").value(1));
            verify(notifications).list(principal.getId(), 0, 20);
        }
    }

    @Test
    void unreadCountAlwaysUsesThePrincipal() throws Exception {
        User principal = principal("TEACHER");
        when(notifications.unreadCount(principal.getId())).thenReturn(3L);

        mvc.perform(get("/api/v1/notifications/unread-count").with(user(principal))
                        .param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(3));

        verify(notifications).unreadCount(principal.getId());
    }

    @Test
    void readingAForeignOrMissingNotificationReturnsNotFoundWithoutDisclosingItsOwner() throws Exception {
        User principal = principal("STUDENT");
        UUID foreignId = UUID.randomUUID();
        when(notifications.markRead(principal.getId(), foreignId))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        mvc.perform(patch("/api/v1/notifications/" + foreignId + "/read").with(user(principal)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Notification not found"));

        verify(notifications).markRead(principal.getId(), foreignId);
    }

    @Test
    void ownReadUpdateUsesPrincipalAndReturnsOnlyTheNotificationDto() throws Exception {
        User principal = principal("STUDENT");
        NotificationResponse entry = item();
        when(notifications.markRead(principal.getId(), entry.id())).thenReturn(entry);

        mvc.perform(patch("/api/v1/notifications/" + entry.id() + "/read").with(user(principal))
                        .contentType("application/json").content("{\"recipientId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(entry.id().toString()))
                .andExpect(jsonPath("$.recipientId").doesNotExist());

        verify(notifications).markRead(principal.getId(), entry.id());
    }

    @Test
    void readAllReturnsTheRemainingCountIncludingConcurrentNotifications() throws Exception {
        User principal = principal("TEACHER");
        when(notifications.markAllRead(principal.getId())).thenReturn(1L);

        mvc.perform(patch("/api/v1/notifications/read-all").with(user(principal))
                        .param("recipientId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1));

        verify(notifications).markAllRead(principal.getId());
    }

    @Test
    void unrelatedRolesAndDisabledAccountsCannotUseTheInbox() throws Exception {
        User administrator = principal("ADMIN");
        mvc.perform(get("/api/v1/notifications").with(user(administrator))).andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/notifications/read-all").with(user(administrator))).andExpect(status().isForbidden());
        User suspended = principal("STUDENT");
        suspended.setStatus("SUSPENDED");
        mvc.perform(get("/api/v1/notifications").with(user(suspended))).andExpect(status().isForbidden());
        verifyNoInteractions(notifications);
    }

    @Test
    void malformedIdsAndPaginationReturnBadRequestWithoutQueryingNotifications() throws Exception {
        User principal = principal("STUDENT");
        mvc.perform(get("/api/v1/notifications").with(user(principal)).param("page", "invalid"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/notifications").with(user(principal)).param("size", "999999999999"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/notifications/not-a-uuid/read").with(user(principal)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(notifications);
    }

    private static User principal(String role) {
        User principal = new User();
        principal.setId(UUID.randomUUID());
        principal.setUserType(role);
        principal.setEmail(role.toLowerCase() + "@example.test");
        principal.setStatus("ACTIVE");
        return principal;
    }

    private static NotificationResponse item() {
        return new NotificationResponse(UUID.randomUUID(), "RESULT_PUBLISHED", "Result available", "View your result",
                "/student/results", Instant.parse("2026-11-21T05:00:00Z"), false, null);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean NotificationService notificationService() { return mock(NotificationService.class); }
        @Bean NotificationController notificationController(NotificationService service) { return new NotificationController(service); }
        @Bean GlobalExceptionHandler exceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean AuthenticationProvider authenticationProvider() { return mock(AuthenticationProvider.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserDetailsService userDetailsService() { return mock(UserDetailsService.class); }
        @Bean JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwt, UserDetailsService users) {
            return new JwtAuthenticationFilter(jwt, users);
        }
    }
}
