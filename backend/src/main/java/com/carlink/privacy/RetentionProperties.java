package com.carlink.privacy;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Data-retention periods (security plan S-17), bound from {@code carlink.retention.*}.
 * Defaults are deliberately short for an anonymous-contact service: a
 * visitor's alert is only useful for minutes, and nothing here is needed for
 * accounting (orders are kept, see docs/security.md).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "carlink.retention")
public class RetentionProperties {

    /** Off switch, e.g. while restoring a backup. */
    private boolean enabled = true;

    /** Conversations (with their messages) are deleted this many days after creation. */
    private int conversationDays = 90;

    /** Expired, revoked or used tokens are deleted this many days later. */
    private int tokenGraceDays = 7;

    /** IP addresses and user agents in audit logs and reports are erased after this many days. */
    private int ipDays = 90;

    /** Public "Contact us" messages (already e-mailed to the team) are deleted after this many days. */
    private int contactMessageDays = 365;
}
