package lk.tutionlms.backend.enrollment;

import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.batch.BatchRepository;
import lk.tutionlms.backend.billing.InvoiceController;
import lk.tutionlms.backend.common.GlobalExceptionHandler;
import lk.tutionlms.backend.config.SecurityConfig;
import lk.tutionlms.backend.identity.*;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real controller, service, URL security and method security; persistence and notifications are mocked. */
@SpringJUnitWebConfig(TeacherEnrollmentReviewTests.TestConfig.class)
class TeacherEnrollmentReviewTests {
    @Autowired private WebApplicationContext context;
    @Autowired private EnrollmentRequestRepository requests;
    @Autowired private EnrollmentRepository enrollments;
    @Autowired private UserRepository users;
    @Autowired private StudentRepository students;
    @Autowired private TeacherRepository teachers;
    @Autowired private BatchRepository batches;
    @Autowired private NotificationService notifications;
    private MockMvc mvc;
    private User teacherUser;
    private Teacher teacher;

    @BeforeEach
    void setup() {
        reset(requests, enrollments, users, students, teachers, batches, notifications);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        teacherUser = principal("TEACHER", "teacher@example.test");
        teacher = teacher(teacherUser);
    }

    @Test
    void pendingRequestsContainReviewDetailsAndAreScopedToTheAuthenticatedTeacher() throws Exception {
        UUID batchId = UUID.randomUUID(), studentId = UUID.randomUUID(), requestId = UUID.randomUUID();
        when(requests.findPendingRequestsByTeacherId(teacher.getId())).thenReturn(List.of(
                new TeacherEnrollmentRequestResponse(requestId, studentId, batchId, EnrollmentStatus.PENDING,
                        LocalDateTime.of(2026, 11, 21, 9, 30), "Kamal Perera", "ST-123", "A/L Physics", "Physics")));

        mvc.perform(get("/api/v1/enrollments/pending").with(user(teacherUser))
                        .param("teacherId", UUID.randomUUID().toString())
                        .param("studentId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(requestId.toString()))
                .andExpect(jsonPath("$[0].studentId").value(studentId.toString()))
                .andExpect(jsonPath("$[0].batchId").value(batchId.toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].studentName").value("Kamal Perera"))
                .andExpect(jsonPath("$[0].studentNumber").value("ST-123"))
                .andExpect(jsonPath("$[0].batchName").value("A/L Physics"))
                .andExpect(jsonPath("$[0].subject").value("Physics"))
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].updatedAt").doesNotExist())
                .andExpect(jsonPath("$[0].deleted").doesNotExist());

        verify(requests).findPendingRequestsByTeacherId(teacher.getId());
        verifyNoMoreInteractions(requests);
        verify(enrollments).findActiveBatchIdsByStudentId(studentId);
        verifyNoInteractions(students, batches, notifications);
    }

    @Test
    void aDifferentTeacherGetsTheirOwnEmptyQueueRatherThanAnotherTeachersRequests() throws Exception {
        User otherUser = principal("TEACHER", "other@example.test");
        Teacher otherTeacher = teacher(otherUser);
        when(requests.findPendingRequestsByTeacherId(otherTeacher.getId())).thenReturn(List.of());

        mvc.perform(get("/api/v1/enrollments/pending").with(user(otherUser))
                        .param("teacherId", teacher.getId().toString()))
                .andExpect(status().isOk()).andExpect(content().json("[]"));

        verify(requests).findPendingRequestsByTeacherId(otherTeacher.getId());
        verify(requests, never()).findPendingRequestsByTeacherId(teacher.getId());
    }

    @Test
    void studentsCannotReadOrDecideTeacherEnrollmentRequests() throws Exception {
        User student = principal("STUDENT", "student@example.test");
        UUID requestId = UUID.randomUUID();
        mvc.perform(get("/api/v1/enrollments/pending").with(user(student)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + requestId + "/approve").with(user(student)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + requestId + "/reject").with(user(student)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(requests, users, teachers, students, batches, enrollments, notifications);
    }

    @Test
    void teachersCannotUseStudentEnrollmentActions() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/v1/enrollments/request").with(user(teacherUser))
                        .contentType("application/json").content("{\"batchId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(requests, users, teachers, students, batches, enrollments, notifications);
    }

    @Test
    void anonymousRequestsCannotReadPendingEnrollments() throws Exception {
        mvc.perform(get("/api/v1/enrollments/pending")).andExpect(status().isForbidden());
        verifyNoInteractions(requests, users, teachers, students, batches, enrollments, notifications);
    }

    @Test
    void anotherTeachersRequestCannotBeApprovedOrRejected() throws Exception {
        EnrollmentRequest request = request(EnrollmentStatus.PENDING, UUID.randomUUID());
        for (String decision : List.of("approve", "reject")) {
            mvc.perform(put("/api/v1/enrollments/teacher/requests/" + request.getId() + "/" + decision)
                            .with(user(teacherUser)))
                    .andExpect(status().isForbidden());
        }
        assertEquals(EnrollmentStatus.PENDING, request.getStatus());
        verify(requests, never()).save(any());
        verifyNoInteractions(enrollments, notifications);
    }

    @Test
    void aDecisionAlreadyMadeInAnotherTabReturnsConflictWithoutChangingIt() throws Exception {
        EnrollmentRequest request = request(EnrollmentStatus.APPROVED, teacher.getId());
        for (String decision : List.of("approve", "reject")) {
            mvc.perform(put("/api/v1/enrollments/teacher/requests/" + request.getId() + "/" + decision)
                            .with(user(teacherUser)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Only pending requests can be "
                            + (decision.equals("approve") ? "approved" : "rejected")));
        }
        assertEquals(EnrollmentStatus.APPROVED, request.getStatus());
        verify(requests, times(2)).findByIdForUpdate(request.getId());
        verify(requests, never()).save(any());
        verifyNoInteractions(enrollments, notifications);
    }

    @Test
    void approvingAnOwnedRequestEnrollsTheStudentAndSendsTheExistingNotification() throws Exception {
        EnrollmentRequest request = request(EnrollmentStatus.PENDING, teacher.getId());
        Student student = students.findById(request.getStudentId()).orElseThrow();

        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + request.getId() + "/approve")
                        .with(user(teacherUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Enrollment approved"));

        assertEquals(EnrollmentStatus.APPROVED, request.getStatus());
        verify(requests).findByIdForUpdate(request.getId());
        verify(requests).save(request);
        verify(enrollments).save(argThat(enrollment -> enrollment.getStudentId().equals(student.getId())
                && enrollment.getBatchId().equals(request.getBatchId()) && enrollment.getStatus().equals("ACTIVE")));
        verify(notifications).notifyUser(eq(student.getUserId()), eq("ENROLLMENT_APPROVED"),
                anyString(), anyString(), eq("/classes/" + request.getBatchId()));
    }

    @Test
    void deletedBatchesCannotReceiveEnrollmentDecisions() throws Exception {
        EnrollmentRequest request = request(EnrollmentStatus.PENDING, teacher.getId());
        batches.findById(request.getBatchId()).orElseThrow().setDeleted(true);

        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + request.getId() + "/approve")
                        .with(user(teacherUser)))
                .andExpect(status().isNotFound());

        assertEquals(EnrollmentStatus.PENDING, request.getStatus());
        verify(requests, never()).save(any());
        verifyNoInteractions(enrollments, notifications);
    }

    @Test
    void aDeletedTeacherAccountCannotReadPendingRequests() throws Exception {
        teacherUser.setDeleted(true);
        mvc.perform(get("/api/v1/enrollments/pending").with(user(teacherUser)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(requests);
    }

    @Test
    void allRequestsIncludePendingApprovedAndDeniedButUseActualEnrollmentForAccess() throws Exception {
        UUID studentId = UUID.randomUUID(), pendingBatch = UUID.randomUUID(), approvedBatch = UUID.randomUUID(), deniedBatch = UUID.randomUUID();
        when(requests.findRequestsByTeacherId(teacher.getId())).thenReturn(List.of(
                new TeacherEnrollmentRequestResponse(UUID.randomUUID(), studentId, pendingBatch, EnrollmentStatus.PENDING,
                        LocalDateTime.now(), "Student", "ST-1", "Pending class", "Physics"),
                new TeacherEnrollmentRequestResponse(UUID.randomUUID(), studentId, approvedBatch, EnrollmentStatus.APPROVED,
                        LocalDateTime.now(), "Student", "ST-1", "Allowed class", "Physics"),
                new TeacherEnrollmentRequestResponse(UUID.randomUUID(), studentId, deniedBatch, EnrollmentStatus.APPROVED,
                        LocalDateTime.now(), "Student", "ST-1", "Stale approved class", "Physics")));
        when(enrollments.findActiveBatchIdsByStudentId(studentId)).thenReturn(List.of(approvedBatch));
        mvc.perform(get("/api/v1/enrollments/teacher/requests").with(user(teacherUser))
                        .param("teacherId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].accessGranted").value(false))
                .andExpect(jsonPath("$[1].accessGranted").value(true))
                .andExpect(jsonPath("$[2].accessGranted").value(false))
                .andExpect(jsonPath("$[1].batchActive").value(true));
        verify(requests).findRequestsByTeacherId(teacher.getId());
        verify(enrollments, times(1)).findActiveBatchIdsByStudentId(studentId);
    }

    @Test
    void studentAndAnonymousCannotReadOrChangeTeacherAccessDecisions() throws Exception {
        User student = principal("STUDENT", "student@example.test");
        mvc.perform(get("/api/v1/enrollments/teacher/requests").with(user(student))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/enrollments/teacher/requests")).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + UUID.randomUUID() + "/access")
                        .with(user(student)).contentType("application/json").content("{\"allowed\":true}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(requests, enrollments, notifications);
    }

    @Test
    void anotherTeacherCannotAllowOrDenyAccess() throws Exception {
        EnrollmentRequest request = request(EnrollmentStatus.APPROVED, UUID.randomUUID());
        for (boolean allowed : List.of(true, false)) {
            mvc.perform(put("/api/v1/enrollments/teacher/requests/" + request.getId() + "/access")
                            .with(user(teacherUser)).contentType("application/json").content("{\"allowed\":" + allowed + "}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(enrollments, notifications);
        verify(requests, never()).save(any());
    }

    @Test
    void allowingAccessReactivatesExistingEnrollmentWithoutCreatingAnotherRow() throws Exception {
        EnrollmentRequest request = request(EnrollmentStatus.REJECTED, teacher.getId());
        Enrollment enrollment = enrollment(request, "DROPPED");
        when(enrollments.findByStudentAndBatchForUpdate(request.getStudentId(), request.getBatchId()))
                .thenReturn(List.of(enrollment));
        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + request.getId() + "/access")
                        .with(user(teacherUser)).contentType("application/json").content("{\"allowed\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessGranted").value(true))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        assertEquals("ACTIVE", enrollment.getStatus());
        assertEquals(EnrollmentStatus.APPROVED, request.getStatus());
        verify(enrollments).save(enrollment);
        verify(enrollments, times(1)).save(any());
        verify(notifications).notifyUser(any(), eq("ACCESS_GRANTED"), anyString(), anyString(),
                eq("/classes/" + request.getBatchId()));
        var order = inOrder(students, requests, enrollments);
        order.verify(students).findByIdForUpdate(request.getStudentId());
        order.verify(requests).findByIdForUpdate(request.getId());
        order.verify(enrollments).findByStudentAndBatchForUpdate(request.getStudentId(), request.getBatchId());
    }

    @Test
    void denyingAccessDropsEveryDuplicateEnrollmentAndSynchronizesLegacyRequests() throws Exception {
        EnrollmentRequest request = request(EnrollmentStatus.APPROVED, teacher.getId());
        Enrollment first = enrollment(request, "ACTIVE"), second = enrollment(request, "ACTIVE");
        when(enrollments.findByStudentAndBatchForUpdate(request.getStudentId(), request.getBatchId()))
                .thenReturn(List.of(first, second));
        EnrollmentRequest duplicate = new EnrollmentRequest();
        duplicate.setId(UUID.randomUUID()); duplicate.setStudentId(request.getStudentId());
        duplicate.setBatchId(request.getBatchId()); duplicate.setStatus(EnrollmentStatus.APPROVED);
        when(requests.findByStudentIdAndIsDeletedFalse(request.getStudentId())).thenReturn(List.of(request, duplicate));
        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + request.getId() + "/access")
                        .with(user(teacherUser)).contentType("application/json").content("{\"allowed\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessGranted").value(false))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertEquals("DROPPED", first.getStatus()); assertEquals("DROPPED", second.getStatus());
        assertEquals(EnrollmentStatus.REJECTED, duplicate.getStatus());
        verify(enrollments).save(first); verify(enrollments).save(second);
        verify(requests).save(duplicate);
        verify(notifications).notifyUser(any(), eq("ACCESS_REVOKED"), anyString(), anyString(),
                eq("/enrollment/" + request.getBatchId()));
    }

    @Test
    void repeatedAllowAndDenyAreIdempotentAndDoNotRepeatNotifications() throws Exception {
        EnrollmentRequest request = request(EnrollmentStatus.REJECTED, teacher.getId());
        Enrollment enrollment = enrollment(request, "DROPPED");
        when(enrollments.findByStudentAndBatchForUpdate(request.getStudentId(), request.getBatchId()))
                .thenReturn(List.of(enrollment));
        for (boolean allowed : List.of(true, true, false, false)) {
            mvc.perform(put("/api/v1/enrollments/teacher/requests/" + request.getId() + "/access")
                            .with(user(teacherUser)).contentType("application/json").content("{\"allowed\":" + allowed + "}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.accessGranted").value(allowed));
        }
        verify(notifications, times(1)).notifyUser(any(), eq("ACCESS_GRANTED"), anyString(), anyString(), anyString());
        verify(notifications, times(1)).notifyUser(any(), eq("ACCESS_REVOKED"), anyString(), anyString(), anyString());
        verify(enrollments, times(2)).save(enrollment);
        verify(requests, times(2)).save(request);
    }

    @Test
    void anExplicitAllowedValueIsRequired() throws Exception {
        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + UUID.randomUUID() + "/access")
                        .with(user(teacherUser)).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("allowed must be true or false"));
        verifyNoInteractions(requests, enrollments, notifications);
    }

    @Test
    void inactiveClassesCannotGrantAccessButCanStillRevokeIt() throws Exception {
        EnrollmentRequest request = request(EnrollmentStatus.APPROVED, teacher.getId());
        batches.findById(request.getBatchId()).orElseThrow().setActive(false);
        Enrollment enrollment = enrollment(request, "ACTIVE");
        when(enrollments.findByStudentAndBatchForUpdate(request.getStudentId(), request.getBatchId()))
                .thenReturn(List.of(enrollment));
        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + request.getId() + "/access")
                        .with(user(teacherUser)).contentType("application/json").content("{\"allowed\":true}"))
                .andExpect(status().isConflict());
        assertEquals("ACTIVE", enrollment.getStatus());
        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + request.getId() + "/access")
                        .with(user(teacherUser)).contentType("application/json").content("{\"allowed\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessGranted").value(false));
        assertEquals("DROPPED", enrollment.getStatus());
    }

