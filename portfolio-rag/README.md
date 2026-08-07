# Portfolio RAG — 马驰个人简历 AI 问答网站

基于 **Spring Boot 3.3 + Spring AI 1.1 + PGVector** 构建的简历 AI 问答系统。
面试官可以直接用自然语言提问，AI 会按需组合三种能力作答：检索简历原文（RAG）、调用本地工具查实时数据（Function Calling）、调用 GitHub 官方远程 MCP Server 查真实仓库信息（MCP），并流式返回答案。

---

## 整体架构

```
用户输入问题
     │
     ▼
Vue 3 前端（Vercel）
     │  POST /api/chat  (SSE 流式)
     ▼
Spring Boot 3.3 后端
     ├── ChatController        →  接收请求，返回 Flux<String>
     ├── RagService             →  编排检索 + 生成流程，输出三种 SSE 帧
     │     ├── HybridRetrievalService      →  Metadata Filter + PGVector + Lexical Rerank
     │     ├── ChatClient.stream()         →  调用 LLM 生成回答，按需触发工具调用
     │     └── ToolUsageTrackingCallback   →  记录本轮实际调用了哪些工具
     └── IngestService          →  启动时把 knowledge/ 文档语义切块并向量化入库
           └── Markdown 标题切块 + category/topic/project 元数据 → vectorStore.add()

工具层（ChatClient 的 defaultToolCallbacks，与 RAG 检索完全解耦）
     ├── PortfolioInfoTools（Function Calling）
     │     └── getLatestBlogPosts()  →  Jsoup 解析博客首页，查最新文章
     └── GitHub 官方远程 MCP Server（MCP，Streamable HTTP）
           └── api.githubcopilot.com/mcp/  →  查仓库、issue、PR 等真实 GitHub 数据

数据层
     ├── Supabase PostgreSQL + pgvector（向量存储）
     └── classpath:knowledge/（本地简历文档）

LLM 服务（DashScope OpenAI 兼容模式）
     ├── qwen-plus         →  对话生成 + 工具调用决策
     └── text-embedding-v3 →  文本向量化（1024 维）
```

---

## 核心流程详解

### 1. 文档入库流程（启动时执行一次）

```
knowledge/*.md / *.pdf / *.docx
         │
         ▼  Markdown 标题语义切块（PDF/Word 仍由 Apache Tika 解析）
     List<Document>（按章节 / 项目切分）
         │
         ▼  写入 category / section / topic / project / chunk_id 元数据
     List<Document>（有结构的短 chunks）
         │
         ▼  text-embedding-v3（DashScope 向量化）
     float[1024]（向量）
         │
         ▼  pgvector HNSW 索引存储
     Supabase vector_store 表
```

### 2. 问答流程（每次请求）

```
用户问题（自然语言）
         │
         ▼ 小语料（≤ full-context-max-chars）直接返回全部片段；大语料才走检索 ↓
         ▼  Query Intent 路由 → Metadata Filter + text-embedding-v3 / pgvector（top 10，阈值 0.25）
     宽召回候选片段
         │
         ▼  本地融合重排（Vector 60% + Lexical 25% + Metadata 15%，意图命中片段额外加权）
     最相关 3 个片段（总 context 上限 2800 字符）
         │
         ▼  RagService 只拼装一次受约束 Prompt + defaultToolCallbacks（注册可用工具）
     [系统 Prompt] + [精简 context] + [范围约束] + [用户问题] + [工具列表]
         │
         ▼  qwen-plus（流式生成，按需决定是否调用工具）
     Flux<String>（逐 token 返回） + ToolUsageTrackingCallback 记录实际调用的工具
         │
         ▼  SSE（text/event-stream）：@@SOURCES@@ 帧 → 正文 token 流 → @@TOOLS@@ 帧
     前端实时渲染（打字机效果），并展示本轮用到的能力标签
```

### 3. 三种 SSE 帧

