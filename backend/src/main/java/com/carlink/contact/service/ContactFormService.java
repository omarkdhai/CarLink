package com.carlink.contact.service;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.contact.dto.ContactFormRequest;
import com.carlink.notification.email.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Delivers a public "Contact us" form submission to the configured business
 * mailbox. The visitor's email and phone are included only to let the team
 * reply to them — nothing is stored, logged beyond the mail transport, or
 * returned by the API.
 */
@Service
@RequiredArgsConstructor
public class ContactFormService {

    private final EmailSender emailSender;
    private final CarLinkProperties properties;

    public void submit(ContactFormRequest request) {
        String to = properties.contactForm().toEmail();
        StringBuilder body = new StringBuilder();
        body.append("New message via the CarLink website contact form\n\n");
        body.append("Name:    ").append(request.name()).append('\n');
        body.append("Email:   ").append(request.email()).append('\n');
        if (request.phone() != null && !request.phone().isBlank()) {
            body.append("Phone:   ").append(request.phone()).append('\n');
        }
        body.append("Subject: ").append(request.subject()).append("\n\n");
        body.append(request.message()).append('\n');

        emailSender.send(to, "[CarLink] " + request.subject(), body.toString());
    }
}