    @Test
    void suspendedStudentAccountsCannotBeGrantedAccess() throws Exception {
        EnrollmentRequest request = request(EnrollmentStatus.PENDING, teacher.getId());
        Student student = students.findById(request.getStudentId()).orElseThrow();
        users.findById(student.getUserId()).orElseThrow().setStatus("SUSPENDED");
        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + request.getId() + "/access")
                        .with(user(teacherUser)).contentType("application/json").content("{\"allowed\":true}"))
                .andExpect(status().isConflict());
        verifyNoInteractions(enrollments, notifications);
        verify(requests, never()).save(any());
    }

    @Test
    void pendingApprovalAlsoRestoresInactiveEnrollmentAndRejectionRemovesUnexpectedActiveAccess() throws Exception {
        EnrollmentRequest request = request(EnrollmentStatus.PENDING, teacher.getId());
        Enrollment enrollment = enrollment(request, "DROPPED");
        when(enrollments.findByStudentAndBatchForUpdate(request.getStudentId(), request.getBatchId()))
                .thenReturn(List.of(enrollment));
        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + request.getId() + "/approve").with(user(teacherUser)))
                .andExpect(status().isOk());
        assertEquals("ACTIVE", enrollment.getStatus());
        request.setStatus(EnrollmentStatus.PENDING);
        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + request.getId() + "/reject").with(user(teacherUser)))
                .andExpect(status().isOk());
        assertEquals("DROPPED", enrollment.getStatus());
    }

    @Test
    void invoiceEndpointIsUnavailableWithoutReadingPaymentHistory() throws Exception {
        mvc.perform(get("/api/v1/invoices/my-invoices").with(user(principal("STUDENT", "student@example.test"))))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.message").value("Payments are currently unavailable. Class access is managed by your teacher."));
        verifyNoInteractions(requests, enrollments, students, notifications);
    }

    @Test
    void legacyActiveStudentsAreListedWithoutWritingAndDuplicateEnrollmentRowsAreCollapsed() throws Exception {
        UUID studentId = UUID.randomUUID(), batchId = UUID.randomUUID(), enrollmentId = UUID.randomUUID();
        TeacherEnrollmentRequestResponse legacy = new TeacherEnrollmentRequestResponse(enrollmentId, studentId,
                batchId, EnrollmentStatus.APPROVED, LocalDateTime.now(), "Legacy student", "ST-OLD", "Physics", "Physics");
        TeacherEnrollmentRequestResponse duplicate = new TeacherEnrollmentRequestResponse(UUID.randomUUID(), studentId,
                batchId, EnrollmentStatus.APPROVED, LocalDateTime.now(), "Legacy student", "ST-OLD", "Physics", "Physics");
        when(enrollments.findLegacyRequestsByTeacherId(teacher.getId())).thenReturn(List.of(legacy, duplicate));
        when(enrollments.findActiveBatchIdsByStudentId(studentId)).thenReturn(List.of(batchId));
        mvc.perform(get("/api/v1/enrollments/teacher/requests").with(user(teacherUser)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(enrollmentId.toString()))
                .andExpect(jsonPath("$[0].accessGranted").value(true));
        verify(requests, never()).save(any()); verify(enrollments, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void anotherTeacherCannotUseALegacyEnrollmentIdToChangeAccess() throws Exception {
        EnrollmentRequest request = request(EnrollmentStatus.PENDING, UUID.randomUUID());
        UUID legacyId = UUID.randomUUID();
        when(enrollments.findIdentityById(legacyId)).thenReturn(Optional.of(
                new EnrollmentIdentity(legacyId, request.getStudentId(), request.getBatchId())));
        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + legacyId + "/access")
                        .with(user(teacherUser)).contentType("application/json").content("{\"allowed\":false}"))
                .andExpect(status().isForbidden());
        verify(students, never()).findByIdForUpdate(any());
        verify(requests, never()).save(any()); verify(enrollments, never()).save(any());
        verifyNoInteractions(notifications);
    }

    @Test
    void deletedOrMissingLegacyEnrollmentIdsCannotCreateAnAccessRequest() throws Exception {
        mvc.perform(put("/api/v1/enrollments/teacher/requests/" + UUID.randomUUID() + "/access")
                        .with(user(teacherUser)).contentType("application/json").content("{\"allowed\":true}"))
                .andExpect(status().isNotFound());
        verify(requests, never()).save(any()); verify(enrollments, never()).save(any());
        verify(students, never()).findByIdForUpdate(any());
    }

    private Enrollment enrollment(EnrollmentRequest request, String status) {
        Enrollment enrollment = Enrollment.builder().studentId(request.getStudentId()).batchId(request.getBatchId()).status(status).build();
        enrollment.setId(UUID.randomUUID());
        return enrollment;
    }

    private Teacher teacher(User user) {
        Teacher profile = new Teacher();
        profile.setId(UUID.randomUUID());
        profile.setUserId(user.getId());
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(teachers.findByUserId(user.getId())).thenReturn(Optional.of(profile));
        return profile;
    }

    private EnrollmentRequest request(EnrollmentStatus status, UUID ownerId) {
        Batch batch = new Batch();
        batch.setId(UUID.randomUUID());
        batch.setTeacherId(ownerId);
        batch.setActive(true);
        when(batches.findById(batch.getId())).thenReturn(Optional.of(batch));
        EnrollmentRequest request = new EnrollmentRequest();
        request.setId(UUID.randomUUID());
        request.setStudentId(UUID.randomUUID());
        request.setBatchId(batch.getId());
        request.setStatus(status);
        when(requests.findByIdForUpdate(request.getId())).thenReturn(Optional.of(request));
        when(requests.findIdentityById(request.getId())).thenReturn(Optional.of(
                new EnrollmentRequestIdentity(request.getId(), request.getStudentId(), request.getBatchId())));
        User studentUser = principal("STUDENT", "student-" + request.getStudentId() + "@example.test");
        Student student = new Student();
        student.setId(request.getStudentId()); student.setUserId(studentUser.getId());
        when(students.findById(student.getId())).thenReturn(Optional.of(student));
        when(students.findByIdForUpdate(student.getId())).thenReturn(Optional.of(student));
        when(users.findById(studentUser.getId())).thenReturn(Optional.of(studentUser));
        return request;
    }

    private static User principal(String role, String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUserType(role);
        user.setEmail(email);
        return user;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean EnrollmentRequestRepository requests() { return mock(EnrollmentRequestRepository.class); }
        @Bean EnrollmentRepository enrollments() { return mock(EnrollmentRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean StudentRepository students() { return mock(StudentRepository.class); }
        @Bean BatchRepository batches() { return mock(BatchRepository.class); }
        @Bean TeacherRepository teachers() { return mock(TeacherRepository.class); }
        @Bean NotificationService notifications() { return mock(NotificationService.class); }
        @Bean EnrollmentService enrollmentService(EnrollmentRequestRepository requests, EnrollmentRepository enrollments,
                UserRepository users, StudentRepository students, BatchRepository batches, TeacherRepository teachers,
                NotificationService notifications) {
            return new EnrollmentService(requests, enrollments, users, students, batches, teachers, notifications);
        }
        @Bean EnrollmentController controller(EnrollmentService service) { return new EnrollmentController(service); }
        @Bean InvoiceController invoiceController() { return new InvoiceController(); }
        @Bean GlobalExceptionHandler exceptionHandler() { return new GlobalExceptionHandler(); }
        @Bean AuthenticationProvider authenticationProvider() { return mock(AuthenticationProvider.class); }
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserDetailsService userDetailsService() { return mock(UserDetailsService.class); }
        @Bean JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwt, UserDetailsService users) {
            return new JwtAuthenticationFilter(jwt, users);
        }
    }
}
