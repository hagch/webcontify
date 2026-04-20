package io.webcontify.shared.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;

    public SmtpEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(EmailMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("EmailMessage must not be null");
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.body(), true);

            if (message.fromName() != null && !message.fromName().isBlank()) {
                helper.setFrom(new InternetAddress(message.fromAddress(), message.fromName(), "UTF-8"));
            } else {
                helper.setFrom(message.fromAddress());
            }

            mailSender.send(mimeMessage);
            log.info("Email sent to={} subject={}", message.to(), message.subject());
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Failed to create email message to={}", message.to(), e);
            throw new RuntimeException("Failed to send email", e);
        } catch (MailException e) {
            log.error("Failed to send email to={}", message.to(), e);
            throw e;
        }
    }
}
