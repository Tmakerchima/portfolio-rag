# Portfolio RAG backend

Java 21 + Spring Boot 3.3 + Spring AI 后端，同时提供个人简历 Agent 和 EnterpriseRAG API。仓库级架构、5,000 文档切块、增量边界与成本说明见 [根 README](../README.md)。

## 两条问答链路

| API | 数据策略 | 每次请求的 DashScope 调用 |
|---|---|---:|
| `POST /api/chat` | 完整 `about-mac.md` 上下文；不查向量库 | 通常 1 次 Qwen；工具问题可能 2+ 模型回合 |
| `POST /api/enterprise/chat` | `enterprise_*` 表；FTS + PGVector + RRF | 1 次 query embedding + 1 次 Qwen |

简历 SSE 只包含正文和末尾的 `@@TOOLS@@<json>`。Enterprise SSE 使用独立的 `@@SOURCES@@`、`@@METRICS@@` 和 `@@ERROR@@` 帧。

`IngestService` 是遗留的手动 helper，应用启动时不会运行，不会清空或重建 `vector_store`。Enterprise 导入同样不是启动任务，由 `eval/enterprise_rag_worker.py` 在受控 runner 中执行。

## 本地启动

服务端环境变量：

```text
DASHSCOPE_API_KEY=<secret>
SUPABASE_DB_PASSWORD=<secret>
GITHUB_MCP_PAT=<optional secret>
ENTERPRISE_RAG_ADMIN_TOKEN=<secret>
ENTERPRISE_RAG_ACTIVE_CORPUS_ID=<validated corpus uuid>
```

```powershell
mvn spring-boot:run
```

验证简历接口：

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/chat" `
  -Method POST `
  -ContentType "application/json" `
  -Headers @{Accept="text/event-stream"} `
  -Body '{"question":"马驰做过哪些 AI 项目？"}'
```

验证 Enterprise 状态与查询：

```powershell
Invoke-RestMethod "http://localhost:8080/api/enterprise/health"

Invoke-WebRequest -Uri "http://localhost:8080/api/enterprise/chat" `
  -Method POST `
  -ContentType "application/json" `
  -Headers @{Accept="text/event-stream"} `
  -Body '{"question":"What are the default limits for multipart uploads?","role":"engineering","strategy":"HYBRID"}'
```

## 部署

- Railway Root Directory：`portfolio-rag`
- 个人主页 Vercel Root Directory：`portfolio-frontend`，公开变量 `VITE_API_BASE`
- Enterprise Vercel Root Directory：`enterprise-rag-frontend`，公开变量 `VITE_API_BASE_URL`
- 数据库与模型密钥只能放在 Railway 服务端环境变量中。

生产 Enterprise corpus 必须先写入 STAGING，完成计数/抽样/查询验收后再激活。Railway redeploy 只读取 ACTIVE corpus，不会重新切块或调用 embedding。

## 进一步阅读

- [EnterpriseRAG 端到端流程](../docs/enterprise-rag-end-to-end-flow.md)
- [部署手册](../docs/enterprise-rag-deployment.md)
- [容量报告](../docs/enterprise-rag-capacity-report.md)
- [灾备与回滚](../docs/enterprise-rag-disaster-recovery.md)
- [离线评估](../docs/enterprise-rag-evaluation.md)

## 安全

不要提交真实 API Key、数据库密码、管理 token 或包含它们的日志。曾经暴露过的凭据应立即轮换，并同步更新 Railway 环境变量。
