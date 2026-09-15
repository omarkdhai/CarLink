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
import com.carlink.order.dto.StickerIssuedResponse;
import com.carlink.order.model.Order;
import com.carlink.order.model.OrderItem;
import com.carlink.order.model.OrderStatus;
import com.carlink.order.repository.OrderItemRepository;
import com.carlink.order.repository.OrderRepository;
import com.carlink.qr.service.QrImageGenerator;
import com.carlink.sticker.model.Sticker;
import com.carlink.sticker.repository.StickerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Order lifecycle for the guest COD checkout. All pricing, totals, and
 * sticker counts are computed exclusively server-side; the client only
 * submits package types and quantities.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private static final int MAX_TOTAL_STICKERS = 100;
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

    /**
     * Creates a new COD order. Returns the full response with sticker tokens
     * (shown exactly once); re-fetching by reference returns the token-free
     * summary.
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

        List<StickerIssuedResponse> stickers = mintStickers(saved, request.items());

        return new OrderResponse(
                order.getReference(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getCreatedAt(),
                items.stream().map(OrderItemView::from).toList(),
                stickers);
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

    // ————————— internal —————————

    /**
     * Mints one {@link Sticker} per slot in the order. Each sticker receives
     * a fresh random token whose raw value is returned once and whose SHA-256
     * hash is stored.
     */
    private List<StickerIssuedResponse> mintStickers(
            Order order, List<OrderItemRequest> items) {

        List<com.carlink.order.dto.StickerIssuedResponse> out = new ArrayList<>();
        for (OrderItemRequest item : items) {
            int slots = item.stickerPackage().stickersPerPack() * item.quantity();
            for (int i = 0; i < slots; i++) {
                String rawToken = tokenGenerator.generateUrlSafe(properties.qr().tokenBytes());
                String tokenHash = tokenGenerator.sha256(rawToken);
                String publicUrl = properties.baseUrl() + "/c/" + rawToken;

                stickerRepository.save(Sticker.newSticker(order, tokenHash));
                out.add(new StickerIssuedResponse(
                        rawToken, publicUrl, imageGenerator.pngDataUri(publicUrl, 300)));
            }
        }
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
        BigDecimal total = BigDecimal.ZERO;
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
}
