package com.carlink.conversation.service;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.conversation.model.Channel;
import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.model.Message;
import com.carlink.conversation.repository.ConversationRepository;
import com.carlink.conversation.repository.MessageRepository;
import com.carlink.vehicle.model.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Persists visitor-initiated conversations. The actual WhatsApp/SMS relay is
 * owned by the contact module (Phase 6); this service only records the
 * conversation and its first message, and stamps an expiry.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final CarLinkProperties properties;

    /**
     * Opens a conversation for a vehicle with {@code status} and sets its
     * expiry from {@code carlink.conversation.expiry-hours}. Returns the
     * conversation id — all identity, no message content.
     */
    public UUID open(Vehicle vehicle, Channel channel, ConversationStatus status) {
        Conversation conversation = Conversation.open(
                vehicle,
                channel,
                status,
                Instant.now().plus(
                        properties.conversation().expiryHours(), ChronoUnit.HOURS));
        return conversationRepository.save(conversation).getId();
    }

    /** Records the first message of a conversation. */
    public void appendMessage(UUID conversationId, String content) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalStateException(
                        "Conversation not found: " + conversationId));
        messageRepository.save(Message.of(conversation, content));
    }
}