package lk.tutionlms.backend.notification;

import jakarta.persistence.EntityManager;
import lk.tutionlms.backend.batch.Batch;
import lk.tutionlms.backend.enrollment.Enrollment;
import lk.tutionlms.backend.identity.Student;
import lk.tutionlms.backend.identity.Teacher;
import lk.tutionlms.backend.identity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Synthetic fixtures only; every test and its notifications roll back together. */
@SpringBootTest(properties = {"debug=false", "logging.level.root=ERROR", "spring.jpa.show-sql=false"})
@Transactional
class NotificationPersistenceTests {
    @Autowired private EntityManager em;
    @Autowired private NotificationService notifications;

    private User account(String role) {
        User user = User.builder().email("notification-test-" + UUID.randomUUID() + "@example.test")
                .password("synthetic-unused-password-hash").userType(role).status("ACTIVE").build();
        em.persist(user);
        return user;
    }

    private Student student(User user) {
        Student student = Student.builder().userId(user.getId()).name("Synthetic notification student")
                .studentId("NT-" + UUID.randomUUID().toString().substring(0, 8)).build();
        em.persist(student);
        return student;
    }

    private Batch batch(User teacherUser) {
        Teacher teacher = Teacher.builder().userId(teacherUser.getId()).name("Synthetic notification teacher").build();
        em.persist(teacher);
        em.flush();
        UUID subjectId = UUID.randomUUID();
        em.unwrap(org.hibernate.Session.class).doWork(connection -> {
            // Older local schemas retain a required subjects.code column not mapped by Subject.
            boolean legacyCode;
            try (var columns = connection.getMetaData().getColumns(null, connection.getSchema(), "subjects", "code")) {
                legacyCode = columns.next();
            }
            String sql = legacyCode
                    ? "INSERT INTO subjects (id, name, active, is_deleted, created_at, code) VALUES (?, ?, true, false, CURRENT_TIMESTAMP, ?)"
                    : "INSERT INTO subjects (id, name, active, is_deleted, created_at) VALUES (?, ?, true, false, CURRENT_TIMESTAMP)";
            try (var statement = connection.prepareStatement(sql)) {
                statement.setObject(1, subjectId);
                statement.setString(2, "Notification test " + subjectId);
                if (legacyCode) statement.setString(3, "NT" + subjectId.toString().substring(0, 8));
                statement.executeUpdate();
            }
        });
        Batch batch = Batch.builder().teacherId(teacher.getId()).subjectId(subjectId)
                .name("Synthetic notification batch").examYear("2026").monthlyFee(BigDecimal.ZERO)
                .deliveryMode("ONLINE").active(true).build();
        em.persist(batch);
        return batch;
    }

    private Enrollment enroll(Student student, Batch batch, String status, boolean deleted) {
        Enrollment enrollment = Enrollment.builder().studentId(student.getId()).batchId(batch.getId())
                .status(status).enrolledDate(LocalDate.now()).build();
        enrollment.setDeleted(deleted);
        em.persist(enrollment);
        return enrollment;
    }

