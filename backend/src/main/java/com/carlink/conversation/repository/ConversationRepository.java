package com.carlink.conversation.repository;

import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.ConversationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
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

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(c) FROM Conversation c WHERE EXTRACT(HOUR FROM c.createdAt) = :hour")
    long countByHourOfDay(int hour);

    /**
     * Admin: per-vehicle activity as {@code [vehicleId, total, unread, lastCreatedAt]}
     * rows. Counts only — never message content.
     */
    @Query("""
            select c.vehicle.id, count(c),
                   sum(case when c.readAt is null then 1 else 0 end),
                   max(c.createdAt)
            from Conversation c
            where c.vehicle.id in :vehicleIds
            group by c.vehicle.id""")
    List<Object[]> activityByVehicleIds(@Param("vehicleIds") Collection<UUID> vehicleIds);
}