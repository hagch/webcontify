package io.webcontify.shared.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    private SmtpEmailService emailService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        emailService = new SmtpEmailService(mailSender);
    }

    @Test
    void send_withValidMessage_sendsEmail() throws Exception {
        EmailMessage message = new EmailMessage(
                "user@example.com",
                "Test Subject",
                "<h1>Hello</h1>",
                "noreply@tenant.com",
                "Tenant Name");

        emailService.send(message);

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue()).isSameAs(mimeMessage);
    }

    @Test
    void send_withoutFromName_sendsEmailWithAddressOnly() {
        EmailMessage message = new EmailMessage(
                "user@example.com",
                "Test Subject",
                "<p>Body</p>",
                "noreply@tenant.com",
                null);

        emailService.send(message);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_withBlankFromName_sendsEmailWithAddressOnly() {
        EmailMessage message = new EmailMessage(
                "user@example.com",
                "Test Subject",
                "<p>Body</p>",
                "noreply@tenant.com",
                "   ");

        emailService.send(message);

        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void send_withNullMessage_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> emailService.send(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("EmailMessage must not be null");
    }

    @Test
    void send_whenMailSenderThrowsMailException_propagatesException() {
        EmailMessage message = new EmailMessage(
                "user@example.com",
                "Test Subject",
                "<p>Body</p>",
                "noreply@tenant.com",
                "Tenant");

        doThrow(new MailSendException("SMTP connection failed"))
                .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.send(message))
                .isInstanceOf(MailSendException.class)
                .hasMessageContaining("SMTP connection failed");
    }
}
