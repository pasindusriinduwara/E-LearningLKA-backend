package lk.tutionlms.backend.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpdateScheduleRequest(
        @NotNull(message = "Batch ID cannot be null")
        UUID batchId,

        @NotBlank(message = "Title cannot be blank")
        String title,

        @NotBlank(message = "Date is required (yyyy-MM-dd)")
        String date,

        @NotBlank(message = "Start time is required (HH:mm)")
        String startTime,

        @NotBlank(message = "End time is required (HH:mm)")
        String endTime,

        String location,
        String mode,
        String repeat
) {}
