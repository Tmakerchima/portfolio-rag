# Portfolio RAG Backend

Portfolio RAG 的 Java 21 + Spring Boot 3.3 + Spring AI 后端，负责个人知识检索、工具调用和 SSE 流式回答。

> EnterpriseRAG 已拆分为[独立仓库](https://github.com/Tmakerchima/EnterpriseRAG)，不属于本服务的产品边界。

## API

| API | 用途 |
|---|---|
| `POST /api/chat` | 根据个人知识与动态工具生成流式回答 |
| `GET /api/chat/recommendations` | 返回主页推荐问题 |

`POST /api/chat` 使用 `text/event-stream`。响应包含正文、`@@SOURCES@@<json>` 和 `@@TOOLS@@<json>`，用于展示来源与工具调用状态。

## 检索

启动时，`KnowledgeCorpusLoader` 会加载 `src/main/resources/knowledge/**/*`：

- `about-mac.md`：个人经历、项目与技术栈
- `github-trend.md`：带快照日期和有效期的 GitHub 趋势

查询通过 Markdown 语义切块、内存 BM25 和元数据意图召回有限上下文。博客和 GitHub 实时信息由工具按需获取，不会把整份知识文档发送给模型。

## 本地启动

```text
DASHSCOPE_API_KEY=<secret>
SUPABASE_DB_PASSWORD=<secret>
GITHUB_MCP_PAT=<optional secret>
PORTFOLIO_CORS_ALLOWED_ORIGINS=http://localhost:5173
```

```powershell
mvn spring-boot:run
```

验证接口：

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/chat" `
  -Method POST `
  -ContentType "application/json" `
  -Headers @{Accept="text/event-stream"} `
  -Body '{"question":"马驰做过哪些 AI 项目？"}'
```

## 测试

```powershell
mvn test
```

## 部署

- Railway Root Directory：`portfolio-rag`
- Vercel Root Directory：`portfolio-frontend`
- 前端公开变量：`VITE_API_BASE`

数据库、模型和 GitHub 凭据只能保存在后端环境变量中。
