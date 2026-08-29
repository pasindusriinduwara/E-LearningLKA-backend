package lk.tutionlms.backend.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.*;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentInvoice extends BaseEntity {

    @Column(name = "invoice_number")
    private String invoiceNumber;

    private String month; 

    @Column(name = "batch_name")
    private String batch; 

    private String amount; 

    private String status; 
}
