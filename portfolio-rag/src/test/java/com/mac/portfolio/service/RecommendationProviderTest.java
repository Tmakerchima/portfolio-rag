package com.mac.portfolio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationProviderTest {

    @Test
    void loadsSmallUniqueQuestionSetFromConfiguration() {
        RecommendationProvider provider = new RecommendationProvider(
                new ObjectMapper(), new ClassPathResource("portfolio-recommendations.json"));

        assertThat(provider.get()).hasSizeBetween(2, 5);
        assertThat(provider.get()).extracting(RecommendationProvider.Recommendation::question)
                .doesNotHaveDuplicates()
                .anyMatch(question -> question.contains("GitHub"));
    }
}
