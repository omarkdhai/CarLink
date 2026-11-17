package com.carlink.contact.service;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.contact.dto.ContactFormRequest;
import com.carlink.contact.model.ContactMessage;
import com.carlink.contact.repository.ContactMessageRepository;
import com.carlink.notification.email.EmailSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ContactFormService {

    private final EmailSender emailSender;
    private final CarLinkProperties properties;
    private final ContactMessageRepository contactMessageRepository;

    public void submit(ContactFormRequest request) {
        contactMessageRepository.save(ContactMessage.builder()
                .name(request.name())
                .email(request.email())
                .mobile(request.phone())
                .reason(request.subject())
                .note(request.message())
                .createdAt(java.time.Instant.now())
                .build());

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
