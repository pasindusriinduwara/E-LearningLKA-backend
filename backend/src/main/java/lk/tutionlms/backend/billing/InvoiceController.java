package lk.tutionlms.backend.billing;

import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.StudentRepository;
import lk.tutionlms.backend.identity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceRepository invoiceRepository;
    private final StudentRepository studentRepository;

    @GetMapping("/my-invoices")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<StudentInvoice>> getMyInvoices(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<Student> studentOpt = studentRepository.findByUserId(user.getId());
        if (studentOpt.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        return ResponseEntity.ok(invoiceRepository.findByStudentIdAndDeletedFalse(studentOpt.get().getId()));
    }
}
