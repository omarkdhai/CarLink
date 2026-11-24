package com.carlink.notification.contact;

/**
 * What a {@link ContactChannelSender} knows right after handing an alert over.
 *
 * <ul>
 *   <li>{@code DELIVERED}: delivery is certain (only the dev mock can say so).</li>
 *   <li>{@code ACCEPTED}: the provider took the message; whether the phone gets
 *       it is only known from the delivery report ({@link DeliveryReportClient}).</li>
 *   <li>{@code FAILED}: refused; {@code error} is a short code, never the
 *       phone number or the message.</li>
 * </ul>
 */
public record SendResult(Outcome outcome, String providerMessageId, String error) {

    public enum Outcome { DELIVERED, ACCEPTED, FAILED }

    public static SendResult delivered() {
        return new SendResult(Outcome.DELIVERED, null, null);
    }

    public static SendResult accepted(String providerMessageId) {
        return new SendResult(Outcome.ACCEPTED, providerMessageId, null);
    }

    public static SendResult failed(String error) {
        return new SendResult(Outcome.FAILED, null, error);
    }
}
