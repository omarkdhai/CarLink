package com.carlink.contact.service;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.contact.dto.ContactFormRequest;
import com.carlink.notification.email.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContactFormServiceTest {

    @Mock private EmailSender emailSender;

    private final CarLinkProperties properties = com.carlink.TestProperties.minimal();
    private ContactFormService service;

    @BeforeEach
    void setUp() {
        service = new ContactFormService(emailSender, properties);
    }

    @Test
    void deliversToConfiguredMailboxWithVisitorDetails() {
        ContactFormRequest body = new ContactFormRequest(
                "Jane", "jane@example.com", "Partnership", "+21655111222", "Let's talk!");

        service.submit(body);

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(to.capture(), subject.capture(), text.capture());

        assertThat(to.getValue()).isEqualTo("hello@carlink.app");
        assertThat(subject.getValue()).isEqualTo("[CarLink] Partnership");
        assertThat(text.getValue())
                .contains("Jane")
                .contains("jane@example.com")
                .contains("+21655111222")
                .contains("Let's talk!");
    }

    @Test
    void omitsBlankPhoneLine() {
        ContactFormRequest body = new ContactFormRequest("Joe", "joe@example.com", "Hi", "  ", "Hello");

        service.submit(body);

        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(emailSender).send(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), text.capture());
        assertThat(text.getValue()).doesNotContain("Phone:");
    }
}