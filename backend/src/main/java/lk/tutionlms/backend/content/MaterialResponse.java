package lk.tutionlms.backend.content;

import java.time.LocalDateTime;
import java.util.UUID;

public record MaterialResponse(
        UUID id,
        UUID batchId,
        String batchName,
        String teacherName,
        String title,
        String subject,
        String type,
        String time,
        String size,
        String fileUrl,
        LocalDateTime createdAt
) {}
