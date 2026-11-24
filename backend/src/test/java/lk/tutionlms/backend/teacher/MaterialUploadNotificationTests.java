package lk.tutionlms.backend.teacher;

import com.cloudinary.Cloudinary;
import lk.tutionlms.backend.content.LearningMaterial;
import lk.tutionlms.backend.content.MaterialRepository;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MaterialUploadNotificationTests {
    private final Cloudinary cloudinary = mock(Cloudinary.class, RETURNS_DEEP_STUBS);
    private final MaterialRepository materials = mock(MaterialRepository.class);
    private final TeacherService teachers = mock(TeacherService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final MaterialUploadService service = new MaterialUploadService(cloudinary, materials, teachers, notifications);
    private final User user = new User();
    private final UUID batchId = UUID.randomUUID();
    private final MockMultipartFile pdf = new MockMultipartFile("file", "notes.pdf", "application/pdf", new byte[]{1, 2});

    @Test
    void successfulUploadNotifiesTheClassAfterSavingTheMaterial() throws Exception {
        when(cloudinary.uploader().upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/test/material.pdf", "public_id", "test/material"));
        when(materials.save(any(LearningMaterial.class))).thenAnswer(call -> {
            LearningMaterial material = call.getArgument(0); material.setId(UUID.randomUUID()); return material;
        });
        var response = service.upload(user, batchId, "Lesson notes", "Physics", pdf);
        assertNotNull(response.id());
        verify(teachers).verifyBatchOwnership(user, batchId);
        var order = inOrder(materials, notifications);
        order.verify(materials).save(any(LearningMaterial.class));
        order.verify(notifications).notifyBatchStudents(eq(batchId), eq("MATERIAL_ADDED"),
                eq("New learning material"), eq("Lesson notes was added to your class."), eq("/materials"));
    }

    @Test
    void uploadFailureDoesNotCreateANotificationForMissingMaterial() throws Exception {
        when(cloudinary.uploader().upload(any(byte[].class), anyMap())).thenThrow(new RuntimeException("Provider failure"));
        assertThrows(ResponseStatusException.class, () -> service.upload(user, batchId, "Lesson notes", "Physics", pdf));
        verifyNoInteractions(materials, notifications);
    }

    @Test
    void unauthorizedBatchUploadCannotProduceNotifications() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your class"))
                .when(teachers).verifyBatchOwnership(user, batchId);
        assertThrows(ResponseStatusException.class, () -> service.upload(user, batchId, "Lesson notes", "Physics", pdf));
        verifyNoInteractions(cloudinary, materials, notifications);
    }
}
