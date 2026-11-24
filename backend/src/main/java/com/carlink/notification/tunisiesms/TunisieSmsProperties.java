package com.carlink.notification.tunisiesms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TunisieSMS (L2T, "URL2SMS" API) — the SMS gateway for owners in Tunisia.
 * Only read when {@code carlink.contact.provider=tunisiesms}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "carlink.tunisiesms")
public class TunisieSmsProperties {

    /** Account API key (TunisieSMS client area). Never logged or echoed. */
    private String apiKey = "";
    /** Sender name authorized on the account (fct=sender lists them). */
    private String sender = "CarLink";
    /** Single endpoint for every function (fct=sms, dlr, balance, sender). */
    private String url = "https://api.l2t.io/tn/v0/api/api.aspx";
    private int connectTimeoutMs = 5_000;
    private int readTimeoutMs = 10_000;
}
