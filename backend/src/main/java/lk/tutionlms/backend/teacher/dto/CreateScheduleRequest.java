package lk.tutionlms.backend.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateScheduleRequest(
        @NotNull(message = "Batch ID is required") UUID batchId,

        @NotBlank(message = "Title is required") String title,

        @NotBlank(message = "Date is required") String date,

        @NotBlank(message = "Start time is required") String startTime,

        @NotBlank(message = "End time is required") String endTime,

        String location,
        String mode,
        String repeat) {
}
