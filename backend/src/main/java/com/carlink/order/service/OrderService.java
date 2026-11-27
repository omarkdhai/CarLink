package com.carlink.order.service;

import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.exception.BadRequestException;
import com.carlink.common.exception.NotFoundException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.order.dto.OrderCreateRequest;
import com.carlink.order.dto.OrderItemRequest;
import com.carlink.order.dto.OrderItemView;
import com.carlink.order.dto.OrderResponse;
import com.carlink.order.dto.OrderSummaryResponse;
import com.carlink.order.dto.AdminOrderStickerView;
import com.carlink.admin.dto.AdminActor;
import com.carlink.admin.service.AuditLogService;
import com.carlink.privacy.PhoneCipher;
import com.carlink.order.model.Order;
import com.carlink.order.model.OrderItem;
import com.carlink.order.model.OrderStatus;
import com.carlink.order.repository.OrderItemRepository;
import com.carlink.order.repository.OrderRepository;
import com.carlink.order.email.OrderConfirmationEmailComposer;
import com.carlink.notification.email.EmailSender;
import com.carlink.qr.service.QrImageGenerator;
import com.carlink.sticker.model.Sticker;
import com.carlink.sticker.repository.StickerRepository;
import com.carlink.order.dto.MyOrderResponse;
import com.carlink.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * Order lifecycle for the guest COD checkout. All pricing, totals, and
 * sticker counts are computed exclusively server-side; the client only
 * submits package types and quantities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class OrderService {

    private static final int MAX_TOTAL_STICKERS = 100;
    /** Flat delivery fee (TND) applied once per order, on top of the pack price. */
    private static final BigDecimal DELIVERY_FEE = new BigDecimal("7.00");
    private static final int REFERENCE_RANDOM_LEN = 8;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] REFERENCE_CHARS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final StickerRepository stickerRepository;
    private final TokenGenerator tokenGenerator;
    private final QrImageGenerator imageGenerator;
    private final CarLinkProperties properties;
    private final EmailSender emailSender;
    private final OrderConfirmationEmailComposer confirmationEmailComposer;
    private final UserRepository userRepository;
    private final PhoneCipher cipher;
    private final AuditLogService auditLogService;

    /** Most recent purchases shown in an owner's history. */
    private static final int HISTORY_LIMIT = 100;

    /**
     * Creates a new COD order and mints its stickers. The response is
     * token-free: QR tokens and activation codes are stored encrypted and only
     * an admin can read them (to print the stickers).
     *
     * @throws BadRequestException if total stickers exceeds {@value MAX_TOTAL_STICKERS}
     */
    @Transactional
    public OrderResponse create(OrderCreateRequest request) {
        int totalStickers = request.items().stream()
                .mapToInt(it -> it.stickerPackage().stickersPerPack() * it.quantity())
                .sum();
        if (totalStickers > MAX_TOTAL_STICKERS) {
            throw new BadRequestException(
                    "Order too large — maximum " + MAX_TOTAL_STICKERS + " stickers per order");
        }

        String reference = generateReference();

        Order order = Order.builder()
                .reference(reference)
                .customerName(request.customerName().trim())
                .mobile(request.mobile())
                .email(request.email().toLowerCase(Locale.ROOT).trim())
                .governorate(request.governorate())
                .deliveryAddress(request.deliveryAddress().trim())
                .deliveryNotes(trimToNull(request.deliveryNotes()))
                .stickerStyle(request.stickerStyle() == null
                        ? com.carlink.order.model.StickerStyle.DARK : request.stickerStyle())
                .status(OrderStatus.PLACED)
                .totalAmount(computeTotal(request.items()))
                .currency("TND")
                .createdAt(Instant.now())
                .build();
        Order saved = orderRepository.save(order);

        List<OrderItem> items = request.items().stream()
                .map(it -> buildItem(saved, it))
                .toList();
        orderItemRepository.saveAll(items);

        mintStickers(saved, request.items());

        sendConfirmationAfterCommit(saved, items);

        return new OrderResponse(
                order.getReference(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt(),
                items.stream().map(OrderItemView::from).toList(),
                order.getStickerStyle().name());
    }

    /**
     * Returns a token-free, PII-free summary of the order for status checks.
     */
    public OrderSummaryResponse summary(String reference) {
        Order order = orderRepository.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        int stickerCount = orderItemRepository.findAllByOrderId(order.getId()).stream()
                .mapToInt(it -> it.getStickersPerPack() * it.getQuantity())
                .sum();
        return new OrderSummaryResponse(
                order.getReference(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCurrency(),
                stickerCount,
                order.getCreatedAt());
    }

    /**
     * The signed-in owner's purchase history, newest first. An order belongs to
     * the account when it was placed with the account's e-mail (only once that
     * e-mail is verified, so nobody can claim someone else's orders by typing
     * their address at sign-up) or when it contains a sticker the account owns.
     * PII-free: buyer name, mobile, address and notes are never returned.
     */
    public List<MyOrderResponse> listForAccount(java.util.UUID userId) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        String email = user.isEmailVerified() ? user.getEmail() : null;
        List<Order> orders = orderRepository.findForAccount(
                email, userId, org.springframework.data.domain.PageRequest.of(0, HISTORY_LIMIT));
        if (orders.isEmpty()) {
            return List.of();
        }
        List<java.util.UUID> ids = orders.stream().map(Order::getId).toList();
        java.util.Map<java.util.UUID, List<OrderItem>> items = orderItemRepository.findAllByOrderIdIn(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(i -> i.getOrder().getId()));
        java.util.Map<java.util.UUID, Long> activated = new java.util.HashMap<>();
        for (Object[] row : stickerRepository.countBoundByOrderIds(userId, ids)) {
            activated.put((java.util.UUID) row[0], (Long) row[1]);
        }
        return orders.stream().map(o -> {
            List<OrderItem> own = items.getOrDefault(o.getId(), List.of());
            return new MyOrderResponse(
                    o.getReference(), o.getStatus().name(), o.getTotalAmount(), o.getCurrency(), o.getCreatedAt(),
                    own.stream().map(OrderItemView::from).toList(),
                    own.stream().mapToInt(i -> i.getQuantity() * i.getStickersPerPack()).sum(),
                    activated.getOrDefault(o.getId(), 0L).intValue(),
                    o.getStickerStyle().name());
        }).toList();
    }

    // ————————— internal —————————

    /**
     * Sends the customer confirmation only once the order is committed, so a
     * rolled-back checkout can never email a confirmation. Delivery failures
     * are logged without customer data and never fail the order itself.
     */
    private void sendConfirmationAfterCommit(Order order, List<OrderItem> items) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendConfirmation(order, items);
                }
            });
        } else {
            sendConfirmation(order, items);
        }
    }

    private void sendConfirmation(Order order, List<OrderItem> items) {
        try {
            OrderConfirmationEmailComposer.EmailContent email =
                    confirmationEmailComposer.compose(order, items);
            emailSender.sendHtml(order.getEmail(), email.subject(), email.html(), email.text());
        } catch (RuntimeException ex) {
            // No customer data in the log line: only the reference and the
            // exception type, so a broken SMTP setup cannot dump PII.
            log.warn("Order confirmation email could not be sent for {} ({})",
                    order.getReference(), ex.getClass().getSimpleName());
        }
    }

    /**
     * Mints one {@link Sticker} per slot in the order. Each sticker receives
     * a fresh random token and a short activation code. Lookups use their
     * SHA-256 hashes; the raw values are kept only encrypted, for the admin
     * who prints the sticker. Nothing is returned to the buyer.
     */
    private void mintStickers(Order order, List<OrderItemRequest> items) {

        for (OrderItemRequest item : items) {
            int slots = item.stickerPackage().stickersPerPack() * item.quantity();
            for (int i = 0; i < slots; i++) {
                String rawToken = tokenGenerator.generateUrlSafe(properties.qr().tokenBytes());
                String tokenHash = tokenGenerator.sha256(rawToken);

                String activationCode = tokenGenerator.generateActivationCode();
                String activationCodeHash = tokenGenerator.sha256(
                        tokenGenerator.normalizeActivationCode(activationCode));

                Sticker sticker = Sticker.newSticker(order, tokenHash, activationCodeHash);
                sticker.setTokenEnc(cipher.encrypt(rawToken));
                sticker.setActivationCodeEnc(cipher.encrypt(activationCode));
                stickerRepository.save(sticker);
            }
        }
    }

    /**
     * Admin: the stickers of an order with their QR and activation code, to
     * print and ship them. Decrypted on demand, never logged; every read is
     * written to the audit log ({@code ORDER_STICKER_CODES_VIEW}).
     */
    @Transactional
    public List<AdminOrderStickerView> stickerCodes(String reference, AdminActor actor) {
        Order order = orderRepository.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        List<AdminOrderStickerView> out = stickerRepository.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId())
                .stream()
                .map(s -> {
                    if (s.getTokenEnc() == null || s.getActivationCodeEnc() == null) {
                        return new AdminOrderStickerView(s.getId(), s.getStatus().name(), false, null, null, null);
                    }
                    String publicUrl = properties.baseUrl() + "/c/" + cipher.decrypt(s.getTokenEnc());
                    return new AdminOrderStickerView(s.getId(), s.getStatus().name(), true, publicUrl,
                            imageGenerator.pngDataUri(publicUrl, 300), cipher.decrypt(s.getActivationCodeEnc()));
                })
                .toList();
        auditLogService.record("ORDER_STICKER_CODES_VIEW", "ORDER", order.getId(),
                actor.adminId(), actor.ipAddress(), actor.userAgent(),
                java.util.Map.of("reference", order.getReference(), "stickers", out.size()));
        return out;
    }

    private String generateReference() {
        String ref;
        do {
            ref = "CL-" + randomAlphanumeric(REFERENCE_RANDOM_LEN);
        } while (orderRepository.existsByReference(ref));
        return ref;
    }

    private static String randomAlphanumeric(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(REFERENCE_CHARS[SECURE_RANDOM.nextInt(REFERENCE_CHARS.length)]);
        }
        return sb.toString();
    }

    private static BigDecimal computeTotal(List<OrderItemRequest> items) {
        BigDecimal total = DELIVERY_FEE;
        for (OrderItemRequest it : items) {
            total = total.add(it.stickerPackage().packPrice().multiply(BigDecimal.valueOf(it.quantity())));
        }
        return total;
    }

    private static OrderItem buildItem(Order order, OrderItemRequest it) {
        return OrderItem.builder()
                .order(order)
                .stickerPackage(it.stickerPackage())
                .quantity(it.quantity())
                .unitPrice(it.stickerPackage().packPrice())
                .stickersPerPack(it.stickerPackage().stickersPerPack())
                .subtotal(it.stickerPackage().packPrice().multiply(BigDecimal.valueOf(it.quantity())))
                .build();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    public org.springframework.data.domain.Page<com.carlink.order.dto.AdminOrderView> listOrders(String q, String status, org.springframework.data.domain.Pageable pageable) {
        OrderStatus statusFilter = null;
        if (status != null && !status.isBlank()) {
            try {
                statusFilter = OrderStatus.valueOf(status.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new com.carlink.common.exception.BadRequestException(
                        "Unknown status '" + status + "' (expected PLACED, DELIVERED or CANCELLED)");
            }
        }
        String term = q == null || q.isBlank() ? null : q.trim();
        org.springframework.data.domain.Page<Order> orders = orderRepository.adminSearch(term, statusFilter, pageable);

        // One query for the items of the whole page instead of two per order.
        java.util.Map<java.util.UUID, java.util.List<com.carlink.order.model.OrderItem>> items =
                orderItemRepository.findAllByOrderIdIn(orders.map(Order::getId).getContent()).stream()
                        .collect(java.util.stream.Collectors.groupingBy(i -> i.getOrder().getId()));
        return orders.map(o -> {
            java.util.List<com.carlink.order.model.OrderItem> own = items.getOrDefault(o.getId(), java.util.List.of());
            return new com.carlink.order.dto.AdminOrderView(
                    o.getReference(), o.getStatus().name(), o.getTotalAmount(), o.getCurrency(),
                    own.stream().mapToInt(i -> i.getQuantity() * i.getStickersPerPack()).sum(),
                    o.getCustomerName(), o.getEmail(), o.getMobile(), o.getGovernorate().name(), o.getDeliveryAddress(), o.getDeliveryNotes(), o.getCreatedAt(),
                    own.stream().map(com.carlink.order.dto.OrderItemView::from).toList(),
                    o.getStickerStyle().name());
        });
    }

    @org.springframework.transaction.annotation.Transactional
    public void updateStatus(String reference, OrderStatus status) {
        Order order = orderRepository.findByReference(reference)
                .orElseThrow(() -> new NotFoundException("Order not found"));
        order.setStatus(status);
        orderRepository.save(order);
    }
}