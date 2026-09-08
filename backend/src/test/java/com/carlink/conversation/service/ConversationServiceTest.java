package com.carlink.conversation.service;

import com.carlink.TestProperties;
import com.carlink.common.exception.NotFoundException;
import com.carlink.conversation.dto.ConversationDetailResponse;
import com.carlink.conversation.dto.ConversationMessageResponse;
import com.carlink.conversation.dto.ConversationSummaryResponse;
import com.carlink.conversation.model.Channel;
import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.model.Message;
import com.carlink.conversation.repository.ConversationRepository;
import com.carlink.conversation.repository.MessageRepository;
import com.carlink.user.model.Role;
import com.carlink.user.model.User;
import com.carlink.vehicle.model.Vehicle;
import com.carlink.vehicle.service.VehicleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private VehicleService vehicleService;

    private ConversationService service;
    private final UUID ownerId = UUID.randomUUID();
    private final UUID otherOwnerId = UUID.randomUUID();
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        service = new ConversationService(
                conversationRepository, messageRepository, vehicleService, TestProperties.minimal());
        vehicle = Vehicle.builder()
                .id(UUID.randomUUID())
                .owner(User.builder().id(ownerId).email("owner@example.com").role(Role.USER).build())
                .nickname("My Car")
                .brand("Toyota")
                .model("Corolla")
                .color("Blue")
                .licensePlate("AB-123-CD")
                .build();
    }

    private Conversation conversation(Instant readAt, Channel channel) {
        return Conversation.builder()
                .id(UUID.randomUUID())
                .vehicle(vehicle)
                .channel(channel)
                .status(ConversationStatus.SENT)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .createdAt(Instant.now())
                .readAt(readAt)
                .build();
    }

    // ---------- listForOwner ----------

    @Test
    void listForOwnerReturnsSummariesWithUnreadFlagsAndPreview() {
        Conversation unread = conversation(null, Channel.WHATSAPP);
        Conversation read = conversation(Instant.now(), Channel.SMS);
        when(conversationRepository.findAllByVehicle_Owner_IdOrderByCreatedAtDesc(ownerId))
                .thenReturn(List.of(unread, read));
        when(messageRepository.findAllByConversation_IdOrderByCreatedAtAsc(unread.getId()))
                .thenReturn(List.of(Message.of(unread, "Hello, is it available?")));
        when(messageRepository.findAllByConversation_IdOrderByCreatedAtAsc(read.getId()))
                .thenReturn(List.of(Message.of(read, "Hi, can I call you?")));

        List<ConversationSummaryResponse> summaries = service.listForOwner(ownerId);

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).unread()).isTrue();
        assertThat(summaries.get(0).channel()).isEqualTo("WHATSAPP");
        assertThat(summaries.get(0).vehicleNickname()).isEqualTo("My Car");
        assertThat(summaries.get(0).lastMessagePreview()).isEqualTo("Hello, is it available?");
        assertThat(summaries.get(1).unread()).isFalse();
    }

    @Test
    void listForOwnerShowsNullPreviewWhenNoMessages() {
        Conversation conv = conversation(null, Channel.WHATSAPP);
        when(conversationRepository.findAllByVehicle_Owner_IdOrderByCreatedAtDesc(ownerId))
                .thenReturn(List.of(conv));
        when(messageRepository.findAllByConversation_IdOrderByCreatedAtAsc(conv.getId()))
                .thenReturn(List.of());

        assertThat(service.listForOwner(ownerId).get(0).lastMessagePreview()).isNull();
    }

    @Test
    void listForOwnerTruncatesLongPreviewTo80Chars() {
        Conversation conv = conversation(null, Channel.WHATSAPP);
        String longMsg = "x".repeat(120);
        when(conversationRepository.findAllByVehicle_Owner_IdOrderByCreatedAtDesc(ownerId))
                .thenReturn(List.of(conv));
        when(messageRepository.findAllByConversation_IdOrderByCreatedAtAsc(conv.getId()))
                .thenReturn(List.of(Message.of(conv, longMsg)));

        ConversationSummaryResponse summary = service.listForOwner(ownerId).get(0);

        assertThat(summary.lastMessagePreview()).hasSize(81); // 80 + "…"
        assertThat(summary.lastMessagePreview()).endsWith("…");
    }

    // ---------- listForVehicle ----------

    @Test
    void listForVehicleScopesToVehicle() {
        Conversation conv = conversation(null, Channel.WHATSAPP);
        when(vehicleService.getOwned(ownerId, vehicle.getId())).thenReturn(vehicle);
        when(conversationRepository.findAllByVehicle_IdOrderByCreatedAtDesc(vehicle.getId()))
                .thenReturn(List.of(conv));
        when(messageRepository.findAllByConversation_IdOrderByCreatedAtAsc(conv.getId()))
                .thenReturn(List.of(Message.of(conv, "hi")));

        List<ConversationSummaryResponse> list = service.listForVehicle(ownerId, vehicle.getId());

        assertThat(list).hasSize(1);
        assertThat(list.get(0).vehicleNickname()).isEqualTo("My Car");
    }

    @Test
    void listForVehicleThrowsNotFoundForCrossOwner() {
        when(vehicleService.getOwned(otherOwnerId, vehicle.getId()))
                .thenThrow(new NotFoundException("Vehicle not found"));
        assertThatThrownBy(() -> service.listForVehicle(otherOwnerId, vehicle.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- getForOwner ----------

    @Test
    void getForOwnerReturnsFullHistory() {
        Conversation conv = conversation(null, Channel.WHATSAPP);
        Message m1 = Message.of(conv, "first");
        Message m2 = Message.of(conv, "second");
        when(conversationRepository.findById(conv.getId())).thenReturn(Optional.of(conv));
        when(vehicleService.getOwned(ownerId, vehicle.getId())).thenReturn(vehicle);
        when(messageRepository.findAllByConversation_IdOrderByCreatedAtAsc(conv.getId()))
                .thenReturn(List.of(m1, m2));

        ConversationDetailResponse detail = service.getForOwner(ownerId, conv.getId());

        assertThat(detail.id()).isEqualTo(conv.getId());
        assertThat(detail.messages()).extracting(ConversationMessageResponse::content)
                .containsExactly("first", "second");
        assertThat(detail.unread()).isTrue();
    }

    @Test
    void getForOwnerThrowsNotFoundForUnknownConversation() {
        when(conversationRepository.findById(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getForOwner(ownerId, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getForOwnerThrowsNotFoundForCrossOwner() {
        Conversation conv = conversation(null, Channel.SMS);
        when(conversationRepository.findById(conv.getId())).thenReturn(Optional.of(conv));
        when(vehicleService.getOwned(otherOwnerId, vehicle.getId()))
                .thenThrow(new NotFoundException("Vehicle not found"));

        assertThatThrownBy(() -> service.getForOwner(otherOwnerId, conv.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    // ---------- markRead ----------

    @Test
    void markReadSetsReadAt() {
        Conversation conv = conversation(null, Channel.SMS);
        when(conversationRepository.findById(conv.getId())).thenReturn(Optional.of(conv));
        when(vehicleService.getOwned(ownerId, vehicle.getId())).thenReturn(vehicle);

        service.markRead(ownerId, conv.getId());

        assertThat(conv.getReadAt()).isNotNull();
    }

    @Test
    void markReadCrossOwnerThrowsNotFound() {
        Conversation conv = conversation(null, Channel.SMS);
        when(conversationRepository.findById(conv.getId())).thenReturn(Optional.of(conv));
        when(vehicleService.getOwned(otherOwnerId, vehicle.getId()))
                .thenThrow(new NotFoundException("Vehicle not found"));

        assertThatThrownBy(() -> service.markRead(otherOwnerId, conv.getId()))
                .isInstanceOf(NotFoundException.class);
        assertThat(conv.getReadAt()).isNull();
    }

    // ---------- sweepExpiredConversations ----------

    @Test
    void sweepMarksStaleConversationsExpired() {
        Conversation stale1 = conversation(null, Channel.WHATSAPP);
        Conversation stale2 = conversation(null, Channel.SMS);
        when(conversationRepository.findByExpiresAtBeforeAndStatusIn(any(), any()))
                .thenReturn(List.of(stale1, stale2));

        service.sweepExpiredConversations();

        assertThat(stale1.getStatus()).isEqualTo(ConversationStatus.EXPIRED);
        assertThat(stale2.getStatus()).isEqualTo(ConversationStatus.EXPIRED);
    }

    @Test
    void sweepDoesNothingWhenNoStaleConversations() {
        when(conversationRepository.findByExpiresAtBeforeAndStatusIn(any(), any()))
                .thenReturn(List.of());

        service.sweepExpiredConversations(); // no exception
    }
}
