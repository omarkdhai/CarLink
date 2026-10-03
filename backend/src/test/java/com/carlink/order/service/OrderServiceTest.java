package com.carlink.order.service;

import com.carlink.TestProperties;
import com.carlink.common.config.CarLinkProperties;
import com.carlink.common.exception.BadRequestException;
import com.carlink.common.exception.NotFoundException;
import com.carlink.common.security.TokenGenerator;
import com.carlink.order.dto.OrderCreateRequest;
import com.carlink.order.dto.OrderItemRequest;
import com.carlink.order.dto.OrderResponse;
import com.carlink.order.dto.OrderSummaryResponse;
import com.carlink.order.model.Governorate;
import com.carlink.order.model.Order;
import com.carlink.order.model.OrderItem;
import com.carlink.order.model.OrderStatus;
import com.carlink.order.model.StickerPackage;
import com.carlink.order.repository.OrderItemRepository;
import com.carlink.order.repository.OrderRepository;
import com.carlink.qr.service.QrImageGenerator;
import com.carlink.sticker.model.Sticker;
import com.carlink.sticker.repository.StickerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private StickerRepository stickerRepository;
    @Mock private QrImageGenerator imageGenerator;

    private final TokenGenerator tokenGenerator = new TokenGenerator();
    private final CarLinkProperties properties = TestProperties.minimal();
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, orderItemRepository,
                stickerRepository, tokenGenerator, imageGenerator, properties);
    }

    private OrderCreateRequest orderRequest(List<OrderItemRequest> items) {
        return new OrderCreateRequest(
                items,
                "Aymen Ben Ali",
                "+21655123456",
                "buyer@example.com",
                Governorate.TUNIS,
                "12 Rue de Carthage",
                "Ring the bell");
    }

    private void stubPersistSucceeds() {
        when(orderRepository.existsByReference(anyString())).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stickerRepository.save(any(Sticker.class))).thenAnswer(inv -> inv.getArgument(0));
        when(imageGenerator.pngDataUri(anyString(), anyInt())).thenReturn("data:image/png;base64,QR");
    }

    // ---------- create ----------

    @Test
    void createUsesServerSidePricesAndMintsExactNumberOfStickers() {
        stubPersistSucceeds();
        // DOUBLE packs ×2 = 4 physical stickers, 35.00 × 2 = 70.00 TND.
        OrderCreateRequest request = orderRequest(
                List.of(new OrderItemRequest(StickerPackage.DOUBLE, 2)));

        OrderResponse response = orderService.create(request);

        assertThat(response.reference()).startsWith("CL-");
        assertThat(response.totalAmount()).isEqualByComparingTo("70.00");
        assertThat(response.currency()).isEqualTo("TND");
        assertThat(response.status()).isEqualTo("PLACED");
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("35.00");
        assertThat(response.stickers()).hasSize(4);
        response.stickers().forEach(s -> {
            assertThat(s.rawToken()).isNotBlank();
            assertThat(s.publicUrl()).startsWith("http://localhost:8080/c/");
            assertThat(s.imageDataUri()).isEqualTo("data:image/png;base64,QR");
        });

        // Only SHA-256 hashes are stored — the raw tokens never reach the repo.
        ArgumentCaptor<Sticker> saved = ArgumentCaptor.forClass(Sticker.class);
        verify(stickerRepository, times(4)).save(saved.capture());
        saved.getAllValues().forEach(sticker -> {
            assertThat(sticker.getTokenHash()).hasSize(64);
            assertThat(sticker.getTokenHash()).matches("[0-9a-f]{64}");
        });
    }

    @Test
    void createRejectsOrdersAboveStickerCap() {
        OrderCreateRequest request = orderRequest(
                List.of(new OrderItemRequest(StickerPackage.BUSINESS, 6))); // 120 stickers

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum");
        verify(orderRepository, never()).save(any());
        verify(stickerRepository, never()).save(any());
    }

    @Test
    void createRetriesReferenceWhileItCollides() {
        // First generated reference collides, the retry succeeds.
        when(orderRepository.existsByReference(anyString())).thenReturn(true).thenReturn(false);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(orderItemRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(stickerRepository.save(any(Sticker.class))).thenAnswer(inv -> inv.getArgument(0));
        when(imageGenerator.pngDataUri(anyString(), anyInt())).thenReturn("data:img");

        OrderResponse response = orderService.create(orderRequest(
                List.of(new OrderItemRequest(StickerPackage.SINGLE, 1))));

        assertThat(response.reference()).matches("CL-[A-Z0-9]{8}");
        verify(orderRepository, times(2)).existsByReference(anyString());
    }

    @Test
    void createNormalizesEmailAndTrimsOptionalNotes() {
        stubPersistSucceeds();
        OrderCreateRequest request = new OrderCreateRequest(
                List.of(new OrderItemRequest(StickerPackage.SINGLE, 1)),
                "  Aymen Ben Ali  ",
                "+21655123456",
                "  Buyer@Example.COM ",
                Governorate.SFAX,
                "  Avenue Habib Bourguiba  ",
                "   ");

        OrderResponse response = orderService.create(request);

        // Captured order carries normalized values.
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getEmail()).isEqualTo("buyer@example.com");
        assertThat(orderCaptor.getValue().getDeliveryNotes()).isNull();
        assertThat(orderCaptor.getValue().getGovernorate()).isEqualTo(Governorate.SFAX);
        assertThat(response.totalAmount()).isEqualByComparingTo("20.00");
    }

    // ---------- summary ----------

    @Test
    void summaryReturnsTokenFreePiiFreeViewAndStickerCount() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .reference("CL-ABC12345")
                .customerName("Aymen Ben Ali")
                .mobile("+21655123456")
                .email("buyer@example.com")
                .governorate(Governorate.TUNIS)
                .deliveryAddress("12 Rue de Carthage")
                .status(OrderStatus.PLACED)
                .totalAmount(new BigDecimal("20.00"))
                .currency("TND")
                .createdAt(Instant.now())
                .build();
        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .stickerPackage(StickerPackage.SINGLE)
                .quantity(1)
                .unitPrice(new BigDecimal("20.00"))
                .stickersPerPack(1)
                .subtotal(new BigDecimal("20.00"))
                .build();

        when(orderRepository.findByReference("CL-ABC12345")).thenReturn(Optional.of(order));
        when(orderItemRepository.findAllByOrderId(order.getId())).thenReturn(List.of(item));

        OrderSummaryResponse summary = orderService.summary("CL-ABC12345");

        assertThat(summary.reference()).isEqualTo("CL-ABC12345");
        assertThat(summary.stickerCount()).isEqualTo(1);
        assertThat(summary.status()).isEqualTo("PLACED");
        assertThat(summary.totalAmount()).isEqualByComparingTo("20.00");
        // No PII, no sticker tokens can leak through the summary.
        assertThat(summary.toString())
                .doesNotContain("Aymen")
                .doesNotContain("+216")
                .doesNotContain("Carthage")
                .doesNotContain("rawToken");
    }

    @Test
    void summaryUnknownReferenceIs404() {
        when(orderRepository.findByReference("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.summary("NOPE"))
                .isInstanceOf(NotFoundException.class);
    }
}