package com.carlink.conversation.repository;

import com.carlink.conversation.model.Channel;
import com.carlink.conversation.model.ConversationDelivery;
import com.carlink.conversation.model.DeliveryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Per-channel outcomes of the alerts. */
public interface ConversationDeliveryRepository extends JpaRepository<ConversationDelivery, UUID> {

    List<ConversationDelivery> findAllByConversationId(UUID conversationId);

    List<ConversationDelivery> findAllByConversationIdIn(Collection<UUID> conversationIds);

    /** The delivery a provider's report (webhook) is about. */
    Optional<ConversationDelivery> findByProviderAndProviderMessageId(String provider, String providerMessageId);

    /** Accepted messages of one channel/provider still waiting for their report, oldest first. */
    List<ConversationDelivery> findTop100ByChannelAndProviderAndStatusAndProviderMessageIdIsNotNullAndCreatedAtAfterOrderByCreatedAtAsc(
            Channel channel, String provider, DeliveryStatus status, Instant createdAfter);

    /**
     * Applies a report only while the delivery is in one of {@code from}: a
     * report never goes backwards (FAILED after DELIVERED, DELIVERED after READ),
     * and two pollers cannot apply it twice.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ConversationDelivery d set d.status = :status, d.error = :error, d.updatedAt = :now "
            + "where d.id = :id and d.status in :from")
    int applyReport(@Param("id") UUID id, @Param("status") DeliveryStatus status,
                    @Param("error") String error, @Param("now") Instant now,
                    @Param("from") Collection<DeliveryStatus> from);
}
