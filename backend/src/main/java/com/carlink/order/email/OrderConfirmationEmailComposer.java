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
                    /* Progressive enhancement: clients that support @import
                       (Apple Mail, iOS, most webmail) render the brand face;
                       others fall back to the system stack below. */
                    @import url('https://fonts.googleapis.com/css2?family=Bricolage+Grotesque:opsz,wght@12..96,400..800&display=swap');
                    body,table,td,a{-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%}
                    table,td{mso-table-lspace:0;mso-table-rspace:0}
                    body{margin:0;padding:0;width:100%!important;background:#f1f5f9}
                    .cl-display{font-family:'Bricolage Grotesque','Segoe UI',Arial,sans-serif}
                    @media only screen and (max-width:620px){
                      .cl-shell{padding:20px 10px!important}
                      .cl-pad{padding-left:22px!important;padding-right:22px!important}
                      .cl-title{font-size:26px!important;line-height:1.15!important}
                      .cl-ref{font-size:18px!important}
                      .cl-sec{font-size:10px!important}
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
                                  <td align="left" valign="middle" style="font-family:'Bricolage Grotesque','Segoe UI',Arial,sans-serif;font-size:22px;line-height:26px;font-weight:800;letter-spacing:-.02em;color:#ffffff;white-space:nowrap;">
                                    <span style="display:inline-block;vertical-align:middle;margin-right:8px;"><svg xmlns="http://www.w3.org/2000/svg" viewBox="176 148 258 168" width="26" height="17" style="display:block;"><path d="M0 0 C1.85 0.02 3.71 0.04 5.56 0.05 C10.04 0.1 14.52 0.17 19 0.25 C16.52 2.83 14.02 4.54 10.94 6.38 C0.99 12.64 -9.65 21.55 -15 32.25 C-16.86 32.67 -16.86 32.67 -19.31 33 C-30.65 35.1 -40.86 41.34 -47.75 50.6 C-55.45 62.72 -57.86 74.67 -55.38 88.94 C-51.99 101.42 -44.93 111.54 -33.88 118.19 C-28.83 121.02 -24.22 123.08 -18.41 123.52 C-13.9 124.49 -12.96 126.16 -10.44 129.94 C-7.24 134.54 -4.11 138.46 0 142.25 C0.66 137.4 1.13 133.06 -1.54 128.79 C-2.93 127.17 -4.34 125.56 -5.77 123.97 C-18.41 108.54 -21.14 88.47 -20 69.25 C-17.78 47.93 -6.83 30.54 9.06 16.62 C13.38 13.23 17.83 10.46 22.69 7.94 C23.27 7.62 23.86 7.31 24.46 6.99 C37.79 0.45 51.63 -0.4 66.18 -0.27 C69.06 -0.25 71.94 -0.27 74.82 -0.3 C89.15 -0.32 101.72 1.58 114.75 7.94 C115.46 8.28 116.17 8.62 116.89 8.97 C123.36 12.2 128.72 16.32 134 21.25 C134.63 21.79 135.26 22.33 135.91 22.88 C149.17 34.88 156.96 53.96 158.15 71.5 C159.08 93.68 153.39 113.36 138.75 130.38 C131.13 138.44 123.58 144.3 113.62 149.12 C112.87 149.49 112.12 149.86 111.34 150.24 C97.86 156.45 84.72 156.64 70.19 156.44 C68.32 156.42 66.45 156.41 64.58 156.4 C60.06 156.36 55.53 156.31 51 156.25 C53.6 153.55 56.31 151.67 59.5 149.69 C62.36 147.9 65.2 146.12 68 144.25 C68.36 141.58 68.36 141.58 68.19 138.62 C68.16 137.62 68.13 136.62 68.11 135.59 C68.07 134.82 68.04 134.04 68 133.25 C67.47 133.76 66.93 134.27 66.38 134.79 C48.26 151.53 28.12 156.8 4.1 156.7 C1.2 156.69 -1.69 156.72 -4.59 156.75 C-18.89 156.8 -32.46 155.43 -45.31 148.56 C-45.97 148.22 -46.62 147.88 -47.3 147.53 C-53.51 144.2 -58.82 140.02 -64 135.25 C-64.63 134.71 -65.26 134.17 -65.91 133.62 C-78.87 121.87 -87.1 102.53 -88.12 85.27 C-89.12 62.98 -83.54 43.24 -68.75 26.12 C-61.76 18.7 -54.37 12.64 -45.31 7.94 C-44.73 7.62 -44.14 7.31 -43.54 6.99 C-29.56 0.13 -15.24 -0.29 0 0 Z" fill="#ffffff" transform="translate(270,153.75) scale(0.18)"/></svg></span>
                                    Car<span style="color:#bfdbfe;">Link</span>
                                  </td>
                                  <td align="right" valign="middle" style="font-family:'Bricolage Grotesque','Segoe UI',Arial,sans-serif;font-size:10px;line-height:14px;font-weight:700;letter-spacing:.16em;text-transform:uppercase;color:#dbeafe;white-space:nowrap;">Order confirmation</td>
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
                              <h1 class="cl-title cl-display" style="margin:20px 0 0;font-size:30px;line-height:36px;font-weight:800;letter-spacing:-.025em;color:#0f172a;">Your order is confirmed!</h1>
                              <p style="margin:14px 0 0;font-family:'Segoe UI',Arial,sans-serif;font-size:16px;line-height:26px;color:#475569;">Thanks, <strong style="color:#0f172a;">{{NAME}}</strong>. We&#8217;ve received your order and will contact you soon to arrange delivery.</p>
                            </td>
                          </tr>
                          <tr>
                            <td align="center" style="padding:24px 32px 0;" class="cl-pad">
                              <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="background:#eff6ff;border:1px solid #dbeafe;border-radius:14px;">
                                <tr>
                                  <td align="center" style="padding:12px 28px;">
                                    <div class="cl-sec" style="font-family:'Bricolage Grotesque','Segoe UI',Arial,sans-serif;font-size:10px;line-height:14px;font-weight:800;letter-spacing:.16em;text-transform:uppercase;color:#2563eb;">Order</div>
                                    <div class="cl-ref cl-display" style="margin-top:3px;font-size:20px;line-height:26px;font-weight:800;letter-spacing:.02em;color:#0f172a;">{{REFERENCE}}</div>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:10px 0 0;font-family:'Segoe UI',Arial,sans-serif;font-size:12px;line-height:18px;color:#64748b;">Placed on {{DATE}}</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:30px 32px 0;" class="cl-pad">
                              <p class="cl-sec" style="margin:0 0 10px;font-family:'Bricolage Grotesque','Segoe UI',Arial,sans-serif;font-size:11px;line-height:16px;font-weight:800;letter-spacing:.12em;text-transform:uppercase;color:#64748b;">Order details</p>
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
                              <p class="cl-sec" style="margin:0 0 10px;font-family:'Bricolage Grotesque','Segoe UI',Arial,sans-serif;font-size:11px;line-height:16px;font-weight:800;letter-spacing:.12em;text-transform:uppercase;color:#64748b;">Delivery details</p>
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