| 帧 | 内容 | 含义 |
|---|---|---|
| `@@SOURCES@@<json>` | 检索到的简历片段（source/snippet/score） | 是否用到了 RAG |
| （无标记的纯文本） | 模型生成的正文 | 流式打字机效果 |
| `@@TOOLS@@<json>` | 本轮实际调用的工具列表，格式 `"function-calling:xxx"` / `"mcp:xxx"` | 是否用到了 Function Calling / MCP，以及具体哪个工具 |

---

## 技术栈

| 层次 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 运行时 | Java | 21 | LTS 版本 |
| 框架 | Spring Boot | 3.3.5 | 应用框架 |
| AI 框架 | Spring AI | 1.1.0 | LLM 编排、RAG、Function Calling、MCP Client |
| 响应式 | Spring WebFlux | 随 Boot | SSE 流式响应 |
| LLM | 通义千问 qwen-plus | — | 对话生成 + 工具调用决策（DashScope） |
| Embedding | text-embedding-v3 | 1024 维 | 文本向量化（DashScope） |
| 向量数据库 | PostgreSQL + pgvector | PG 16 | HNSW 索引，余弦距离检索 |
| 托管数据库 | Supabase | — | 免费云 PostgreSQL，pgvector 内置 |
| 文档解析 | Apache Tika | 随 Spring AI | 解析 md/pdf/docx |
| Function Calling | Jsoup | 1.18.1 | 解析博客首页 HTML，查最新文章 |
| MCP Client | spring-ai-starter-mcp-client-webflux | 1.1.0 | 连接 GitHub 官方远程 MCP Server（Streamable HTTP） |
| 前端框架 | Vue 3 + Vite 5 | — | Composition API + script setup |
| 样式 | TailwindCSS 3 | — | 原子化 CSS，无 UI 库 |
| 前端语言 | TypeScript | — | 类型安全 |
| 前端部署 | Vercel | — | 免费静态托管 + CDN |
| 后端部署 | Railway / Render | — | 二选一，仓库内已同时提供两套部署配置 |

---

## 项目结构

```
portfolio-rag/
├── pom.xml                              # Maven 依赖（Spring AI BOM 管理）
├── Dockerfile                           # Render 部署用（多阶段构建：Maven 编译 → JRE 运行）
├── .dockerignore
├── docker-compose.yml                   # 本地 PGVector 备用（已改用 Supabase，未使用）
├── README.md                            # 本文档
└── src/main/
    ├── java/com/mac/portfolio/
    │   ├── PortfolioApplication.java    # 启动类
    │   ├── config/
    │   │   ├── AiConfig.java            # ChatClient Bean：系统提示词 + 工具注册（带 MCP 容错降级） + CORS
    │   │   └── McpConfig.java           # MCP WebClient 认证头（GitHub PAT）
    │   ├── controller/
    │   │   └── ChatController.java      # POST /api/chat → Flux<String> SSE
    │   ├── service/
    │   │   ├── RagService.java               # 精简 context 拼装 + 流式生成 + 三种 SSE 帧
    │   │   ├── HybridRetrievalService.java   # Metadata + Vector + Lexical 融合检索与本地重排
    │   │   ├── KnowledgeDocumentChunker.java # Markdown 语义切块与结构化元数据
    │   │   ├── KnowledgeChunkStore.java      # 入库 chunk 的只读内存快照（关键词召回降级）
    │   │   └── IngestService.java            # 启动时语义切块并向量化入库
    │   └── tool/
    │       ├── PortfolioInfoTools.java        # Function Calling 工具（查博客最新文章）
    │       └── ToolUsageTrackingCallback.java # 包装工具调用，记录本轮实际触发了哪个工具
    └── resources/
        ├── application.yml              # 数据库、DashScope、pgvector、MCP Client 配置
        ├── prompts/
        │   └── interview-system.st      # 系统提示词（定义 AI 角色和回答规范）
        └── knowledge/
            └── about-mac.md            # 简历文档（可替换/追加真实简历内容）

render.yaml                              # 仓库根目录，Render Blueprint 配置
```

