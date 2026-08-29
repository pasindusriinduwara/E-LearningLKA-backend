package lk.tutionlms.backend.batch;

import java.math.BigDecimal;
import java.util.UUID;

public record AvailableBatchResponse(
        UUID id,
        String name,
        String subject,
        String teacher,
        String schedule,
        String status,
        String examYear,
        BigDecimal monthlyFee,
        String deliveryMode
) {
}