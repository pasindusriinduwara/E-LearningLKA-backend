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

    private String month; // "August 2026"

    @Column(name = "batch_name")
    private String batch; // "A/L Combined Mathematics"

    private String amount; // "LKR 3,500"

    private String status; // "Due 30 Aug" or "Paid 02 Aug"
}
