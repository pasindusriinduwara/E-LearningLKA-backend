package lk.tutionlms.backend.billing;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceRepository invoiceRepository;

    @GetMapping("/my-invoices")
    public ResponseEntity<List<StudentInvoice>> getMyInvoices() {
        return ResponseEntity.ok(invoiceRepository.findByDeletedFalse());
    }
}
