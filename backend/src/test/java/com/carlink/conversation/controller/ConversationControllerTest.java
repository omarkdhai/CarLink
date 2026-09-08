package com.carlink.conversation.controller;

import com.carlink.common.dto.MessageResponse;
import com.carlink.common.exception.UnauthorizedException;
import com.carlink.conversation.dto.ConversationDetailResponse;
import com.carlink.conversation.dto.ConversationMessageResponse;
import com.carlink.conversation.dto.ConversationSummaryResponse;
import com.carlink.conversation.service.ConversationService;
import com.carlink.security.principal.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationControllerTest {

    @Mock private ConversationService conversationService;

    private ConversationController controller;

    private static final UUID OWNER_ID = UUID.randomUUID();
    private static final UUID VEHICLE_ID = UUID.randomUUID();
    private static final UUID CONV_ID = UUID.randomUUID();
    private static final UserPrincipal PRINCIPAL =
            new UserPrincipal(OWNER_ID, "owner@example.com", "USER");

    @BeforeEach
    void setUp() {
        controller = new ConversationController(conversationService);
    }

    private ConversationSummaryResponse summary(UUID id) {
        return new ConversationSummaryResponse(
                id, VEHICLE_ID, "My Car", "WHATSAPP", "SENT",
                true, Instant.now(), Instant.now(), "Hello!");
    }

    private ConversationDetailResponse detail(UUID id) {
        return new ConversationDetailResponse(
                id, VEHICLE_ID, "My Car", "WHATSAPP", "SENT",
                true, Instant.now(), Instant.now(),
                List.of(new ConversationMessageResponse(
                        UUID.randomUUID(), "Hello!", Instant.now())));
    }

    // ---------- list ----------

    @Test
    void listWithoutVehicleIdDelegatesToListForOwner() {
        ConversationSummaryResponse s = summary(CONV_ID);
        when(conversationService.listForOwner(OWNER_ID)).thenReturn(List.of(s));

        List<ConversationSummaryResponse> result = controller.list(PRINCIPAL, null);

        assertThat(result).containsExactly(s);
        verify(conversationService).listForOwner(OWNER_ID);
    }

    @Test
    void listWithVehicleIdDelegatesToListForVehicle() {
        when(conversationService.listForVehicle(OWNER_ID, VEHICLE_ID)).thenReturn(List.of());

        controller.list(PRINCIPAL, VEHICLE_ID);

        verify(conversationService).listForVehicle(OWNER_ID, VEHICLE_ID);
    }

    // ---------- get ----------

    @Test
    void getDelegatesToGetForOwner() {
        ConversationDetailResponse d = detail(CONV_ID);
        when(conversationService.getForOwner(OWNER_ID, CONV_ID)).thenReturn(d);

        ConversationDetailResponse result = controller.get(PRINCIPAL, CONV_ID);

        assertThat(result).isSameAs(d);
        verify(conversationService).getForOwner(OWNER_ID, CONV_ID);
    }

    // ---------- markRead ----------

    @Test
    void markReadReturnsAcknowledgmentAndDelegates() {
        MessageResponse result = controller.markRead(PRINCIPAL, CONV_ID);

        assertThat(result.message()).contains("read");
        verify(conversationService).markRead(OWNER_ID, CONV_ID);
    }

    // ---------- null principal ----------

    @Test
    void nullPrincipalThrowsUnauthorizedForAllEndpoints() {
        UserPrincipal nullPrincipal = null;

        assertThatThrownBy(() -> controller.list(nullPrincipal, null))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> controller.get(nullPrincipal, CONV_ID))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> controller.markRead(nullPrincipal, CONV_ID))
                .isInstanceOf(UnauthorizedException.class);
    }
}
