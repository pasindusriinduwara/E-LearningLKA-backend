package lk.tutionlms.backend.teacher.dto;

import java.util.UUID;

public record MaterialUploadResponse(
        UUID id,
        UUID batchId,
        String title,
        String type,
        String fileUrl
) {}
