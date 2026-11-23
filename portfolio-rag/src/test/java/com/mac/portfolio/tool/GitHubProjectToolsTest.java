package com.mac.portfolio.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubProjectToolsTest {

    private static final String REPOS = """
            [
              {"name":"lifeAdvanture","description":"Life game","language":"TypeScript","stargazers_count":3,
               "pushed_at":"2026-11-12T08:00:12Z","homepage":"https://life-advanture.vercel.app",
               "html_url":"https://github.com/Tmakerchima/lifeAdvanture","fork":false,"archived":false},
              {"name":"forked-lib","description":"fork","language":"Go","stargazers_count":0,
               "pushed_at":"2026-11-10T00:00:00Z","homepage":null,
               "html_url":"https://github.com/Tmakerchima/forked-lib","fork":true,"archived":false},
              {"name":"Tmakerchima","description":"profile","language":null,"stargazers_count":0,
               "pushed_at":"2026-11-09T00:00:00Z","homepage":"",
               "html_url":"https://github.com/Tmakerchima/Tmakerchima","fork":false,"archived":false},
              {"name":"portfolio-rag","description":null,"language":"Java","stargazers_count":5,
               "pushed_at":"2026-08-26T14:43:12Z","homepage":"",
               "html_url":"https://github.com/Tmakerchima/portfolio-rag","fork":false,"archived":false}
            ]
            """;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-23T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void listsOwnedReposByRecentPushAndSkipsForksAndProfileReadme() {
        List<URI> calls = new ArrayList<>();
        GitHubProjectTools tools = tools((uri, token) -> {
            calls.add(uri);
            return new GitHubProjectTools.Response(200, REPOS, "59");
        });

        String result = tools.getRecentGitHubProjects(5);

        assertThat(calls).singleElement().asString()
                .startsWith("https://api.github.com/users/Tmakerchima/repos?")
                .contains("sort=pushed");
        assertThat(result)
                .contains("lifeAdvanture：Life game", "TypeScript", "★3", "最近推送 2026-11-12",
                        "在线：https://life-advanture.vercel.app", "portfolio-rag（Java")
                .doesNotContain("forked-lib", "/Tmakerchima/Tmakerchima", "null");
    }

    @Test
    void cachesResultsAndFallsBackToCacheWhenGitHubIsRateLimited() {
        AtomicInteger calls = new AtomicInteger();
        GitHubProjectTools tools = new GitHubProjectTools("Tmakerchima", "", "https://api.github.com",
                Duration.ZERO, (uri, token) -> calls.incrementAndGet() == 1
                ? new GitHubProjectTools.Response(200, REPOS, "59")
                : new GitHubProjectTools.Response(403, "{}", "0"),
                new ObjectMapper(), clock);

        tools.getRecentGitHubProjects(1);
        String second = tools.getRecentGitHubProjects(1);

        assertThat(calls).hasValue(2);
        assertThat(second).contains("lifeAdvanture").doesNotContain("portfolio-rag");
    }

    @Test
    void explainsRateLimitWithoutThrowingWhenNothingIsCached() {
        GitHubProjectTools tools = tools((uri, token) -> new GitHubProjectTools.Response(403, "{}", "0"));

        assertThat(tools.getRecentGitHubProjects(null)).contains("GitHub API 额度已用完");
    }

    @Test
    void sendsTokenOnlyWhenConfigured() {
        List<String> tokens = new ArrayList<>();
        GitHubProjectTools tools = new GitHubProjectTools("Tmakerchima", " secret ", "https://api.github.com/",
                Duration.ofMinutes(10), (uri, token) -> {
            tokens.add(token);
            return new GitHubProjectTools.Response(200, "[]", "4999");
        }, new ObjectMapper(), clock);

        assertThat(tools.getRecentGitHubProjects(3)).contains("没有查到");
        assertThat(tokens).containsExactly("secret");
    }

    private GitHubProjectTools tools(GitHubProjectTools.GitHubHttp http) {
        return new GitHubProjectTools("Tmakerchima", "", "https://api.github.com",
                Duration.ofMinutes(10), http, new ObjectMapper(), clock);
    }
}
