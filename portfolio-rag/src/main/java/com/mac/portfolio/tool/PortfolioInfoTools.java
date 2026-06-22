package com.mac.portfolio.tool;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

// 独立于 RAG 检索流程：作为 ChatClient 的默认工具供模型按需调用，不参与 RagService 的检索逻辑
// GitHub 相关查询已迁移到真正的 GitHub 官方远程 MCP Server（见 McpConfig/AiConfig），这里只保留没有现成 MCP 的博客查询
@Component
public class PortfolioInfoTools {

    private static final Logger log = LoggerFactory.getLogger(PortfolioInfoTools.class);
    private static final String BLOG_URL = "https://tmakerchima.github.io/";

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
}
