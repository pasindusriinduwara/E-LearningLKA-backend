package lk.tutionlms.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class PasswordMailConfig {
    @Bean("passwordMailExecutor")
    public Executor passwordMailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); executor.setMaxPoolSize(2); executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("password-mail-");
        executor.setRejectedExecutionHandler((task, pool) ->
                org.slf4j.LoggerFactory.getLogger(PasswordMailConfig.class).warn("Password email queue is full; retry delivery later."));
        executor.initialize();
        return executor;
    }
}
