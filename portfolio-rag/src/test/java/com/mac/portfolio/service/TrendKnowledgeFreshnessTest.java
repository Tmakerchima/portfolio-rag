package com.mac.portfolio.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TrendKnowledgeFreshnessTest {

    @Test
    void trendKnowledgeUsesARecentSnapshotAndExplicitSelectionSignals() throws Exception {
        String markdown = new ClassPathResource("knowledge/github-trend.md")
                .getContentAsString(StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("(?m)^snapshot_date:\\s*(\\d{4}-\\d{2}-\\d{2})$")
                .matcher(markdown);

        assertThat(matcher.find()).isTrue();
        LocalDate snapshotDate = LocalDate.parse(matcher.group(1));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Taipei"));
        assertThat(snapshotDate).isBeforeOrEqualTo(today);
        assertThat(ChronoUnit.DAYS.between(snapshotDate, today)).isBetween(0L, 31L);
        assertThat(markdown)
                .contains("最近一周 Trending", "最近一月 Trending", "累计 Star ≥ 50,000")
                .contains("| 入选信号 | 累计 Stars | 周/月新增 |");
    }

    @Test
    void systemPromptForbidsInventingTrendDates() throws Exception {
        String prompt = new ClassPathResource("prompts/interview-system.st")
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(prompt).contains("必须逐字复制上下文给出的快照日期", "禁止自行推断或编造年月");
    }
}
