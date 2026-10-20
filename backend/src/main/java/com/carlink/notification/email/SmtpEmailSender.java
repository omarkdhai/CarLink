package com.carlink.notification.email;

import com.carlink.common.config.CarLinkProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Real SMTP delivery (production / MailHog in dev). Active only when
 * {@code carlink.email.provider=smtp}.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "carlink.email.provider", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

    private static final String FROM_NAME = "CarLink";

    private final JavaMailSender mailSender;
    private final CarLinkProperties properties;

    @Override
    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        applyIdentity(message);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    @Override
    public void sendHtml(String to, String subject, String html, String textBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            // multipart=true is required for setText(plain, html) alternatives.
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            String from = properties.email().from();
            if (from != null && !from.isBlank()) {
                helper.setFrom(from, FROM_NAME);
            }
            String replyTo = properties.email().replyTo();
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody, html);
            mailSender.send(message);
        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            throw new MailSendException("Could not build the HTML email", ex);
        }
    }

    private void applyIdentity(SimpleMailMessage message) {
        String from = properties.email().from();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        String replyTo = properties.email().replyTo();
        if (replyTo != null && !replyTo.isBlank()) {
            message.setReplyTo(replyTo);
        }
    }
}
