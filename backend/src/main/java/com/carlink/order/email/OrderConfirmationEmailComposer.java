package com.carlink.order.email;

import com.carlink.order.model.Governorate;
import com.carlink.order.model.Order;
import com.carlink.order.model.OrderItem;
import com.carlink.order.model.StickerPackage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Renders the customer-facing order confirmation. Pure formatting: no I/O and
 * no persistence, so it can be unit-tested and previewed without an SMTP
 * server. Raw QR tokens are deliberately absent — they are returned exactly
 * once by the checkout API and must never be re-sent or embedded in email.
 */
@Component
public class OrderConfirmationEmailComposer {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
                    .withZone(ZoneId.of("Africa/Tunis"));

    private static final String INK = "#0f172a";
    private static final String SOFT = "#64748b";
    private static final String BORDER = "#e2e8f0";

    public EmailContent compose(Order order, List<OrderItem> items) {
        String reference = order.getReference();
        String name = order.getCustomerName();
        String subject = "Your CarLink order " + reference + " is confirmed";
        String preheader = "Order " + reference + " is confirmed — here are your details and delivery information.";

        BigDecimal itemsSubtotal = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal delivery = order.getTotalAmount().subtract(itemsSubtotal);

        String html = htmlTemplate()
                .replace("{{PREHEADER}}", escape(preheader))
                .replace("{{NAME}}", escape(name))
                .replace("{{REFERENCE}}", escape(reference))
                .replace("{{DATE}}", escape(DATE_FORMAT.format(order.getCreatedAt())))
                .replace("{{ITEMS}}", itemRows(items, order.getCurrency()))
                .replace("{{SUBTOTAL}}", money(itemsSubtotal, order.getCurrency()))
                .replace("{{DELIVERY}}", money(delivery, order.getCurrency()))
                .replace("{{TOTAL}}", money(order.getTotalAmount(), order.getCurrency()))
                .replace("{{DELIVERY_ROWS}}", deliveryRows(order))
                .replace("{{TITLE}}", escape(subject));

        return new EmailContent(subject, html, textBody(order, items, itemsSubtotal, delivery));
    }

    // ————————— HTML parts —————————

