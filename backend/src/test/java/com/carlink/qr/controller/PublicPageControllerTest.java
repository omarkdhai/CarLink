package com.carlink.qr.controller;

import com.carlink.qr.dto.ContactSubmitRequest;
import com.carlink.qr.dto.QrPublicView;
import com.carlink.qr.model.ContactReason;
import com.carlink.qr.service.PublicQrService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The server-rendered fallback page at {@code /c/{token}} carries its own inline
 * JavaScript, so the payload it POSTs is written as a string literal rather than
 * through {@link ContactSubmitRequest}. Nothing compiled checks that the two
 * agree, which is how the page came to omit the required {@code reason} field
 * and 400 on every submission.
 *
 * <p>{@link #pageSendsExactlyTheFieldsTheApiAccepts()} closes that gap: it reads
 * the field names straight out of the rendered script and compares them to the
 * DTO's record components.
 */
@ExtendWith(MockitoExtension.class)
class PublicPageControllerTest {

    @Mock
    private PublicQrService publicQrService;
    @Mock
    private HttpServletRequest request;
    @InjectMocks
    private PublicPageController controller;

    @Test
    void rendersOneChipPerContactReason() throws Exception {
        String html = renderBoundPage();

        for (ContactReason reason : ContactReason.values()) {
            assertThat(html)
                    .as("chip for %s", reason)
                    .contains("data-reason=\"" + reason.name() + "\"")
                    .contains(reason.label());
        }
        // Nothing may reach the page that identifies the owner.
        assertThat(html).doesNotContain("@REASONS@").doesNotContain("@VEHICLE@");
    }

    @Test
    void pageSendsExactlyTheFieldsTheApiAccepts() throws Exception {
        String html = renderBoundPage();

        Matcher body = Pattern.compile("JSON\\.stringify\\(\\{([^}]*)\\}\\)").matcher(html);
        assertThat(body.find())
                .as("the page must build its POST body with JSON.stringify")
                .isTrue();

        Set<String> sent = Arrays.stream(body.group(1).split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(pair -> pair.split(":")[0].trim())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> accepted = Arrays.stream(ContactSubmitRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(sent)
                .as("fields POSTed by /c/{token} vs fields ContactSubmitRequest accepts")
                .isEqualTo(accepted);
    }

    @Test
    void messageIsOptionalButReasonIsRequired() throws Exception {
        String html = renderBoundPage();
        // The visitor may leave the note blank; the reason is the required part.
        assertThat(html).doesNotContain("Please write a message.");
        assertThat(html).contains("Please choose a reason.");
    }

    @Test
    void unknownTokenRendersNotFoundPage() throws Exception {
        when(publicQrService.resolve(anyString(), any()))
                .thenThrow(new com.carlink.common.exception.NotFoundException("nope"));

        ResponseEntity<String> response = controller.page("unknown-token", request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).contains("not active");
    }

    // ---------- helpers ----------

    private String renderBoundPage() throws Exception {
        when(publicQrService.resolve(anyString(), any())).thenReturn(new QrPublicView(
                "BOUND",
                new QrPublicView.VehicleSafe("My Car", "Toyota", "Corolla", "Blue"),
                List.of("WHATSAPP", "SMS")));

        ResponseEntity<String> response = controller.page("some-raw-token", request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        return response.getBody();
    }
}