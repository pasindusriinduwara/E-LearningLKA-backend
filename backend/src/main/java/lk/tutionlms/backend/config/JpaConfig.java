package lk.tutionlms.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JpaConfig activates Spring Data JPA's auditing feature.
 */

// @Configuration: Marks this class as a source of Spring bean definitions and
// framework setup.
@Configuration

// @EnableJpaAuditing: Enables the AuditingEntityListener we specified in
// BaseEntity.
// Without this annotation, @CreatedDate and @LastModifiedDate will stay null.
@EnableJpaAuditing
public class JpaConfig {
    // Configuration options (like current auditor provider for @CreatedBy) can be
    // added here later.
}
