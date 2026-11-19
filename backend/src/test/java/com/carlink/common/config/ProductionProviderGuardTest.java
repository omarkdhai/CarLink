package com.carlink.common.config;

import com.carlink.TestProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Security plan S-16: prod refuses the mock email sender, tolerates the mock relay. */
class ProductionProviderGuardTest {

    @Test
    void refusesMockEmailInProduction() {
        CarLinkProperties props = TestProperties.minimal(); // email + contact = mock
        assertThatThrownBy(() -> new ProductionProviderGuard(props, new com.carlink.security.captcha.CaptchaProperties()).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("EMAIL_PROVIDER=mock");
    }

    @Test
    void acceptsSmtpEmailWithMockRelay() {
        CarLinkProperties base = TestProperties.minimal();
        CarLinkProperties props = new CarLinkProperties(
                base.baseUrl(), base.publicUrl(), base.jwt(), base.conversation(), base.qr(),
                base.ratelimit(), base.contact(), base.contactForm(),
                new CarLinkProperties.Email("smtp", 60, "noreply@carlink.test", "noreply@carlink.test"),
                base.security(), base.admin(), base.telnyx());
        assertThatCode(() -> new ProductionProviderGuard(props, new com.carlink.security.captcha.CaptchaProperties()).check()).doesNotThrowAnyException();
    }

    @Test
    void refusesTheNeutralPlaceholderSender() {
        // S-22: the committed default is no-reply@carlink.invalid; prod must set MAIL_FROM.
        CarLinkProperties base = TestProperties.minimal();
        CarLinkProperties props = new CarLinkProperties(
                base.baseUrl(), base.publicUrl(), base.jwt(), base.conversation(), base.qr(),
                base.ratelimit(), base.contact(), base.contactForm(),
                new CarLinkProperties.Email("smtp", 60, "no-reply@carlink.invalid", "no-reply@carlink.invalid"),
                base.security(), base.admin(), base.telnyx());
        assertThatThrownBy(() -> new ProductionProviderGuard(props, new com.carlink.security.captcha.CaptchaProperties()).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_FROM");
    }
}
