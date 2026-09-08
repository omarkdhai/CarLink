package com.carlink.conversation.service;

import com.carlink.common.config.CarLinkProperties;
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
import com.carlink.vehicle.model.Vehicle;
import com.carlink.vehicle.service.VehicleService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Persists visitor-initiated conversations (public flow) and exposes
 * owner-scoped read/write operations (Phase 7 dashboard). The actual
 * WhatsApp/SMS relay is in the contact module (Phase 6).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final VehicleService vehicleService;
    private final CarLinkProperties properties;

    // ---- public flow (Phases 5-6) ----

    public UUID open(Vehicle vehicle, Channel channel, ConversationStatus status) {
        Conversation conversation = Conversation.open(
                vehicle,
                channel,
                status,
                Instant.now().plus(
                        properties.conversation().expiryHours(), ChronoUnit.HOURS));
        return conversationRepository.save(conversation).getId();
    }

    public void appendMessage(UUID conversationId, String content) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Conversation not found: " + conversationId));
        messageRepository.save(Message.of(conversation, content));
    }

    public void updateStatus(UUID conversationId, ConversationStatus status) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Conversation not found: " + conversationId));
        conversation.setStatus(status);
    }

    // ---- owner dashboard (Phase 7) ----

    /** Returns all conversations for the owner, newest first, with a last-message preview. */
    public List<ConversationSummaryResponse> listForOwner(UUID ownerId) {
        List<Conversation> conversations =
                conversationRepository.findAllByVehicle_Owner_IdOrderByCreatedAtDesc(ownerId);

        return conversations.stream()
                .map(conv -> {
                    List<Message> msgs = messageRepository
                            .findAllByConversation_IdOrderByCreatedAtAsc(conv.getId());
                    String preview = msgs.isEmpty() ? null
                            : msgs.get(msgs.size() - 1).getContent();
                    return ConversationSummaryResponse.from(conv, preview);
                })
                .toList();
    }

    /** Returns conversations scoped to one vehicle. */
    public List<ConversationSummaryResponse> listForVehicle(UUID ownerId, UUID vehicleId) {
        // Ownership check (throws 404 on mismatch)
        vehicleService.getOwned(ownerId, vehicleId);

        List<Conversation> conversations =
                conversationRepository.findAllByVehicle_IdOrderByCreatedAtDesc(vehicleId);

        return conversations.stream()
                .map(conv -> {
                    List<Message> msgs = messageRepository
                            .findAllByConversation_IdOrderByCreatedAtAsc(conv.getId());
                    String preview = msgs.isEmpty() ? null
                            : msgs.get(msgs.size() - 1).getContent();
                    return ConversationSummaryResponse.from(conv, preview);
                })
                .toList();
    }

    /** Returns the full message history for one conversation. */
    public ConversationDetailResponse getForOwner(UUID ownerId, UUID conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));

        // Ownership check — 404 on mismatch
        vehicleService.getOwned(ownerId, conv.getVehicle().getId());

        List<ConversationMessageResponse> messages = messageRepository
                .findAllByConversation_IdOrderByCreatedAtAsc(conversationId)
                .stream()
                .map(ConversationMessageResponse::from)
                .toList();

        return ConversationDetailResponse.from(conv, messages);
    }

    /** Marks a conversation as read. */
    public void markRead(UUID ownerId, UUID conversationId) {
        Conversation conv = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));

        // Ownership check — 404 on mismatch
        vehicleService.getOwned(ownerId, conv.getVehicle().getId());

        conv.setReadAt(Instant.now());
    }

    // ---- expiry sweeper ----

    /** Finds conversations past their expiry that are still in a transitional state and marks them EXPIRED. */
    @Scheduled(fixedDelayString = "${carlink.conversation.sweep-interval-ms:3600000}")
    public void sweepExpiredConversations() {
        var now = Instant.now();
        var stale = conversationRepository.findByExpiresAtBeforeAndStatusIn(
                now, List.of(ConversationStatus.PENDING, ConversationStatus.SENT));
        stale.forEach(c -> c.setStatus(ConversationStatus.EXPIRED));
    }
}