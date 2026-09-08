package com.carlink.notification.contact;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.carlink.common.security.TokenGenerator;
import com.carlink.conversation.model.Channel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dev contact sender logs a would-be delivery so a scan-and-submit can be
 * exercised without a provider — but it must never reveal the owner's phone.
 */
class LogContactChannelSenderTest {

    private LogContactChannelSender sender;
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    private final String ownerPhone = "+21655123456";
    private final String message = "Hi, is this car still available?";

    @BeforeEach
    void setUp() {
        sender = new LogContactChannelSender(new TokenGenerator());
        logger = (Logger) LoggerFactory.getLogger(LogContactChannelSender.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
    }

    @Test
    void sendReturnsTrueAndLogsChannelAndMessage() {
        boolean delivered = sender.send(new ContactDelivery(Channel.WHATSAPP, ownerPhone, message));

        assertThat(delivered).isTrue();
        String log = appender.list.get(0).getFormattedMessage();
        assertThat(log).contains("WHATSAPP");
        assertThat(log).contains(message);
    }

    @Test
    void logNeverContainsTheRawPhoneOnlyItsHash() {
        sender.send(new ContactDelivery(Channel.SMS, ownerPhone, message));

        String log = appender.list.get(0).getFormattedMessage();
        assertThat(log).doesNotContain(ownerPhone);
        // The phone is correlated only through its SHA-256, never raw.
        assertThat(log).contains(new TokenGenerator().sha256(ownerPhone));
    }
}
