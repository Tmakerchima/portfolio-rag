package com.mac.portfolio.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PublicKnowledgePrivacyTest {

    @Test
    void publicProfileDoesNotExposeAChineseMobileNumber() throws Exception {
        String profile = new ClassPathResource("knowledge/about-mac.md")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(profile).doesNotContainPattern("(?<!\\d)1[3-9]\\d{9}(?!\\d)");
        assertThat(profile).doesNotContain("联系电话");
    }
}
