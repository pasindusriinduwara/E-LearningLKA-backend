package lk.tutionlms.backend.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lk.tutionlms.backend.common.BaseEntity;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "invoices", indexes = {
        @Index(name = "idx_invoices_student_id", columnList = "student_id"),
        @Index(name = "idx_invoices_invoice_number", columnList = "invoice_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentInvoice extends BaseEntity {

    @Column(name = "student_id")
    private UUID studentId;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    private String month; 

    @Column(name = "batch_name")
    private String batch; 

    private String amount; 

    private String status; 
}
