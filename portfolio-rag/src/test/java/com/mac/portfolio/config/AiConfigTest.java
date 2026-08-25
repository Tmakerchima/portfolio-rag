package com.mac.portfolio.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiConfigTest {

    @Test
    void acceptsOnlyExplicitCorsOrigins() {
        AiConfig config = new AiConfig("http://localhost:5173, https://example.com, *");

        assertThat(config.allowedOrigins())
                .containsExactly("http://localhost:5173", "https://example.com")
                .doesNotContain("*");
    }

    @Test
    void refusesAnEmptyOrWildcardOnlyCorsConfiguration() {
        assertThatThrownBy(() -> new AiConfig("*"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
