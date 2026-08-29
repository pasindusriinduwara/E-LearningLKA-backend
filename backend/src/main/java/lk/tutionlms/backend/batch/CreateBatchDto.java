// src/main/java/lk/tutionlms/backend/batch/CreateBatchDto.java
package lk.tutionlms.backend.batch;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateBatchDto {
    private String name;
    private String examYear;
    private BigDecimal monthlyFee;
    private String deliveryMode; // ONLINE, IN_PERSON, HYBRID
    private UUID subjectId; 
}