---

## 本地启动

### 前置条件

- Java 21（`java -version` 确认）
- Maven 3.8+（`mvn -version` 确认）
- `DASHSCOPE_API_KEY`（通义千问 API Key，在 [dashscope.aliyun.com](https://dashscope.aliyun.com) 获取）
- `SUPABASE_DB_PASSWORD`（Supabase 项目数据库密码，走 Session Pooler 连接）
- `GITHUB_MCP_PAT`（GitHub Personal Access Token，只读权限即可，用于连接 GitHub 官方远程 MCP Server）

### 启动步骤

```bash
# 1. 进入项目目录
cd portfolio-rag

# 2. 设置环境变量（Windows PowerShell）
$env:DASHSCOPE_API_KEY = "sk-xxxxxxxxxxxx"
$env:SUPABASE_DB_PASSWORD = "xxxxxxxxxxxx"
$env:GITHUB_MCP_PAT = "github_pat_xxxxxxxxxxxx"

# Linux / macOS
export DASHSCOPE_API_KEY="sk-xxxxxxxxxxxx"
export SUPABASE_DB_PASSWORD="xxxxxxxxxxxx"
export GITHUB_MCP_PAT="github_pat_xxxxxxxxxxxx"

# 3. 启动应用
mvn spring-boot:run
```

启动日志中会看到：
```
已清空 vector_store，开始重新入库
已入库：about-mac.md，chunk 数量：X
知识库入库完成，共处理 1 个文件
```

如果 GitHub MCP Server 连接慢或暂时不可达，会看到一条降级警告而不是启动失败：
```
GitHub MCP Server 连接失败，本次启动跳过 MCP 工具：...
```
此时 RAG 和 Function Calling 仍正常工作，只是缺少 MCP 工具。

### 验证接口

```bash
# Windows PowerShell
Invoke-WebRequest -Uri "http://localhost:8080/api/chat" `
  -Method POST `
  -ContentType "application/json" `
  -Body '{"question":"马驰有哪些技术栈？"}' `
  -Headers @{"Accept"="text/event-stream"}

# Linux / macOS curl
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d '{"question":"马驰有哪些技术栈？"}' \
  --no-buffer
```

可以分别测试三种能力路径：
- `"马驰做过什么项目？"` → 走 RAG 检索
- `"马驰最近写了什么博客？"` → 走 Function Calling（`getLatestBlogPosts`）
- `"马驰 GitHub 上有多少个仓库？"` → 走 MCP（GitHub 官方远程 Server）

---

## 知识库管理

将简历文档放入 `src/main/resources/knowledge/` 目录，重启应用后自动重新入库（启动时会先清空 `vector_store` 表再全量重建）。

**支持格式：** `.md`（推荐）、`.pdf`、`.docx`

> Tika 解析 PDF/Word 时可能丢失部分格式，建议重要内容用 Markdown 编写。

---

## API 文档

### POST /api/chat

**请求体：**
```json
{
  "question": "马驰在 WMS 项目里怎么做的库存扣减？"
}
```

**响应：** `text/event-stream`（SSE 流式），依次包含：

1. `@@SOURCES@@[{"source":"about-mac.md","snippet":"...","score":0.82}, ...]`
2. 若干段不带标记的正文 token
3. `@@TOOLS@@["function-calling:getLatestBlogPosts"]`（或 `[]`，或 `mcp:` 前缀）

前端按这三类分别解析渲染。

---

## 部署说明

仓库里同时准备了 Railway 和 Render 两套部署配置，二选一即可。

### 方案一：Railway（当前线上使用）

1. 在 [railway.app](https://railway.app) 新建项目，连接 GitHub 仓库，Root 选 `portfolio-rag`
2. Railway 通过 Nixpacks 自动识别 Maven 项目并构建（无需 Dockerfile）
3. 设置环境变量：`DASHSCOPE_API_KEY`、`SUPABASE_DB_PASSWORD`、`GITHUB_MCP_PAT`
4. 注意 Railway 免费 Trial 额度有限，用尽后服务会被暂停且不再自动部署，需要绑卡升级 Hobby Plan

### 方案二：Render（免费备选，无需信用卡）

1. 在 [render.com](https://render.com) 用 GitHub 登录 → New → Blueprint → 选择本仓库
2. Render 会自动读取根目录的 `render.yaml`，识别出 `portfolio-rag` 子目录 + `Dockerfile` 构建方式
3. 为标记 `sync: false` 的三个变量手动填值：`DASHSCOPE_API_KEY`、`SUPABASE_DB_PASSWORD`、`GITHUB_MCP_PAT`
4. 免费版限制：512MB 内存，15 分钟无请求自动休眠，冷启动 30-60 秒

### 前端部署（Vercel）

1. 在 Vercel 导入 `portfolio-frontend` 子目录（Root Directory 设为 `portfolio-frontend`）
2. 设置环境变量 `VITE_API_BASE` 为后端域名（Railway 或 Render 二选一的域名）
3. Git 集成后自动部署

---

## 设计决策记录

| 决策 | 选择 | 原因 |
|------|------|------|
| Web 框架 | WebFlux（而非 Web MVC） | 原生支持 SSE 流式响应，Flux<String> 直接映射 |
| 向量存储 | PGVector（而非 Chroma/Weaviate） | PostgreSQL 生态成熟，金融场景更易被接受 |
| LLM 接入 | DashScope OpenAI 兼容模式 | 国内网络无需代理，Spring AI 原生支持 OpenAI 协议 |
| 数据库托管 | Supabase | 免费、pgvector 内置、无需本地 Docker |
| Embedding 维度 | 1024 | text-embedding-v3 默认维度，精度与成本的平衡点 |
| RAG 实现 | Metadata Filter + Vector Recall + 本地 Lexical Rerank | 避免 Naive RAG 重复检索和无关 context；小型简历库无需额外 reranker 模型 |
| 语料自适应 | 小语料走全量上下文，大语料走混合检索（full-context-max-chars: 8000） | 简历仅约 6400 字符，切块检索反而会漏召回（例如毕业于哪个学校检索不到教育背景）；语料超过阈值自动回退检索，保证可扩展性 |
| 意图命中加权 | Intent 类目命中的片段额外加分（Vector 60% + Lexical 25% + Metadata 15%，命中 +0.2） | 短问题向量相似度扎堆（0.28~0.31），纯向量排序会让问教育却召回技术栈；加权后意图类目稳定置顶 |
| Reranker | 暂不接独立模型 | 当前语料仅一个结构化个人档案，本地融合排序延迟更低、可解释且无新增调用成本；语料扩大后可在 `HybridRetrievalService` 排序阶段替换为模型 reranker |
| GitHub 数据查询 | 真实 MCP（而非自己手写 RestClient 调 GitHub API） | 演示真正的 MCP 协议接入，而不是用同名概念包装普通 HTTP 调用 |
| MCP 启动方式 | `spring.ai.mcp.client.initialized=false` + try-catch 兜底 | GitHub 远程 MCP 偶发握手超时（实测约 1/3 概率 >20s），若不降级会把整个应用（包括 RAG）一起拖垮启动失败 |
| commons-lang3 版本 | 显式锁定 3.17.0 | Spring AI 1.1.0 引入的依赖树里 Maven 解析出 3.14.0，缺少 commons-compress 1.28 需要的方法，导致 Tika 解析文档时 `NoSuchMethodError` |
| Spring AI 2.0 | 暂不升级 | 强制要求 Spring Boot 4.0，属于平台级迁移而非简单版本号变更，生态尚新，作品集项目优先稳定可演示 |
| 工具调用可观测性 | `ToolUsageTrackingCallback` 包装所有工具 + `@@TOOLS@@` SSE 帧 | 让前端能区分一次回答到底是 RAG、Function Calling 还是 MCP 产出的，便于演示和调试 |
