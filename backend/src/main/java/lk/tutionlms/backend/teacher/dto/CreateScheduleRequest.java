package lk.tutionlms.backend.teacher.dto;

import java.util.UUID;

public record CreateScheduleRequest(
                UUID batchId,
                String title,
                String date, // e.g. "2026-09-01"
                String startTime, // e.g. "04:30 PM"
                String endTime, // e.g. "06:30 PM"
                String location, // e.g. "Studio 2" or Zoom URL
                String mode, // "IN_PERSON", "ONLINE", "HYBRID"
                String repeat // "ONCE", "WEEKLY", "BI_WEEKLY"
) {
}
