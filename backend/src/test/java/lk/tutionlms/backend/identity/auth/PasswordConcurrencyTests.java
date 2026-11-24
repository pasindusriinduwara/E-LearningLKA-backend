package lk.tutionlms.backend.identity.auth;

import lk.tutionlms.backend.identity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses PostgreSQL transactions; synthetic accounts are removed after each committed concurrency test. */
@SpringBootTest(properties={"logging.level.root=ERROR","spring.jpa.show-sql=false"})
class PasswordConcurrencyTests {
    @Autowired PasswordService service;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager manager;
    @Test void oneLinkCanChangeThePasswordOnlyOnceAcrossConcurrentTransactions() throws Exception {
        User user = User.builder().email("concurrency-"+UUID.randomUUID()+"@example.test").password(encoder.encode("old-password")).userType("STUDENT").build();
        users.saveAndFlush(user);
        String raw = "B".repeat(43);
        PasswordResetToken token = new PasswordResetToken(); token.setUserId(user.getId());token.setTokenHash(PasswordService.hash(raw));token.setExpiresAt(Instant.now().plusSeconds(60)); tokens.saveAndFlush(token);
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
            Callable<Boolean> attempt = () -> { ready.countDown(); start.await(); try { service.reset(raw,"new-password"); return true; } catch(IllegalArgumentException expected) {return false;} };
            Future<Boolean> first=pool.submit(attempt), second=pool.submit(attempt); assertTrue(ready.await(10,TimeUnit.SECONDS)); start.countDown();
            int successes=(first.get(15,TimeUnit.SECONDS)?1:0)+(second.get(15,TimeUnit.SECONDS)?1:0);
            assertEquals(1,successes); assertEquals(1,users.findById(user.getId()).orElseThrow().getCredentialVersion());
        } finally {
            new TransactionTemplate(manager).executeWithoutResult(status -> { tokens.deleteById(token.getId()); users.deleteById(user.getId()); });
        }
    }
}
