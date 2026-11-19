package com.carlink.security.captcha;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Security plan S-11: Turnstile verification, failing closed. */
class CaptchaVerifierTest {

    private static final String URL = "https://challenges.example/siteverify";

    private CaptchaProperties props;
    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        props = new CaptchaProperties();
        props.setEnabled(true);
        props.setSecretKey("server-secret");
        props.setVerifyUrl(URL);
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    @Test
    void acceptsATokenTurnstileConfirms() {
        server.expect(requestTo(URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("secret=server-secret")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("response=tok")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("remoteip=203.0.113.9")))
                .andRespond(withSuccess("{\"success\":true}", MediaType.APPLICATION_JSON));

        assertThat(new CaptchaVerifier(props, builder).verify("tok", "203.0.113.9")).isTrue();
        server.verify();
    }

    @Test
    void rejectsATokenTurnstileRefuses() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}", MediaType.APPLICATION_JSON));
        assertThat(new CaptchaVerifier(props, builder).verify("tok", "203.0.113.9")).isFalse();
    }

    @Test
    void failsClosedWhenTurnstileIsDown() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThat(new CaptchaVerifier(props, builder).verify("tok", "203.0.113.9")).isFalse();
    }

    @Test
    void rejectsMissingTokenWithoutCallingOut() {
        CaptchaVerifier verifier = new CaptchaVerifier(props, builder);
        assertThat(verifier.verify(null, "203.0.113.9")).isFalse();
        assertThat(verifier.verify("  ", "203.0.113.9")).isFalse();
        server.verify(); // no request was expected or made
    }

    @Test
    void passesEverythingWhenDisabled() {
        props.setEnabled(false);
        assertThat(new CaptchaVerifier(props, builder).verify(null, null)).isTrue();
        server.verify();
    }
}
