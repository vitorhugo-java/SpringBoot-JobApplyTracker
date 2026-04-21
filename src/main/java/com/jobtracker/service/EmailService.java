package com.jobtracker.service;

import com.jobtracker.entity.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private static final DateTimeFormatter TEST_EMAIL_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String fromAddress;
    private final boolean mailEnabled;

    public EmailService(JavaMailSender mailSender,
                        TemplateEngine templateEngine,
                        @Value("${spring.mail.properties.mail.from}") String fromAddress,
                        @Value("${app.mail.enabled:true}") boolean mailEnabled) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.mailEnabled = mailEnabled;
        this.fromAddress = fromAddress;
    }

    public boolean isMailEnabled() {
        return mailEnabled;
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

    public void sendPendingApplicationsReminderEmail(User user, long pendingCount) {
        if (!mailEnabled) {
            log.info("event=MAIL_DISABLED_SKIP to={} type=PENDING_APPLICATIONS_REMINDER", user.getEmail());
            return;
        }

        String body = String.format(
                "Hello %s,\n\nYou still have %d pending application(s) marked as 'to send later'.\nOpen JobTracker and review them today.\n",
                user.getName(),
                pendingCount
        );

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setFrom(fromAddress);
            helper.setSubject("JobTracker reminder: pending applications to send");
            helper.setText(body, false);
            mailSender.send(message);
            log.info("event=MAIL_SENT to={} type=PENDING_APPLICATIONS_REMINDER pendingCount={}", user.getEmail(), pendingCount);
        } catch (MessagingException | MailException ex) {
            throw new IllegalStateException("Failed to send pending applications reminder email", ex);
        }
    }

    public void sendRecruiterDmReminderEmail(User user, long pendingDmCount) {
        if (!mailEnabled) {
            log.info("event=MAIL_DISABLED_SKIP to={} type=RECRUITER_DM_REMINDER", user.getEmail());
            return;
        }

        String body = String.format(
                "Hello %s,\n\nYou still have %d application(s) with recruiter DM reminder enabled and no DM sent yet.\nOpen JobTracker and send your recruiter message today.\n",
                user.getName(),
                pendingDmCount
        );

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setFrom(fromAddress);
            helper.setSubject("JobTracker reminder: send recruiter DM");
            helper.setText(body, false);
            mailSender.send(message);
            log.info("event=MAIL_SENT to={} type=RECRUITER_DM_REMINDER pendingDmCount={}", user.getEmail(), pendingDmCount);
        } catch (MessagingException | MailException ex) {
            throw new IllegalStateException("Failed to send recruiter DM reminder email", ex);
        }
    }

    public void sendTestEmail(User user) {
        if (!mailEnabled) {
            log.info("event=MAIL_DISABLED_SKIP to={} type=TEST_EMAIL", user.getEmail());
            return;
        }

        String body = String.format(
                "Hello %s,\n\nThis is a test email from JobTracker.\nSent at: %s\n\nIf you received this message, your email configuration is working.",
                user.getName(),
                LocalDateTime.now().format(TEST_EMAIL_TIMESTAMP_FORMAT)
        );

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(user.getEmail());
            helper.setFrom(fromAddress);
            helper.setSubject("JobTracker test email");
            helper.setText(body, false);
            mailSender.send(message);
            log.info("event=MAIL_SENT to={} type=TEST_EMAIL", user.getEmail());
        } catch (MessagingException | MailException ex) {
            throw new IllegalStateException("Failed to send test email", ex);
        }
    }
}
