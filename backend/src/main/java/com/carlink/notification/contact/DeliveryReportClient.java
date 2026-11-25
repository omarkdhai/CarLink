package com.carlink.notification.contact;

import java.util.Optional;

/**
 * Asks the SMS provider what became of an accepted message. Implemented by
 * providers that publish delivery reports (TunisieSMS); without a bean, an
 * accepted SMS simply stays SENT.
 */
public interface DeliveryReportClient {

    /** The provider whose message ids this client can look up. */
    String provider();

    /**
     * @return empty while the operator has not reported yet (or the provider
     *         could not be asked); otherwise the final outcome.
     */
    Optional<DeliveryReport> report(String providerMessageId);

    /** Final delivery outcome; {@code code} is the provider's status, e.g. DELIVRD or REJECTED. */
    record DeliveryReport(boolean delivered, String code) {
    }
}
