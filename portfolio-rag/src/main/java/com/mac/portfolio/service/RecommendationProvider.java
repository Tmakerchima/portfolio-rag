package com.mac.portfolio.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class RecommendationProvider {

    private final List<Recommendation> recommendations;

    public RecommendationProvider(
            ObjectMapper objectMapper,
            @Value("${portfolio.recommendations-path:classpath:portfolio-recommendations.json}") Resource resource) {
        try {
            List<Recommendation> loaded;
            try (InputStream input = resource.getInputStream()) {
                loaded = objectMapper.readValue(input, new TypeReference<List<Recommendation>>() {});
            }
            LinkedHashSet<String> uniqueQuestions = new LinkedHashSet<>();
            this.recommendations = loaded.stream()
                    .filter(item -> item.question() != null && !item.question().isBlank())
                    .filter(item -> uniqueQuestions.add(item.question().trim()))
                    .map(item -> new Recommendation(item.question().trim(), item.category()))
                    .limit(5)
                    .toList();
            if (recommendations.size() < 2) {
                throw new IllegalStateException("At least two unique recommended questions are required");
            }
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load recommended questions", error);
        }
    }

    public List<Recommendation> get() {
        return recommendations;
    }

    public record Recommendation(String question, String category) {}
}
