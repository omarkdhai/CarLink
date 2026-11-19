package com.carlink.notification.email;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security plan S-16: the mock email sender must never log a body — bodies
 * carry single-use verification / reset links — nor the full recipient.
 */
class LogEmailSenderTest {

    private final LogEmailSender sender = new LogEmailSender();
    private ListAppender<ILoggingEvent> appender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(LogEmailSender.class);
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
    void plainTextBodyAndTokenLinkAreNeverLogged() {
        String body = "Hello Ali,\nConfirm: http://localhost/verify-email?token=SECRETtoken123";
        sender.send("ali.ben@example.com", "Verify", body);

        String log = appender.list.get(0).getFormattedMessage();
        assertThat(log).doesNotContain("token=").doesNotContain("SECRETtoken123").doesNotContain("Hello Ali");
        assertThat(log).doesNotContain("ali.ben@example.com").contains("a***@example.com");
        assertThat(log).contains(body.length() + " chars (not logged)");
    }

    @Test
    void htmlBodyIsNeverLogged() {
        sender.sendHtml("ali.ben@example.com", "Reset",
                "<a href=\"http://localhost/reset-password?token=SECRET\">reset</a>", "text token=SECRET");

        String log = appender.list.get(0).getFormattedMessage();
        assertThat(log).doesNotContain("token=").doesNotContain("SECRET");
        assertThat(log).doesNotContain("ali.ben@example.com");
    }

    @Test
    void maskHandlesOddInput() {
        assertThat(LogEmailSender.mask(null)).isEmpty();
        assertThat(LogEmailSender.mask("no-at-sign")).isEqualTo("***");
        assertThat(LogEmailSender.mask("@example.com")).isEqualTo("***");
    }
}
