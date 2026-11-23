package com.mac.portfolio.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 直接查询 GitHub REST API，列出马驰本人最近更新的公开仓库。
 *
 * <p>“马驰最近完成了哪些 GitHub 项目”是主页推荐问题，不能依赖远程 MCP 的握手、PAT 和
 * LLM 自行拼 search query 才能回答。这里固定查询 owner 的公开仓库：有 token 时走 5000 次/小时额度，
 * 没有 token 也能用匿名额度；结果缓存几分钟，避免每次提问都打 GitHub。</p>
 */
@Component
public class GitHubProjectTools {

    private static final Logger log = LoggerFactory.getLogger(GitHubProjectTools.class);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Asia/Taipei"));
    private static final int MAX_LIMIT = 10;
    private static final int FETCH_SIZE = 30;

    /** 访问 GitHub 的最小抽象，测试里可以替换成假数据。 */
    @FunctionalInterface
    interface GitHubHttp {
        Response get(URI uri, String token) throws IOException, InterruptedException;
    }

    record Response(int status, String body, String rateLimitRemaining) {}

    private record CachedRepos(Instant fetchedAt, List<JsonNode> repos) {}

    private final String owner;
    private final String token;
    private final String apiBase;
    private final Duration cacheTtl;
    private final GitHubHttp http;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private volatile CachedRepos cache;

    @Autowired
    public GitHubProjectTools(@Value("${github.owner:Tmakerchima}") String owner,
                              @Value("${github.token:}") String token,
                              @Value("${github.api-base:https://api.github.com}") String apiBase,
                              @Value("${github.cache-ttl:PT10M}") Duration cacheTtl,
                              ObjectMapper objectMapper) {
        this(owner, token, apiBase, cacheTtl, defaultHttp(), objectMapper, Clock.systemUTC());
    }

    GitHubProjectTools(String owner, String token, String apiBase, Duration cacheTtl,
                       GitHubHttp http, ObjectMapper objectMapper, Clock clock) {
        this.owner = owner.trim();
        this.token = token == null ? "" : token.trim();
        this.apiBase = apiBase.replaceAll("/+$", "");
        this.cacheTtl = cacheTtl;
        this.http = http;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Tool(description = "查询马驰（GitHub 用户 Tmakerchima）本人最近更新的公开 GitHub 仓库，"
            + "返回仓库名、简介、语言、Star、最近推送日期、在线地址和仓库链接。"
            + "用户问马驰最近做了/完成了哪些项目、GitHub 上有什么作品、某个仓库最近是否更新时调用。")
    public String getRecentGitHubProjects(
            @ToolParam(description = "返回的仓库数量，1～10，默认 5", required = false) Integer limit) {
        int size = limit == null ? 5 : Math.max(1, Math.min(MAX_LIMIT, limit));
        try {
            List<JsonNode> repos = loadRepos();
            List<JsonNode> visible = repos.stream()
                    .filter(repo -> !repo.path("fork").asBoolean(false))
                    .filter(repo -> !repo.path("archived").asBoolean(false))
                    // 与用户名同名的仓库是 GitHub 个人主页 README，不算项目
                    .filter(repo -> !owner.equalsIgnoreCase(repo.path("name").asText()))
                    .limit(size)
                    .toList();
            if (visible.isEmpty()) {
                return "GitHub 上暂时没有查到 " + owner + " 的公开仓库。";
            }

            StringBuilder sb = new StringBuilder("GitHub 实时数据（")
                    .append(owner).append("，按最近推送排序，查询日期 ")
                    .append(DATE.format(clock.instant())).append("）：\n");
            for (JsonNode repo : visible) {
                sb.append("- ").append(repo.path("name").asText());
                String description = repo.path("description").asText("");
                if (!description.isBlank() && !"null".equals(description)) sb.append("：").append(description.trim());
                List<String> facts = new ArrayList<>();
                String language = repo.path("language").asText("");
                if (!language.isBlank() && !"null".equals(language)) facts.add(language);
                facts.add("★" + repo.path("stargazers_count").asInt(0));
                String pushedAt = repo.path("pushed_at").asText("");
                if (!pushedAt.isBlank() && !"null".equals(pushedAt)) {
                    facts.add("最近推送 " + DATE.format(Instant.parse(pushedAt)));
                }
                sb.append("（").append(String.join(" · ", facts)).append("）");
                String homepage = repo.path("homepage").asText("");
                if (homepage.startsWith("http")) sb.append(" 在线：").append(homepage);
                sb.append(" 仓库：").append(repo.path("html_url").asText()).append('\n');
            }
            return sb.toString().trim();
        } catch (GitHubUnavailableException e) {
            log.warn("GitHub 仓库查询失败：{}", e.getMessage());
            return "暂时无法从 GitHub 获取实时仓库列表（" + e.getMessage() + "）。";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "暂时无法从 GitHub 获取实时仓库列表（请求被中断）。";
        } catch (Exception e) {
            log.warn("GitHub 仓库查询异常：{}", e.toString());
            return "暂时无法从 GitHub 获取实时仓库列表（网络异常）。";
        }
    }

    private List<JsonNode> loadRepos() throws IOException, InterruptedException {
        CachedRepos current = cache;
        if (current != null && current.fetchedAt().plus(cacheTtl).isAfter(clock.instant())) {
            return current.repos();
        }

        URI uri = URI.create(apiBase + "/users/" + owner + "/repos?type=owner&sort=pushed&direction=desc&per_page=" + FETCH_SIZE);
        Response response = http.get(uri, token);
        if (response.status() != 200) {
            // 有缓存时宁可返回稍旧的数据，也不要让主页推荐问题直接失败
            if (current != null) {
                log.warn("GitHub 返回 {}，继续使用 {} 的缓存", response.status(), current.fetchedAt());
                return current.repos();
            }
            throw new GitHubUnavailableException(describe(response));
        }

        JsonNode root = objectMapper.readTree(response.body());
        if (!root.isArray()) throw new GitHubUnavailableException("返回格式异常");
        List<JsonNode> repos = new ArrayList<>();
        root.forEach(repos::add);
        cache = new CachedRepos(clock.instant(), List.copyOf(repos));
        return cache.repos();
    }

    private static String describe(Response response) {
        return switch (response.status()) {
            case 401 -> "GitHub token 无效或已过期";
            case 403, 429 -> "0".equals(response.rateLimitRemaining()) ? "GitHub API 额度已用完" : "GitHub 拒绝了请求";
            case 404 -> "GitHub 用户不存在";
            default -> "GitHub 返回 HTTP " + response.status();
        };
    }

    private static GitHubHttp defaultHttp() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        return (uri, token) -> {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("User-Agent", "portfolio-rag")
                    .GET();
            if (!token.isBlank()) request.header("Authorization", "Bearer " + token);
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body(),
                    response.headers().firstValue("x-ratelimit-remaining").orElse(""));
        };
    }

    private static final class GitHubUnavailableException extends IOException {
        GitHubUnavailableException(String message) {
            super(message);
        }
    }
}
