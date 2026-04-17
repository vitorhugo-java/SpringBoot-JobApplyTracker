package com.jobtracker.service;

import com.jobtracker.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import org.springframework.scheduling.annotation.Async;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String fromAddress;
    private final boolean mailEnabled;

    public EmailService(JavaMailSender mailSender,
                        TemplateEngine templateEngine,
                        @Value("${spring.mail.properties.mail.from}") fromAddress
                        @Value("${app.mail.enabled:true}") boolean mailEnabled) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.mailEnabled = mailEnabled;
        this.fromAddress = fromAddress;
    }

    @Async
    public void sendPasswordResetEmail(User user, String resetLink, LocalDateTime expiresAt) {
        if (!mailEnabled) {
            log.info("event=MAIL_DISABLED_SKIP to={} type=PASSWORD_RESET", user.getEmail());
            return;
        }

        Context context = new Context();
        context.setVariable("name", user.getName());
        context.setVariable("resetLink", resetLink);
        context.setVariable("expiresAt", expiresAt);

        String htmlBody = templateEngine.process("password-reset", context);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setFrom(fromAddress);
            helper.setSubject("Reset your JobTracker password");
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("event=MAIL_SENT to={} type=PASSWORD_RESET", user.getEmail());
        } catch (MessagingException | MailException ex) {
            throw new IllegalStateException("Failed to send password reset email", ex);
        }
    }
}
