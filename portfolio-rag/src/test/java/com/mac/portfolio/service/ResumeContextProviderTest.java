package com.mac.portfolio.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeContextProviderTest {

    @Test
    void loadsTheCompleteResumeAsOneContext() {
        ResumeContextProvider provider = new ResumeContextProvider(
                new ClassPathResource("knowledge/about-mac.md"));

        assertThat(provider.content()).isNotBlank();
        assertThat(provider.content()).contains("马驰", "Java");
    }
}
