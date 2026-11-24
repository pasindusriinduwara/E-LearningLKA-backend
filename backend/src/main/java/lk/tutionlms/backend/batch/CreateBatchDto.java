
package lk.tutionlms.backend.batch;

import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateBatchDto {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max=255)
    private String name;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Pattern(regexp="[0-9]{4}", message="Exam year must contain four digits")
    private String examYear;
    /** Legacy client field; ignored while billing is paused. */
    private BigDecimal monthlyFee;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Pattern(regexp="ONLINE|IN_PERSON|HYBRID")
    private String deliveryMode;
    @jakarta.validation.constraints.NotNull
    private UUID subjectId; 
}
