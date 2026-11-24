package lk.tutionlms.backend.identity.auth;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByTokenHash(String hash);

    @Query("select t.userId from PasswordResetToken t where t.tokenHash = :hash")
    Optional<UUID> findUserIdByTokenHash(@Param("hash") String hash);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :hash")
    Optional<PasswordResetToken> findByHashForUpdate(@Param("hash") String hash);

    @Modifying(flushAutomatically = true)
    @Query("update PasswordResetToken t set t.consumedAt = :now where t.userId = :userId and t.consumedAt is null")
    int consumeForUser(@Param("userId") UUID userId, @Param("now") Instant now);
}
