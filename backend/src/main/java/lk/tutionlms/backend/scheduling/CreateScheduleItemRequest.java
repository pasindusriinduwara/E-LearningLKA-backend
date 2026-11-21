package lk.tutionlms.backend.scheduling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Creation input deliberately excludes persistence identity and audit fields. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateScheduleItemRequest(
        @NotNull UUID batchId,
        @NotNull DayOfWeek dayOfWeek,
        LocalDate effectiveDate,
        @NotBlank String title,
        String subject,
        String teacher,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        String time,
        String location,
        @NotNull DeliveryMode mode,
        RecurrenceType recurrence,
        String accent) {
}
