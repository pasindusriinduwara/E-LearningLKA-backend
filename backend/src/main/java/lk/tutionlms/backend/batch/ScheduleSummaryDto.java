package lk.tutionlms.backend.batch;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

public record ScheduleSummaryDto(
        UUID id,
        DayOfWeek dayOfWeek,
        String timeText,
        LocalTime startTime,
        LocalTime endTime,
        String location,
        String mode
) {
}
