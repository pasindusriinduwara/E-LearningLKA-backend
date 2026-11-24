package lk.tutionlms.backend.identity.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import java.net.URI;

@Component
public class PasswordMailDelivery {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PasswordMailDelivery.class);
    private final JavaMailSender sender;
    private final String from;
    private final String publicUrl;
    public PasswordMailDelivery(JavaMailSender sender, @Value("${application.mail.from}") String from,
                                @Value("${application.public-url}") String publicUrl) {
        URI uri = URI.create(publicUrl);
        if (!java.util.Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
            throw new IllegalArgumentException("APP_PUBLIC_URL must be an application origin.");
        }
        this.sender = sender; this.from = from; this.publicUrl = publicUrl.replaceAll("/$", "");
    }
    @Async("passwordMailExecutor")
    @TransactionalEventListener
    public void deliver(PasswordResetMail event) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from); mail.setTo(event.email()); mail.setSubject("Reset your E-LearningLKA password");
            mail.setText("Use this link within 30 minutes to reset your password:\n\n" + publicUrl
                    + "/reset-password#token=" + event.token()
                    + "\n\nIf you did not request this, ignore this message. Your password has not changed.");
            sender.send(mail);
        } catch (RuntimeException e) {
            // No addresses, links, token, message body or provider exception are logged.
            LOG.warn("Password reset email delivery failed; check SMTP configuration.");
        }
    }
}
