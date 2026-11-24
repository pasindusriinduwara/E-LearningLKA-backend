package lk.tutionlms.backend.billing;

import lk.tutionlms.backend.identity.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    @GetMapping("/my-invoices")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Void> getMyInvoices(@AuthenticationPrincipal User user) {
        throw new ResponseStatusException(HttpStatus.GONE,
                "Payments are currently unavailable. Class access is managed by your teacher.");
    }
}
