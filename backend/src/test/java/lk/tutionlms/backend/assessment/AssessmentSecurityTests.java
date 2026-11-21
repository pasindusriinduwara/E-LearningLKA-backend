package lk.tutionlms.backend.assessment;

import lk.tutionlms.backend.identity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"debug=false", "logging.level.root=ERROR", "spring.jpa.show-sql=false"})
class AssessmentSecurityTests {
    @Autowired private WebApplicationContext context;
    @MockitoBean private AssessmentService service;
    private MockMvc mvc;

    private User principal(String role) {
        User user = new User(); user.setId(UUID.randomUUID()); user.setUserType(role); return user;
    }

    @BeforeEach
    void setup() { mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test
    void studentCannotReadAnswerBearingManagementDetailOrList() throws Exception {
        User student = principal("STUDENT");
        mvc.perform(get("/api/v1/assessments/" + UUID.randomUUID()).with(user(student)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/assessments").with(user(student))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void teacherOwnershipDenialRemainsForbidden() throws Exception {
        when(service.getTeacherAssessment(any(), any())).thenThrow(new AccessDeniedException("Other teacher's batch"));
        mvc.perform(get("/api/v1/assessments/" + UUID.randomUUID()).with(user(principal("TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void studentUsesAnswerUploadWhileTeacherPaperUploadStaysRestricted() throws Exception {
        User student = principal("STUDENT");
        UUID id = UUID.randomUUID();
        when(service.uploadAnswerFile(eq(id), any(), any())).thenReturn(Map.of("url", "https://example.test/answer.pdf", "fileName", "answer.pdf"));
        MockMultipartFile file = new MockMultipartFile("file", "answer.pdf", "application/pdf", new byte[]{1});
        mvc.perform(multipart("/api/v1/assessments/" + id + "/upload-answer").file(file).with(user(student)))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/v1/assessments/upload-paper").file(file).with(user(student)))
                .andExpect(status().isForbidden());
        verify(service).uploadAnswerFile(eq(id), any(), any());
        verify(service, never()).uploadPaperFile(any(), any());
    }
}
