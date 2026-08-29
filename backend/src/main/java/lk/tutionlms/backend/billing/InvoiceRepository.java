package lk.tutionlms.backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<StudentInvoice, UUID> {
    List<StudentInvoice> findByDeletedFalse();
}