    private static String itemRows(List<OrderItem> items, String currency) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            OrderItem item = items.get(i);
            boolean last = i == items.size() - 1;
            int stickerCount = item.getStickersPerPack() * item.getQuantity();
            rows.append("""
                    <tr>
                      <td style="padding:14px 0;font-size:14px;font-weight:700;line-height:20px;color:%s;%s">
                        %s
                        <div style="margin-top:3px;font-size:12px;font-weight:400;line-height:17px;color:%s;">%d sticker%s &middot; %s each</div>
                      </td>
                      <td align="right" valign="top" style="padding:14px 0 14px 10px;font-size:13px;line-height:20px;color:%s;white-space:nowrap;">&times; %d</td>
                      <td align="right" valign="top" style="padding:14px 0 14px 10px;font-size:14px;font-weight:700;line-height:20px;color:%s;white-space:nowrap;">%s</td>
                    </tr>
                    """.formatted(
                    INK,
                    last ? "" : "border-bottom:1px solid " + BORDER + ";",
                    escape(packageLabel(item.getStickerPackage())),
                    SOFT,
                    stickerCount,
                    stickerCount == 1 ? "" : "s",
                    money(item.getUnitPrice(), currency),
                    SOFT,
                    item.getQuantity(),
                    INK,
                    money(item.getSubtotal(), currency)));
        }
        return rows.toString();
    }

    private static String deliveryRows(Order order) {
        StringBuilder rows = new StringBuilder();
        rows.append(deliveryRow("Name", escape(order.getCustomerName()), false));
        rows.append(deliveryRow("Phone", escape(formatMobile(order.getMobile())), false));
        rows.append(deliveryRow("Address", escape(order.getDeliveryAddress()).replace("\n", "<br>"), false));
        rows.append(deliveryRow("Governorate", escape(governorateLabel(order.getGovernorate())), false));
        String notes = order.getDeliveryNotes();
        if (notes != null && !notes.isBlank()) {
            rows.append(deliveryRow("Notes", escape(notes).replace("\n", "<br>"), false));
        }
        rows.append(deliveryRow("Payment", "Cash on delivery", true));
        return rows.toString();
    }

    private static String deliveryRow(String label, String value, boolean last) {
        return """
                <tr>
                  <td width="112" valign="top" style="padding:12px 12px 12px 18px;font-size:12px;font-weight:600;line-height:20px;color:%s;%s">%s</td>
                  <td valign="top" style="padding:12px 18px 12px 0;font-size:14px;line-height:20px;color:%s;%s">%s</td>
                </tr>
                """.formatted(
                SOFT,
                last ? "" : "border-bottom:1px solid " + BORDER + ";",
                label,
                INK,
                last ? "" : "border-bottom:1px solid " + BORDER + ";",
                value);
    }

    private static String htmlTemplate() {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <meta name="color-scheme" content="light">
                  <title>{{TITLE}}</title>
                  <style>
                    body,table,td,a{-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%}
                    table,td{mso-table-lspace:0;mso-table-rspace:0}
                    body{margin:0;padding:0;width:100%!important;background:#f1f5f9}
                    @media only screen and (max-width:620px){
                      .cl-shell{padding:20px 10px!important}
                      .cl-pad{padding-left:22px!important;padding-right:22px!important}
                      .cl-title{font-size:26px!important;line-height:1.15!important}
                      .cl-ref{font-size:18px!important}
                    }
                  </style>
                </head>
                <body style="margin:0;padding:0;background:#f1f5f9;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">{{PREHEADER}}</div>
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" class="cl-shell" style="background:#f1f5f9;padding:36px 14px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="600" cellpadding="0" cellspacing="0" border="0" style="width:100%;max-width:600px;background:#ffffff;border:1px solid #e2e8f0;border-radius:20px;overflow:hidden;font-family:'Bricolage Grotesque','Segoe UI',Arial,sans-serif;color:#0f172a;">
                          <tr>
                            <td style="background:#2563eb;padding:22px 32px;" class="cl-pad">
                              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                                <tr>
                                  <td align="left" style="font-size:22px;line-height:26px;font-weight:800;letter-spacing:-.02em;color:#ffffff;">Car<span style="color:#bfdbfe;">Link</span></td>
                                  <td align="right" style="font-size:10px;line-height:14px;font-weight:700;letter-spacing:.16em;text-transform:uppercase;color:#dbeafe;">Order confirmation</td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td align="center" style="padding:38px 32px 0;" class="cl-pad">
                              <table role="presentation" cellpadding="0" cellspacing="0" border="0">
                                <tr>
                                  <td align="center" valign="middle" width="64" height="64" style="width:64px;height:64px;background:#ecfdf5;border-radius:32px;font-family:Arial,sans-serif;font-size:30px;line-height:64px;color:#059669;">&#10003;</td>
                                </tr>
                              </table>
                              <h1 class="cl-title" style="margin:20px 0 0;font-size:30px;line-height:36px;font-weight:800;letter-spacing:-.025em;color:#0f172a;">Your order is confirmed!</h1>
                              <p style="margin:14px 0 0;font-family:'Segoe UI',Arial,sans-serif;font-size:16px;line-height:26px;color:#475569;">Thanks, <strong style="color:#0f172a;">{{NAME}}</strong>. We&#8217;ve received your order and will contact you soon to arrange delivery.</p>
                            </td>
                          </tr>
                          <tr>
                            <td align="center" style="padding:24px 32px 0;" class="cl-pad">
                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="background:#eff6ff;border:1px solid #dbeafe;border-radius:14px;">
                                <tr>
                                  <td align="center" style="padding:12px 28px;">
                                    <div style="font-size:10px;line-height:14px;font-weight:700;letter-spacing:.16em;text-transform:uppercase;color:#2563eb;">Order</div>
                                    <div class="cl-ref" style="margin-top:3px;font-size:20px;line-height:26px;font-weight:800;letter-spacing:.02em;color:#0f172a;">{{REFERENCE}}</div>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:10px 0 0;font-family:'Segoe UI',Arial,sans-serif;font-size:12px;line-height:18px;color:#64748b;">Placed on {{DATE}}</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:30px 32px 0;" class="cl-pad">
                              <p style="margin:0 0 10px;font-family:'Segoe UI',Arial,sans-serif;font-size:11px;line-height:16px;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:#64748b;">Order details</p>
                              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#f8fafc;border:1px solid #e2e8f0;border-radius:14px;">
                                <tr>
                                  <td style="padding:2px 18px 0;">
                                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0">
                                      {{ITEMS}}
                                    </table>
                                  </td>
                                </tr>
                                <tr>
                                  <td style="padding:2px 18px 18px;">
                                    <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="font-family:'Segoe UI',Arial,sans-serif;">
                                      <tr>
                                        <td style="padding:4px 0;font-size:13px;line-height:20px;color:#64748b;">Subtotal</td>
                                        <td align="right" style="padding:4px 0;font-size:13px;line-height:20px;color:#0f172a;">{{SUBTOTAL}}</td>
                                      </tr>
                                      <tr>
                                        <td style="padding:4px 0;font-size:13px;line-height:20px;color:#64748b;">Delivery</td>
                                        <td align="right" style="padding:4px 0;font-size:13px;line-height:20px;color:#0f172a;">{{DELIVERY}}</td>
                                      </tr>
                                      <tr>
                                        <td style="padding:12px 0 0;font-size:15px;line-height:22px;font-weight:800;color:#0f172a;">Total</td>
                                        <td align="right" style="padding:12px 0 0;font-size:18px;line-height:22px;font-weight:800;color:#0f172a;">{{TOTAL}}</td>
                                      </tr>
                                    </table>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:24px 32px 0;" class="cl-pad">
                              <p style="margin:0 0 10px;font-family:'Segoe UI',Arial,sans-serif;font-size:11px;line-height:16px;font-weight:700;letter-spacing:.12em;text-transform:uppercase;color:#64748b;">Delivery details</p>
                              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#ffffff;border:1px solid #e2e8f0;border-radius:14px;font-family:'Segoe UI',Arial,sans-serif;">
                                {{DELIVERY_ROWS}}
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:26px 32px 34px;" class="cl-pad">
                              <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background:#eff6ff;border-radius:14px;">
                                <tr>
                                  <td align="center" style="padding:18px 22px;font-family:'Segoe UI',Arial,sans-serif;font-size:14px;line-height:22px;color:#1e3a8a;"><strong style="color:#1e40af;">Need help?</strong> Reply to this email.</td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:22px 32px 26px;background:#f8fafc;border-top:1px solid #e2e8f0;text-align:center;font-family:'Segoe UI',Arial,sans-serif;">
                              <p style="margin:0;font-size:12px;line-height:18px;font-weight:600;color:#64748b;">CarLink — anonymous contact via QR code</p>
                              <p style="margin:5px 0 0;font-size:11px;line-height:16px;color:#94a3b8;">You received this email because an order was placed on CarLink.</p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """;
    }

    // ————————— plain text —————————

    private static String textBody(Order order, List<OrderItem> items,
                                   BigDecimal itemsSubtotal, BigDecimal delivery) {
        String currency = order.getCurrency();
        StringBuilder text = new StringBuilder();
        text.append("Your order is confirmed!\n\n");
        text.append("Thanks, ").append(order.getCustomerName())
                .append(". We've received your order and will contact you soon to arrange delivery.\n\n");
        text.append("Order: ").append(order.getReference()).append('\n');
        text.append("Placed on ").append(DATE_FORMAT.format(order.getCreatedAt())).append("\n\n");

        text.append("ORDER DETAILS\n");
        for (OrderItem item : items) {
            int stickerCount = item.getStickersPerPack() * item.getQuantity();
            text.append(packageLabel(item.getStickerPackage()))
                    .append(" (").append(stickerCount).append(stickerCount == 1 ? " sticker" : " stickers")
                    .append(") x ").append(item.getQuantity())
                    .append(" at ").append(money(item.getUnitPrice(), currency))
                    .append(" — ").append(money(item.getSubtotal(), currency))
                    .append('\n');
        }
        text.append("Subtotal: ").append(money(itemsSubtotal, currency)).append('\n');
        text.append("Delivery: ").append(money(delivery, currency)).append('\n');
        text.append("Total: ").append(money(order.getTotalAmount(), currency)).append("\n\n");

        text.append("DELIVERY DETAILS\n");
        text.append("Name: ").append(order.getCustomerName()).append('\n');
        text.append("Phone: ").append(formatMobile(order.getMobile())).append('\n');
        text.append("Address: ").append(order.getDeliveryAddress()).append('\n');
        text.append("Governorate: ").append(governorateLabel(order.getGovernorate())).append('\n');
        if (order.getDeliveryNotes() != null && !order.getDeliveryNotes().isBlank()) {
            text.append("Notes: ").append(order.getDeliveryNotes()).append('\n');
        }
        text.append("Payment: Cash on delivery\n\n");
        text.append("Need help? Reply to this email.\n\n");
        text.append("— CarLink\n");
        return text.toString();
    }

    // ————————— helpers —————————

    private static String packageLabel(StickerPackage stickerPackage) {
        return switch (stickerPackage) {
            case SINGLE -> "Single sticker";
            case DOUBLE -> "Two-sticker pack";
            case BUSINESS -> "Business pack";
        };
    }

    private static String governorateLabel(Governorate governorate) {
        String[] words = governorate.name().split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(word.charAt(0)).append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return label.toString();
    }

    /** "+21655123456" → "+216 55 123 456"; other formats are shown unchanged. */
    private static String formatMobile(String mobile) {
        if (mobile != null && mobile.matches("\\+216\\d{8}")) {
            return "+216 " + mobile.substring(4, 6) + " "
                    + mobile.substring(6, 9) + " " + mobile.substring(9);
        }
        return mobile == null ? "" : mobile;
    }

    private static String money(BigDecimal amount, String currency) {
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                + " " + currency;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    public record EmailContent(String subject, String html, String text) {}
}
