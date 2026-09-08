package lk.tutionlms.backend.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateAnnouncementRequest(
        @NotNull(message = "Batch ID is required")
        UUID batchId,

        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title cannot exceed 255 characters")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = 2000, message = "Description cannot exceed 2000 characters")
        String description,

        @Size(max = 50, message = "Type cannot exceed 50 characters")
        String type,

        @Size(max = 100, message = "Time text cannot exceed 100 characters")
        String time
) {}
