
package lk.tutionlms.backend.batch;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateBatchDto {
    private String name;
    private String examYear;
    private BigDecimal monthlyFee;
    private String deliveryMode; 
    private UUID subjectId; 
}