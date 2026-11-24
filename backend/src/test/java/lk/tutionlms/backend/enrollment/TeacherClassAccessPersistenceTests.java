package lk.tutionlms.backend.enrollment;

import jakarta.persistence.EntityManager;
import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.billing.StudentInvoice;
import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.Teacher;
import lk.tutionlms.backend.identity.User;
import lk.tutionlms.backend.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic rows only; all access decisions, invoices and notifications roll back. */
@SpringBootTest(properties = {"debug=false", "logging.level.root=ERROR", "spring.jpa.show-sql=false"})
@Transactional
class TeacherClassAccessPersistenceTests {
    @Autowired private EntityManager em;
    @Autowired private EnrollmentService service;
    @Autowired private EnrollmentRepository enrollments;
    @Autowired private NotificationService notifications;
    @Autowired private EnrollmentRequestRepository requests;

    @Test
    void teacherCanRestoreAndRevokeActualAccessWithoutPayingOrLosingPaymentHistory() {
        User teacher = account("TEACHER"), studentUser = account("STUDENT");
        Student student = student(studentUser); Batch batch = batch(teacher);
        EnrollmentRequest request = request(student, batch, EnrollmentStatus.REJECTED);
        Enrollment enrollment = enroll(student, batch, "DROPPED");
        StudentInvoice invoice = StudentInvoice.builder().studentId(student.getId()).invoiceNumber("TEST-" + UUID.randomUUID())
                .amount("3500").status("OVERDUE").month("November").batch(batch.getName()).build();
        invoice.setId(UUID.randomUUID());
        em.flush();
        em.unwrap(org.hibernate.Session.class).doWork(connection -> {
            // Legacy databases keep a required invoices.batch_id not mapped by StudentInvoice.
            boolean legacyBatchId;
            try (var columns = connection.getMetaData().getColumns(null, connection.getSchema(), "invoices", "batch_id")) {
                legacyBatchId = columns.next();
            }
            String sql = "INSERT INTO invoices (id, student_id, invoice_number, month, batch_name, amount, status, is_deleted, created_at"
                    + (legacyBatchId ? ", batch_id" : "") + ") VALUES (?, ?, ?, ?, ?, ?, ?, false, CURRENT_TIMESTAMP"
                    + (legacyBatchId ? ", ?" : "") + ")";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setObject(1, invoice.getId()); statement.setObject(2, student.getId());
                statement.setString(3, invoice.getInvoiceNumber()); statement.setString(4, invoice.getMonth());
                statement.setString(5, invoice.getBatch()); statement.setString(6, invoice.getAmount());
                statement.setString(7, invoice.getStatus());
                if (legacyBatchId) statement.setObject(8, batch.getId());
                statement.executeUpdate();
            }
        });

        assertFalse(enrollments.isStudentActiveInBatch(student.getId(), batch.getId()));
        assertTrue(service.setStudentClassAccess(teacher.getEmail(), request.getId(), true).accessGranted());
        em.flush();
        assertTrue(enrollments.isStudentActiveInBatch(student.getId(), batch.getId()));
        assertTrue(enrollments.findActiveBatchIdsByStudentId(student.getId()).contains(batch.getId()));
        assertEquals(enrollment.getId(), enrollments.findByStudentIdAndBatchIdAndDeletedFalse(student.getId(), batch.getId()).getFirst().getId());
        assertTrue(service.getRequestsForTeacher(teacher.getEmail()).getFirst().accessGranted());
        assertTrue(service.setStudentClassAccess(teacher.getEmail(), request.getId(), true).accessGranted());
        assertEquals(1, notifications.unreadCount(studentUser.getId()));

