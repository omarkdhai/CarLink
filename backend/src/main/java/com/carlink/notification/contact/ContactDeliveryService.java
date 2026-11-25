package com.carlink.notification.contact;

import com.carlink.conversation.model.Channel;
import com.carlink.conversation.model.ConversationStatus;
import com.carlink.conversation.model.DeliveryStatus;
import com.carlink.conversation.service.ConversationDeliveryService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sends one visitor alert to the vehicle owner on every channel they can get:
 * SMS always, and WhatsApp when a WhatsApp sender is configured
 * ({@code carlink.whatsapp.provider}) and the owner has not turned it off.
 *
 * <p>The channels are sent in parallel. Each one's outcome is saved as soon as
 * it is known ({@link ConversationDeliveryService}): DELIVERED (dev mock), SENT
 * (accepted, the delivery report decides later) or FAILED with a short code —
 * NO_PHONE, INVALID_NUMBER, UNREACHABLE (provider down / timeout), API_&lt;code&gt;,
 * WA_&lt;code&gt; or ERROR. The conversation's status is their aggregate, so the
 * visitor hears "failed" only when no channel could reach the owner.</p>
 *
 * <p>Must be called outside a database transaction: each delivery row is
 * committed at once, so a WhatsApp status webhook arriving a second later finds
 * it. Failures are not thrown. The owner phone is passed only into the senders
 * and is never logged.</p>
 */
@Slf4j
@Service
public class ContactDeliveryService {

    /** Longer than any sender's own HTTP timeouts: only a stuck thread hits it. */
    static final long WAIT_SECONDS = 25;

    private final List<ContactChannelSender> senders;
    private final AnonymousCallSender anonymousCallSender;
    private final ConversationDeliveryService deliveries;
    private final ExecutorService executor;

    public ContactDeliveryService(List<ContactChannelSender> senders,
                                  AnonymousCallSender anonymousCallSender,
                                  ConversationDeliveryService deliveries) {
        // SMS first: it is the channel every owner gets.
        this.senders = senders.stream()
                .sorted(Comparator.comparing((ContactChannelSender s) -> s.channel() != Channel.SMS))
                .toList();
        this.anonymousCallSender = anonymousCallSender;
        this.deliveries = deliveries;
        AtomicInteger n = new AtomicInteger();
        this.executor = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "alert-send-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /** Channels alerts go out on, SMS first (e.g. [SMS, WHATSAPP]). */
    public List<String> channels() {
        return senders.stream().map(s -> s.channel().name()).distinct().toList();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    public ConversationStatus deliver(UUID conversationId, String ownerPhone, boolean whatsappAlerts,
                                      String message, String reason) {
        List<ContactChannelSender> targets = senders.stream()
                .filter(s -> s.channel() == Channel.SMS || (s.channel() == Channel.WHATSAPP && whatsappAlerts))
                .toList();

        // Phone is optional at registration, but no channel can reach an owner
        // without one. Record it as failed rather than sending to null.
        if (ownerPhone == null || ownerPhone.isBlank()) {
            log.warn("Relay skipped, owner has no phone (conversation={})", conversationId);
            for (ContactChannelSender sender : targets) {
                deliveries.record(conversationId, sender.channel(), "none", DeliveryStatus.FAILED, null, "NO_PHONE");
            }
            return deliveries.recompute(conversationId);
        }

        List<CompletableFuture<DeliveryStatus>> sends = new ArrayList<>();
        for (ContactChannelSender sender : targets) {
            ContactDelivery delivery = new ContactDelivery(sender.channel(), ownerPhone, message, reason);
            sends.add(CompletableFuture.supplyAsync(() -> sendAndRecord(conversationId, sender, delivery), executor));
        }

        boolean handedOver = false;
        boolean stillSending = false;
        for (CompletableFuture<DeliveryStatus> send : sends) {
            try {
                handedOver |= send.get(WAIT_SECONDS, TimeUnit.SECONDS) != DeliveryStatus.FAILED;
            } catch (TimeoutException e) {
                // It records its outcome itself when it ends; refresh the
                // aggregate then.
                stillSending = true;
                send.thenRun(() -> deliveries.recompute(conversationId));
                log.warn("Alert send still running for conversation {}", conversationId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                stillSending = true;
            } catch (ExecutionException e) {
                log.warn("Alert send crashed for conversation {}: {}",
                        conversationId, e.getCause().getClass().getSimpleName());
            }
        }

        // The alert call is best-effort. Its failure must not downgrade a
        // message that was already handed over.
        if (handedOver) {
            try {
                anonymousCallSender.initiateCall(ownerPhone, reason, message, conversationId.toString());
            } catch (RuntimeException e) {
                log.warn("Alert call failed for conversation {} (message already relayed)", conversationId);
            }
        }

        ConversationStatus status = deliveries.recompute(conversationId);
        return status == ConversationStatus.FAILED && stillSending ? ConversationStatus.SENT : status;
    }

    /** Sends on one channel and saves the outcome at once. Never throws a send failure. */
    private DeliveryStatus sendAndRecord(UUID conversationId, ContactChannelSender sender, ContactDelivery delivery) {
        SendResult result;
        try {
            result = sender.send(delivery);
        } catch (RuntimeException e) {
            // The error type tells a timeout / unreachable provider apart from a
            // bad number or a bug; the exception message is not logged (it may
            // carry provider details), and never the phone or the note.
            log.warn("Contact relay failed for conversation {} ({}): {}",
                    conversationId, sender.channel(), e.getClass().getSimpleName());
            result = SendResult.failed(errorCode(e));
        }
        DeliveryStatus status = switch (result.outcome()) {
            case DELIVERED -> DeliveryStatus.DELIVERED;
            case ACCEPTED -> DeliveryStatus.SENT;
            case FAILED -> DeliveryStatus.FAILED;
        };
        deliveries.record(conversationId, sender.channel(), sender.provider(),
                status, result.providerMessageId(), result.error());
        return status;
    }

    private static String errorCode(RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            return "UNREACHABLE";
        }
        if (e instanceof IllegalArgumentException) {
            return "INVALID_NUMBER";
        }
        return "ERROR";
    }
}
