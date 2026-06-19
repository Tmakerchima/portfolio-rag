# Portfolio RAG — 马驰个人简历 AI 问答网站

基于 **Spring Boot 3.3 + Spring AI 1.0 + PGVector** 构建的简历 RAG（检索增强生成）问答系统。
面试官可以直接用自然语言提问，AI 实时从简历中检索相关内容，流式返回答案。

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
     ├── ChatController   →  接收请求，返回 Flux<String>
     ├── RagService       →  编排检索 + 生成流程
     │     ├── VectorStore.search()   →  PGVector 向量检索（top 5 相关片段）
     │     └── ChatClient.stream()    →  调用 LLM 生成回答
     └── IngestService    →  启动时把 knowledge/ 文档向量化入库
           └── TikaDocumentReader → TokenTextSplitter → vectorStore.add()

数据层
     ├── Supabase PostgreSQL + pgvector（向量存储）
     └── classpath:knowledge/（本地简历文档）

LLM 服务（DashScope OpenAI 兼容模式）
     ├── qwen-plus         →  对话生成
     └── text-embedding-v3 →  文本向量化（1024 维）
```

---

## 核心流程详解

### 1. 文档入库流程（启动时执行一次）

```
knowledge/*.md / *.pdf / *.docx
         │
         ▼  TikaDocumentReader（Apache Tika 解析）
     List<Document>（原始文档）
         │
         ▼  TokenTextSplitter（按 token 切块）
     List<Document>（小段落 chunks）
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
         ▼  text-embedding-v3（问题向量化）
     float[1024]（问题向量）
         │
         ▼  pgvector 余弦相似度检索（top 5，阈值 0.5）
     相关简历片段（context）
         │
         ▼  QuestionAnswerAdvisor（拼装 Prompt）
     [系统Prompt] + [context] + [用户问题]
         │
         ▼  qwen-plus（流式生成）
     Flux<String>（逐 token 返回）
         │
         ▼  SSE（text/event-stream）
     前端实时渲染（打字机效果）
```

---

## 技术栈

| 层次 | 技术 | 版本 | 用途 |
|------|------|------|------|
| 运行时 | Java | 21 | LTS 版本，虚拟线程支持 |
| 框架 | Spring Boot | 3.3.5 | 应用框架 |
| AI 框架 | Spring AI | 1.0.0 GA | LLM 编排、RAG、向量存储抽象 |
| 响应式 | Spring WebFlux | 随 Boot | SSE 流式响应 |
| LLM | 通义千问 qwen-plus | — | 对话生成（DashScope） |
| Embedding | text-embedding-v3 | 1024 维 | 文本向量化（DashScope） |
| 向量数据库 | PostgreSQL + pgvector | PG 16 | HNSW 索引，余弦距离检索 |
| 托管数据库 | Supabase | — | 免费云 PostgreSQL，pgvector 内置 |
| 文档解析 | Apache Tika | 随 Spring AI | 解析 md/pdf/docx |
| 前端框架 | Vue 3 + Vite 5 | — | Composition API + script setup |
| 样式 | TailwindCSS 3 | — | 原子化 CSS，无 UI 库 |
| 前端语言 | TypeScript | — | 类型安全 |
| 前端部署 | Vercel | — | 免费静态托管 + CDN |

---

## 项目结构

```
portfolio-rag/
├── pom.xml                              # Maven 依赖（Spring AI BOM 管理）
├── docker-compose.yml                   # 本地 PGVector 备用（已改用 Supabase）
├── README.md                            # 本文档
└── src/main/
    ├── java/com/mac/portfolio/
    │   ├── PortfolioApplication.java    # 启动类
    │   ├── config/
    │   │   └── AiConfig.java            # ChatClient Bean + CORS 配置
    │   ├── controller/
    │   │   └── ChatController.java      # POST /api/chat → Flux<String> SSE
    │   └── service/
    │       ├── RagService.java          # RAG 编排：检索 + 流式生成
    │       └── IngestService.java       # 启动时文档向量化入库
    └── resources/
        ├── application.yml              # 数据库、DashScope、pgvector 配置
        ├── prompts/
        │   └── interview-system.st      # 系统提示词（定义 AI 角色和回答规范）
        └── knowledge/
            └── about-mac.md            # 简历文档（可替换为真实简历）
```

---

## 本地启动

### 前置条件

- Java 21（`java -version` 确认）
- Maven 3.8+（`mvn -version` 确认）
- DASHSCOPE_API_KEY（通义千问 API Key，在 [dashscope.aliyun.com](https://dashscope.aliyun.com) 获取）
- Supabase 项目已创建，`application.yml` 中数据库连接已配置

### 启动步骤

```bash
# 1. 进入项目目录
cd portfolio-rag

# 2. 设置 API Key（Windows PowerShell）
$env:DASHSCOPE_API_KEY = "sk-xxxxxxxxxxxx"

# Linux / macOS
export DASHSCOPE_API_KEY="sk-xxxxxxxxxxxx"

# 3. 启动应用
mvn spring-boot:run
```

启动日志中会看到：
```
已清空 vector_store，开始重新入库
已入库：about-mac.md，chunk 数量：X
知识库入库完成，共处理 1 个文件
```

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

---

## 知识库管理

将简历文档放入 `src/main/resources/knowledge/` 目录，重启应用后自动重新入库。

```
knowledge/
├── resume.md           # 主简历（推荐 Markdown，结构最清晰）
├── wms-project.md      # WMS 项目详情
├── bank-project.md     # 银行项目详情
└── skills.md           # 技能清单
```

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

**响应：** `text/event-stream`（SSE 流式）

每个 chunk 是一段文字，前端逐字拼接渲染。

---

## 部署说明

### 后端部署（Railway 推荐，免费）

1. 在 [railway.app](https://railway.app) 新建项目，连接 GitHub 仓库
2. 设置环境变量 `DASHSCOPE_API_KEY`
3. Railway 自动识别 Maven 项目并构建

### 前端部署（Vercel）

1. 在 Vercel 导入前端仓库
2. 设置环境变量 `VITE_API_BASE` 为 Railway 后端域名
3. 自动部署

---

## 设计决策记录

| 决策 | 选择 | 原因 |
|------|------|------|
| Web 框架 | WebFlux（而非 Web MVC） | 原生支持 SSE 流式响应，Flux<String> 直接映射 |
| 向量存储 | PGVector（而非 Chroma/Weaviate） | PostgreSQL 生态成熟，金融场景更易被接受 |
| LLM 接入 | DashScope OpenAI 兼容模式 | 国内网络无需代理，Spring AI 原生支持 OpenAI 协议 |
| 数据库托管 | Supabase | 免费、pgvector 内置、无需本地 Docker |
| Embedding 维度 | 1024 | text-embedding-v3 默认维度，精度与成本的平衡点 |
| RAG 实现 | QuestionAnswerAdvisor | Spring AI 内置，无需手写 Prompt 拼装逻辑 |
