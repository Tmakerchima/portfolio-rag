package com.mac.portfolio.tool;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;

// 独立于 RAG 检索流程：作为 ChatClient 的默认工具供模型按需调用，不参与 RagService 的检索逻辑
@Component
public class PortfolioInfoTools {

    private static final Logger log = LoggerFactory.getLogger(PortfolioInfoTools.class);
    private static final String GITHUB_USER = "Tmakerchima";
    private static final String BLOG_URL = "https://tmakerchima.github.io/";

    private final RestClient restClient = RestClient.create();

    @Tool(description = "查询马驰（Mac Ma）GitHub 上的公开仓库统计信息，包括仓库总数、总 star 数，以及最近更新的仓库名称")
    public String getGithubStats() {
        try {
            List<GithubRepo> repos = restClient.get()
                    .uri("https://api.github.com/users/{user}/repos?per_page=100", GITHUB_USER)
                    .retrieve()
                    .body(new org.springframework.core.ParameterizedTypeReference<List<GithubRepo>>() {});

            if (repos == null || repos.isEmpty()) {
                return "未能获取到 GitHub 仓库信息";
            }

            int totalStars = repos.stream().mapToInt(GithubRepo::stargazersCount).sum();
            GithubRepo latest = repos.stream()
                    .max(Comparator.comparing(GithubRepo::pushedAt))
                    .orElse(repos.get(0));

            return String.format(
                    "马驰的 GitHub（github.com/%s）共有 %d 个公开仓库，累计 %d 个 star，最近更新的仓库是「%s」（%s）",
                    GITHUB_USER, repos.size(), totalStars, latest.name(), latest.htmlUrl());
        } catch (Exception e) {
            log.error("调用 GitHub API 失败：{}", e.getMessage());
            return "暂时无法获取 GitHub 统计信息";
        }
    }

    @Tool(description = "获取马驰个人博客（tmakerchima.github.io）最新发布的文章列表，包括标题、链接和发表日期")
    public String getLatestBlogPosts() {
        try {
            Document doc = Jsoup.connect(BLOG_URL).get();
            List<Element> posts = doc.select("article.post").subList(0, Math.min(5, doc.select("article.post").size()));

            if (posts.isEmpty()) {
                return "未能获取到博客文章列表";
            }

            StringBuilder sb = new StringBuilder("马驰博客最新文章：\n");
            for (Element post : posts) {
                Element titleLink = post.selectFirst("a.post-title-link");
                Element time = post.selectFirst("time");
                if (titleLink == null) continue;

                String title = titleLink.text();
                String url = BLOG_URL.replaceAll("/$", "") + titleLink.attr("href");
                String date = time != null ? time.text() : "未知日期";
                sb.append(String.format("- %s（%s）：%s\n", title, date, url));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("解析博客首页失败：{}", e.getMessage());
            return "暂时无法获取博客文章列表";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GithubRepo(String name, String htmlUrl, int stargazersCount, String pushedAt) {
        @com.fasterxml.jackson.annotation.JsonCreator
        GithubRepo(
                @com.fasterxml.jackson.annotation.JsonProperty("name") String name,
                @com.fasterxml.jackson.annotation.JsonProperty("html_url") String htmlUrl,
                @com.fasterxml.jackson.annotation.JsonProperty("stargazers_count") int stargazersCount,
                @com.fasterxml.jackson.annotation.JsonProperty("pushed_at") String pushedAt) {
            this.name = name;
            this.htmlUrl = htmlUrl;
            this.stargazersCount = stargazersCount;
            this.pushedAt = pushedAt;
        }
    }
}
