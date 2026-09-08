package lk.tutionlms.backend.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateMaterialRequest(
        @NotNull(message = "Batch ID is required")
        UUID batchId,

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title cannot exceed 255 characters")
        String title,

        @Size(max = 100, message = "Subject cannot exceed 100 characters")
        String subject,

        @NotBlank(message = "Resource type is required")
        @Size(max = 50, message = "Type cannot exceed 50 characters")
        String type,

        @Size(max = 100, message = "Time text cannot exceed 100 characters")
        String time,

        @Size(max = 50, message = "Size text cannot exceed 50 characters")
        String size,

        @Size(max = 1000, message = "File URL cannot exceed 1000 characters")
        String fileUrl
) {}
