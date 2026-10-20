package com.carlink.order.email;

import com.carlink.order.model.Governorate;
import com.carlink.order.model.Order;
import com.carlink.order.model.OrderItem;
import com.carlink.order.model.OrderStatus;
import com.carlink.order.model.StickerPackage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderConfirmationEmailComposerTest {

    private final OrderConfirmationEmailComposer composer = new OrderConfirmationEmailComposer();

    private Order order(String name, String address, String notes) {
        return Order.builder()
                .id(UUID.randomUUID())
                .reference("CL-AB12CD34")
                .customerName(name)
                .mobile("+21655123456")
                .email("buyer@example.com")
                .governorate(Governorate.LE_KEF)
                .deliveryAddress(address)
                .deliveryNotes(notes)
                .status(OrderStatus.PLACED)
                .totalAmount(new BigDecimal("77.00"))
                .currency("TND")
                .createdAt(Instant.parse("2026-10-20T09:30:00Z"))
                .build();
    }

    private List<OrderItem> items(Order order) {
        return List.of(OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .stickerPackage(StickerPackage.DOUBLE)
                .quantity(2)
                .unitPrice(new BigDecimal("35.00"))
                .stickersPerPack(2)
                .subtotal(new BigDecimal("70.00"))
                .build());
    }

    @Test
    void rendersBrandedConfirmationWithOrderAndDeliveryDetails() {
        Order order = order("Aymen Ben Ali", "12 Rue de Carthage", "Ring the bell");
        OrderConfirmationEmailComposer.EmailContent email = composer.compose(order, items(order));

        assertThat(email.subject()).isEqualTo("Your CarLink order CL-AB12CD34 is confirmed");
        assertThat(email.html())
                .contains("Your order is confirmed!")
                .contains("Thanks, <strong style=\"color:#0f172a;\">Aymen Ben Ali</strong>.")
                .contains("CL-AB12CD34")
                .contains("Two-sticker pack")
                .contains("4 stickers")
                .contains("70.00 TND")
                .contains("77.00 TND")
                .contains("Le Kef")
                .contains("+216 55 123 456")
                .contains("Ring the bell")
                .contains("Cash on delivery")
                .contains("Need help?</strong> Reply to this email.");
        assertThat(email.text())
                .contains("Thanks, Aymen Ben Ali.")
                .contains("Order: CL-AB12CD34")
                .contains("Subtotal: 70.00 TND")
                .contains("Delivery: 7.00 TND")
                .contains("Total: 77.00 TND")
                .contains("Governorate: Le Kef")
                .startsWith("Your order is confirmed!")
                .contains("Need help? Reply to this email.");
    }

    @Test
    void escapesCustomerSuppliedValuesInHtmlButKeepsThemReadableInText() {
        Order order = order("Aymen <script>alert(\"x\")</script>",
                "12 Rue <b>Carthage</b> & Co", null);
        OrderConfirmationEmailComposer.EmailContent email = composer.compose(order, items(order));

        assertThat(email.html())
                .doesNotContain("<script>")
                .doesNotContain("<b>Carthage</b>")
                .contains("&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt;")
                .contains("12 Rue &lt;b&gt;Carthage&lt;/b&gt; &amp; Co")
                .doesNotContain("Notes</td>");
        assertThat(email.text())
                .contains("Aymen <script>alert(\"x\")</script>")
                .contains("12 Rue <b>Carthage</b> & Co");
    }
}
