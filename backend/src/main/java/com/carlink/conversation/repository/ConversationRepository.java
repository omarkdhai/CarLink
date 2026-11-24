package com.carlink.conversation.repository;

import com.carlink.conversation.model.Conversation;
import com.carlink.conversation.model.ConversationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    /** Accepted SMS still waiting for their delivery report, oldest first. */
    List<Conversation> findTop100ByStatusAndProviderMessageIdIsNotNullAndCreatedAtAfterOrderByCreatedAtAsc(
            ConversationStatus status, Instant createdAfter);

    /**
     * Applies a delivery report, only if the alert is still SENT (a report never
     * overrides EXPIRED, and two pollers cannot apply it twice).
     */
    @Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query("update Conversation c set c.status = :status, c.deliveryError = :error "
            + "where c.id = :id and c.status = com.carlink.conversation.model.ConversationStatus.SENT")
    int applyDeliveryReport(@Param("id") UUID id, @Param("status") ConversationStatus status,
                            @Param("error") String error);

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