        service.setStudentClassAccess(teacher.getEmail(), request.getId(), false); em.flush();
        assertFalse(enrollments.isStudentActiveInBatch(student.getId(), batch.getId()));
        assertFalse(enrollments.findActiveBatchIdsByStudentId(student.getId()).contains(batch.getId()));
        assertFalse(service.getRequestsForTeacher(teacher.getEmail()).getFirst().accessGranted());
        assertEquals(EnrollmentStatus.REJECTED, service.getStudentStatuses(studentUser.getEmail()).getFirst().status());
        service.setStudentClassAccess(teacher.getEmail(), request.getId(), false);
        assertEquals(2, notifications.unreadCount(studentUser.getId()));
        assertEquals("OVERDUE", em.find(StudentInvoice.class, invoice.getId()).getStatus());
        assertEquals(new BigDecimal("3500"), batch.getMonthlyFee());
        assertEquals(1, enrollments.findByStudentIdAndBatchIdAndDeletedFalse(student.getId(), batch.getId()).size());
    }

    @Test
    void activeQueriesRejectInactiveBatchSuspendedAccountAndDeletedProfile() {
        User teacher = account("TEACHER"), user = account("STUDENT");
        Student student = student(user); Batch batch = batch(teacher);
        enroll(student, batch, "ACTIVE"); em.flush();
        assertTrue(enrollments.isStudentActiveInBatch(student.getId(), batch.getId()));
        batch.setActive(false); em.flush();
        assertFalse(enrollments.isStudentActiveInBatch(student.getId(), batch.getId()));
        assertTrue(enrollments.findActiveBatchIdsByStudentId(student.getId()).isEmpty());
        batch.setActive(true); user.setStatus("SUSPENDED"); em.flush();
        assertFalse(enrollments.isStudentActiveInBatch(student.getId(), batch.getId()));
        user.setStatus("ACTIVE"); student.setDeleted(true); em.flush();
        assertFalse(enrollments.isStudentActiveInBatch(student.getId(), batch.getId()));
    }

    @Test
    void requestQueriesIncludeEveryDecisionButOnlyTheOwnersNondeletedStudentsAndClasses() {
        User teacher = account("TEACHER"), other = account("TEACHER");
        Student student = student(account("STUDENT"));
        Batch pending = batch(teacher), approved = batch(teacher), denied = batch(teacher), foreign = batch(other);
        request(student, pending, EnrollmentStatus.PENDING);
        request(student, approved, EnrollmentStatus.APPROVED); enroll(student, approved, "ACTIVE");
        request(student, denied, EnrollmentStatus.REJECTED);
        request(student, foreign, EnrollmentStatus.PENDING);
        Batch deleted = batch(teacher); deleted.setDeleted(true); request(student, deleted, EnrollmentStatus.PENDING);
        Student removed = student(account("STUDENT")); removed.setDeleted(true); request(removed, pending, EnrollmentStatus.PENDING);
        em.flush();
        var all = service.getRequestsForTeacher(teacher.getEmail());
        assertEquals(3, all.size());
        assertEquals(1, all.stream().filter(TeacherEnrollmentRequestResponse::accessGranted).count());
        assertEquals(1, service.getPendingRequestsForTeacher(teacher.getEmail()).size());
        assertEquals(1, service.getRequestsForTeacher(other.getEmail()).size());
    }

    @Test
    void legacyStudentsCanBeDeniedAndRestoredUsingTheOriginalEnrollmentIdWithoutGetWrites() {
        User teacher = account("TEACHER"), user = account("STUDENT");
        Student student = student(user); Batch batch = batch(teacher);
        Enrollment legacy = enroll(student, batch, "ACTIVE");
        // Persist valid data on databases that enforce one enrollment per student/class.
        // Defensive handling of legacy duplicate rows is covered by TeacherEnrollmentReviewTests.
        em.flush();
        assertTrue(requests.findByStudentIdAndIsDeletedFalse(student.getId()).isEmpty());
        var before = service.getRequestsForTeacher(teacher.getEmail());
        assertEquals(1, before.size()); assertTrue(before.getFirst().accessGranted());
        assertTrue(requests.findByStudentIdAndIsDeletedFalse(student.getId()).isEmpty(), "GET must not create request rows");

        assertFalse(service.setStudentClassAccess(teacher.getEmail(), legacy.getId(), false).accessGranted()); em.flush();
        assertFalse(enrollments.isStudentActiveInBatch(student.getId(), batch.getId()));
        assertEquals("DROPPED", legacy.getStatus());
        assertEquals(1, requests.findByStudentIdAndIsDeletedFalse(student.getId()).size());
        assertEquals(1, notifications.unreadCount(user.getId()));
        service.setStudentClassAccess(teacher.getEmail(), legacy.getId(), false);
        assertEquals(1, notifications.unreadCount(user.getId()));

        assertTrue(service.setStudentClassAccess(teacher.getEmail(), legacy.getId(), true).accessGranted()); em.flush();
        assertTrue(enrollments.isStudentActiveInBatch(student.getId(), batch.getId()));
        assertEquals(1, enrollments.findByStudentIdAndBatchIdAndDeletedFalse(student.getId(), batch.getId()).stream()
                .filter(enrollment -> "ACTIVE".equals(enrollment.getStatus())).count());
        var after = service.getRequestsForTeacher(teacher.getEmail());
        assertEquals(1, after.size()); assertTrue(after.getFirst().accessGranted());
        assertNotEquals(legacy.getId(), after.getFirst().id(), "New lists use the canonical request identity");
        assertEquals(1, requests.findByStudentIdAndIsDeletedFalse(student.getId()).size());
        assertEquals(2, notifications.unreadCount(user.getId()));
    }

    private User account(String role) {
        User user = User.builder().email("access-test-" + UUID.randomUUID() + "@example.test")
                .password("synthetic-unused-password-hash").userType(role).status("ACTIVE").build();
        em.persist(user); return user;
    }

    private Student student(User user) {
        Student student = Student.builder().userId(user.getId()).name("Synthetic access student")
                .studentId("AT-" + UUID.randomUUID().toString().substring(0, 8)).build();
        em.persist(student); return student;
    }

    private EnrollmentRequest request(Student student, Batch batch, EnrollmentStatus status) {
        EnrollmentRequest request = new EnrollmentRequest(); request.setStudentId(student.getId());
        request.setBatchId(batch.getId()); request.setStatus(status); em.persist(request); return request;
    }

    private Enrollment enroll(Student student, Batch batch, String status) {
        Enrollment enrollment = Enrollment.builder().studentId(student.getId()).batchId(batch.getId())
                .status(status).enrolledDate(LocalDate.now()).build();
        em.persist(enrollment); return enrollment;
    }

    private Batch batch(User teacherUser) {
        Teacher teacher = Teacher.builder().userId(teacherUser.getId()).name("Synthetic access teacher").build();
        var existing = em.createQuery("SELECT t FROM Teacher t WHERE t.userId = :id", Teacher.class)
                .setParameter("id", teacherUser.getId()).getResultStream().findFirst();
        if (existing.isPresent()) teacher = existing.get(); else em.persist(teacher);
        em.flush();
        UUID subjectId = UUID.randomUUID();
        em.unwrap(org.hibernate.Session.class).doWork(connection -> {
            boolean legacyCode;
            try (var columns = connection.getMetaData().getColumns(null, connection.getSchema(), "subjects", "code")) {
                legacyCode = columns.next();
            }
            String sql = legacyCode
                    ? "INSERT INTO subjects (id, name, active, is_deleted, created_at, code) VALUES (?, ?, true, false, CURRENT_TIMESTAMP, ?)"
                    : "INSERT INTO subjects (id, name, active, is_deleted, created_at) VALUES (?, ?, true, false, CURRENT_TIMESTAMP)";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setObject(1, subjectId); statement.setString(2, "Access test " + subjectId);
                if (legacyCode) statement.setString(3, "AT" + subjectId.toString().substring(0, 8));
                statement.executeUpdate();
            }
        });
        Batch batch = Batch.builder().teacherId(teacher.getId()).subjectId(subjectId).name("Synthetic access class")
                .examYear("2026").monthlyFee(new BigDecimal("3500")).deliveryMode("ONLINE").active(true).build();
        em.persist(batch); return batch;
    }
}
