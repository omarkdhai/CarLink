package com.carlink.common.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenGeneratorTest {

    private final TokenGenerator generator = new TokenGenerator();

    @Test
    void generatesUrlSafeDistinctTokens() {
        String a = generator.generateUrlSafe(32);
        String b = generator.generateUrlSafe(32);
        assertThat(a).isNotBlank().isNotEqualTo(b);
        assertThat(a).matches("[A-Za-z0-9._~-]+");
    }

    @Test
    void sha256IsStableAndHidesInput() {
        String hash1 = generator.sha256("secret-value");
        String hash2 = generator.sha256("secret-value");
        assertThat(hash1).isEqualTo(hash2);
        assertThat(hash1).hasSize(64).doesNotContain("secret-value");
        assertThat(generator.sha256("other")).isNotEqualTo(hash1);
    }

    @Test
    void producesManyUniqueTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(generator.generateUrlSafe(32));
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void safeEqualsIsConstantTimeSemantics() {
        assertThat(generator.safeEquals("abc", "abc")).isTrue();
        assertThat(generator.safeEquals("abc", "abd")).isFalse();
    }
}