    @Test
    void persistedInboxPaginationAndReadMutationsArePrivateAndIdempotent() {
        User first = account("STUDENT"), other = account("TEACHER");
        notifications.notifyUser(first.getId(), "ASSESSMENT_AVAILABLE", "Assessment ready", "A new assessment is available.", "/assessments");
        notifications.notifyUser(first.getId(), "RESULT_PUBLISHED", "Result available", "Your result has been published.", "/assessments");
        notifications.notifyUser(other.getId(), "ENROLLMENT_REQUEST", "Enrollment request", "A student requested enrollment.", "/teacher/enrollments");
        em.flush();

        var page = notifications.list(first.getId(), 0, 1);
        assertEquals(2, page.totalElements());
        assertEquals(2, page.unreadCount());
        assertEquals(1, page.items().size());
        var secondPage = notifications.list(first.getId(), 1, 1);
        assertNotEquals(page.items().getFirst().id(), secondPage.items().getFirst().id());
        UUID id = page.items().getFirst().id();

        ResponseStatusException denied = assertThrows(ResponseStatusException.class,
                () -> notifications.markRead(other.getId(), id));
        assertEquals(404, denied.getStatusCode().value());
        assertEquals(2, notifications.unreadCount(first.getId()));
        var read = notifications.markRead(first.getId(), id);
        assertTrue(read.read());
        assertNotNull(read.readAt());
        assertEquals(read.readAt(), notifications.markRead(first.getId(), id).readAt());
        assertEquals(1, notifications.unreadCount(first.getId()));
        assertEquals(0, notifications.markAllRead(first.getId()));
        assertEquals(1, notifications.unreadCount(other.getId()));
        assertTrue(notifications.list(first.getId(), 0, 20).items().stream().allMatch(NotificationResponse::read));
    }

    @Test
    void batchAudiencesExcludeInactiveDeletedAndUnenrolledAccounts() {
        User teacher = account("TEACHER"), active = account("STUDENT"), dropped = account("STUDENT"),
                suspended = account("STUDENT"), deletedProfile = account("STUDENT"), deletedAccount = account("STUDENT"),
                deletedEnrollment = account("STUDENT"), notEnrolled = account("STUDENT");
        Batch batch = batch(teacher);
        Student activeStudent = student(active);
        enroll(activeStudent, batch, "ACTIVE", false);
        enroll(student(dropped), batch, "DROPPED", false);
        suspended.setStatus("SUSPENDED");
        enroll(student(suspended), batch, "ACTIVE", false);
        Student removedProfile = student(deletedProfile); removedProfile.setDeleted(true);
        enroll(removedProfile, batch, "ACTIVE", false);
        deletedAccount.setDeleted(true);
        enroll(student(deletedAccount), batch, "ACTIVE", false);
        enroll(student(deletedEnrollment), batch, "ACTIVE", true);
        student(notEnrolled);
        em.flush();

        notifications.notifyBatchStudents(batch.getId(), "CLASS_ANNOUNCEMENT", "Class update", "A class announcement was posted.", "/classes/" + batch.getId() + "?tab=announcements");
        notifications.notifyBatchTeacher(batch.getId(), "ENROLLMENT_REQUEST", "Enrollment request", "A student requested enrollment.", "/teacher/enrollments");
        em.flush();
        assertEquals(1, notifications.unreadCount(active.getId()));
        assertEquals(1, notifications.unreadCount(teacher.getId()));
        for (User excluded : new User[]{dropped, suspended, deletedProfile, deletedAccount, deletedEnrollment, notEnrolled}) {
            assertEquals(0, notifications.unreadCount(excluded.getId()));
        }

        batch.setActive(false); em.flush();
        notifications.notifyBatchStudents(batch.getId(), "CLASS_ANNOUNCEMENT", "Inactive update", "This must not be delivered.", "/classes");
        notifications.notifyBatchTeacher(batch.getId(), "ENROLLMENT_REQUEST", "Inactive request", "This must not be delivered.", "/teacher/enrollments");
        assertEquals(1, notifications.unreadCount(active.getId()));
        assertEquals(1, notifications.unreadCount(teacher.getId()));
    }

    @Test
    void notificationsRollBackWithTheBusinessOperationInsteadOfSurvivingAFailedWrite() {
        User recipient = account("STUDENT"); em.flush();
        var session = em.unwrap(org.hibernate.Session.class);
        session.doWork(connection -> {
            var savepoint = connection.setSavepoint();
            notifications.notifyUser(recipient.getId(), "ASSESSMENT_AVAILABLE", "Rolled back", "This activity failed.", "/assessments");
            em.flush();
            assertEquals(1, notifications.unreadCount(recipient.getId()));
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        });
        em.clear();
        assertEquals(0, notifications.unreadCount(recipient.getId()));
    }
}
