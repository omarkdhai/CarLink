package com.carlink.conversation.repository;

import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.ConversationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Conversation persistence. The public flow writes conversations; the owner
 * dashboard reads them, always scoped by the acting owner's vehicle.
 */
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    /** All conversations for a given owner, newest first. */
    List<Conversation> findAllByVehicle_Owner_IdOrderByCreatedAtDesc(UUID ownerId);

    /** All conversations for a single vehicle, newest first. */
    List<Conversation> findAllByVehicle_IdOrderByCreatedAtDesc(UUID vehicleId);

    /** Finds stale conversations whose expiry has passed and are still in a transitional state. */
    List<Conversation> findByExpiresAtBeforeAndStatusIn(
            Instant expiresAtBefore, List<ConversationStatus> statuses);

    /** Admin analytics: conversations currently in a given state. */
    long countByStatus(ConversationStatus status);
}