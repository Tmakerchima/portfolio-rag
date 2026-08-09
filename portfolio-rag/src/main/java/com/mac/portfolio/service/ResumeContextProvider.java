package com.mac.portfolio.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Loads the small, static resume once; it is intentionally not embedded or chunked. */
@Component
public class ResumeContextProvider {

    private final String content;

    public ResumeContextProvider(@Value("classpath:knowledge/about-mac.md") Resource resource) {
        try {
            String loaded = resource.getContentAsString(StandardCharsets.UTF_8)
                    .replace("\uFEFF", "")
                    .trim();
            if (loaded.isBlank()) throw new IllegalStateException("about-mac.md must not be blank");
            if (loaded.length() > 50_000) {
                throw new IllegalStateException("about-mac.md exceeds the full-context safety limit");
            }
            this.content = loaded;
        } catch (IOException error) {
            throw new IllegalStateException("Unable to load about-mac.md", error);
        }
    }

    public String content() {
        return content;
    }
}
