# 马驰 — 个人与工程档案

> 最后更新：2026-08-25。项目事实以马驰公开 GitHub 仓库及仓库 README 为依据；仓库数量、star、Issue、PR 和最新提交等动态数据应通过 GitHub 工具实时查询，不以本文作为实时统计来源。

## 基本信息

- 姓名：马驰（英文名 Mac Ma，网络 ID：machi / tmakerchima）
- 生日：12 月 23 日
- 意向城市：宁波、杭州、上海
- 邮箱：709851837@qq.com
- 个人博客：https://tmakerchima.github.io
- GitHub：https://github.com/Tmakerchima
- 求职方向：Java 后端工程师 / AI Agent 工程师

## 核心定位

马驰是一名以 Java 和 Spring Boot 为工程基本盘、持续向 AI Agent 与 RAG 系统延伸的开发者。他有银行额度系统、WMS 对接、云平台接口、Databricks 数据迁移和 CI/CD 的企业经验，也独立完成了多套可以运行、测试和部署的 AI 产品。

他的优势不是单纯调用模型 API，而是把模型放进完整的软件链路：设计数据与权限边界，构建检索和工具调用，处理流式协议、失败降级、可观测性与评测，再把前后端部署到真实环境。公开 GitHub 项目还显示出一条清晰的演进路线：从算法与 Java 应用，走向 RAG、Agent runtime、AI 代码审查和面向真实用户的智能产品。

## 能力摘要

1. **企业级 Java 工程**：具备 Spring Boot、接口设计、数据库、测试、权限控制、CI/CD 和线上交付经验，参与过金融、仓储和云数据工程项目。
2. **RAG 与 Agent 工程**：实践过语义切块、向量与词法混合召回、RRF、reranker、ACL、grounded generation、Function Calling、MCP、SSE/NDJSON 流式协议和本地工具执行。
3. **产品落地与安全边界**：能够独立完成前后端、认证、持久化、定时任务和云部署；在 LocalAgent、Life Adventure、Spring Vibe Bench 等项目中主动设计权限、密钥、审计与风险边界。

## 教育背景

**浙江工业大学 · 软件工程本科（2017-09 ～ 2021-06）**

系统学习计算机网络、操作系统、数据结构和软件工程等课程。GPA 位列专业前 50%，获得全国数学竞赛二等奖、校级“创新杯”三等奖，并通过英语四级。

## 职业生涯

### 杭州云融有限公司 — Java 后端工程师（2021-11 ～ 2023-06）

参与宁波银行额度中心和小微金融项目，经历银行级系统从开发、联调到上线的完整过程。主要积累是多渠道额度管理、ESB 接口、业务流程编排以及对稳定性、合规性和可维护性的理解。

### 乐歌人体工学 — WCS 开发工程师（2023-10 ～ 2024-04）

参与 WMS/WCS 系统对接与自研项目开发，并把仓储业务理解转化为个人算法项目：使用 NSGA-II 与模拟退火设计仓库上架推荐方案，综合优化库存成本、需求和上架效率。

### 美国道富 State Street — 软件开发工程师（2024-06 ～ 2026-01）

在英语环境中参与企业级数据和云平台工程：完成 Hadoop HDFS 到 Databricks/Delta Lake 的迁移，使用 Auto Loader 处理增量入湖与 Hive 元数据兼容问题；参与 PAM Data Repository、Azure 与 Databricks 鉴权、Harness CI/CD，以及 Azure Blob Storage Java 上传接口的开发与测试。

## 代表性 GitHub 项目

### GitHub 项目总览

公开代表项目包括 Portfolio RAG、EnterpriseRAG、Life Adventure、Spring Vibe Bench、LocalAgent、TrendCopy AI 与 FundLens；此外还有宁波银行额度中心、仓库上架推荐、室内定位和 Hadoop → Databricks 迁移等企业或算法实践。需要完整介绍时，应先用这份总览保证覆盖，再按用户关注点展开具体项目。

### Portfolio RAG — 个人作品集 AI Agent

仓库：https://github.com/Tmakerchima/portfolio-rag

这是马驰用于展示个人经历与工程能力的问答系统。前端采用 Vue 3 + TypeScript，后端采用 Java 21、Spring Boot、Spring AI 与 WebFlux；问答通过 SSE 流式返回，并可通过 Function Calling 查询博客、通过 GitHub MCP 查询动态仓库信息。

个人知识库由 `about-mac.md` 和带日期、有效期的 `github-trend.md` 等文档组成。应用启动时按 Markdown 标题做语义切块，在线查询结合内存 BM25、元数据意图和可用的向量结果进行混合检索，只把命中的有限上下文交给 Qwen，并向前端返回文档级来源与快照状态。这样既能让趋势文档进入真实检索，也避免每次请求重复发送整份档案。

Portfolio RAG 只服务个人作品集问答。项目重点是协调静态知识、动态工具与流式交互，通过稳定 chunk id、有限上下文、工具调用追踪和 GitHub MCP 失败降级保持回答简洁、可追溯。

技术栈：Java 21、Spring Boot、Spring AI、WebFlux、Hybrid Retrieval、PGVector、Function Calling、MCP、Vue 3、TypeScript、Vercel、Railway、Supabase。

### EnterpriseRAG — 可审计的企业知识库案例

仓库：https://github.com/Tmakerchima/EnterpriseRAG

EnterpriseRAG 是与 Portfolio RAG 完全分离的企业知识库项目。它围绕 versioned corpus、tenant/ACL、PGVector、BM25、RRF、可选 reranker、grounded SSE 和评测闭环构建可验证的检索链路。

每次请求保留 request id、trace id、来源与指标；外部依赖不可用时会明确标记未执行状态，不用模拟数据冒充真实结果。

技术栈：Java 21、Spring Boot WebFlux、Spring AI、PostgreSQL、PGVector、ParadeDB BM25、RRF、Python evaluation package、Vue 3、Docker。

### Life Adventure — 个性化 Life OS

仓库：https://github.com/Tmakerchima/lifeAdvanture
在线版本：https://life-advanture.vercel.app

Life Adventure 将 Stanford “Designing Your Life”的方法转化为可交互产品：用户记录人生阶段、价值观、能量与想法，Qwen 根据当前用户的真实上下文生成具体、低风险、可完成的每日行动。产品还提供人生罗盘、Odyssey 路线和未登录用户可用的公共任务。

项目的核心挑战是个性化与隐私同时成立。解决方式是使用 Supabase Auth 和 Google OAuth 登录，对个人数据表启用 Row Level Security，浏览器只能访问当前用户数据；Qwen 与 Service Role 密钥仅在服务端读取。每日推荐由 Vercel Cron 触发，并通过数据库唯一约束保证同一用户同一天的生成幂等。

技术栈：Next.js 16、React 19、TypeScript、Supabase Auth/Postgres/RLS、Qwen、Vercel Functions、Vercel Cron、GitHub Actions。

### Spring Vibe Bench — AI 生成 Spring 代码发布审查器

仓库：https://github.com/Tmakerchima/springVibebench

Spring Vibe Bench 是一个本地、只读、确定性的 Spring Boot 发布前审查工具，用来把 AI 辅助生成代码中的高信号风险压缩为一份短小、可定位、可解释的证据报告。当前规则覆盖通配 CORS、硬编码凭据、危险 DDL、Actuator 暴露、`permitAll`、CSRF、Bean Validation、Docker 跳过测试、MyBatis 原始替换、无 WHERE 的 UPDATE/DELETE 等问题。

每个 finding 都包含文件、行号、命中证据、审查理由和需要人工决定的事项；工具不会自动改写源码，也不把代码上传到 LLM 或云端。它支持 Console、JSON 和离线 HTML 报告，并可以按严重级别作为 CI release gate。项目边界明确：它不替代 SonarQube、Snyk 或人工安全审查，而是服务于更快的 Spring-aware 发布决策。

技术栈：Java、Maven、静态规则扫描、JSON/HTML 报告、CI release gate。

### LocalAgent — 本地编码 Agent

仓库：https://github.com/Tmakerchima/localAgent
公开前端：https://local-agent-azure.vercel.app

LocalAgent 是一套在本机运行的编码 Agent，浏览器工作台与 CLI 复用同一个 Python Agent runtime，通过 Ollama + Qwen 3.5 9B 进行本地推理和工具调用。网页服务器将模型步骤、工具调用与最终回答转换为 NDJSON 流式事件，运行时不依赖 npm、PyPI 或虚拟环境。

它支持 Plan、Edits、Auto 三种权限模式，提供工作区文件读取、原子写入、精确替换和受控 PowerShell 命令。安全边界包括路径越界检查、危险命令拦截、仅绑定 `127.0.0.1`、任务隔离、停止控制和工具运行记录。该项目证明马驰理解 Agent 的核心不仅是模型，而是消息循环、工具协议、权限模型、上下文与可恢复的执行状态。

技术栈：Python、Ollama、Qwen 3.5 9B、Tool Calling、Agent Loop、NDJSON、PowerShell、HTML/CSS/JavaScript。

### TrendCopy AI — 多平台内容 Agent

仓库：https://github.com/Tmakerchima/trendcopyAI
在线版本：https://trendcopy.asia

TrendCopy AI 把公开的 AI/产品趋势转化为小红书、X/Twitter 和 Newsletter 内容，形成“趋势采集 → 信息整理 → LLM 生成 → 平台适配”的工作流。Spring Boot 后端接入 Qwen 与 Firecrawl，Supabase PostgreSQL 保存用户、订阅和用量，并实现邮箱验证码/密码登录、Google OAuth 和支付宝支付链路。前端由 Vercel 托管并代理 Railway 后端 API。

技术栈：Java、Spring Boot、Qwen、Firecrawl、Supabase PostgreSQL、Google OAuth、Resend、Alipay、Vercel、Railway。

### FundLens — 可解释的趋势研究助手

仓库：https://github.com/Tmakerchima/fundPrediction
在线版本：https://fundprediction.vercel.app

FundLens 覆盖中国场外基金、A 股和美股，把多期限趋势、风险区间、费用、走步式样本外验证和信号健康度组合成研究流程。Qwen 只审查用户提供的财报、公告和新闻证据，可以降低可信度或否决，但不能提高排名、加仓或改写量化值。

项目主动处理“预测容易被包装成保证”的风险：展示随机游走基准、样本外 R²、Wilson 区间、安慰剂检验和数据限制；页面将结果定位为研究候选而非买卖指令。

技术栈：JavaScript、Node.js、Qwen、量化研究、走步式回测、风险建模、Vercel、Railway。

### 其他工程实践

- **宁波银行额度中心**：Java、Spring Boot、easyflow、ESB、多渠道额度管理；2022 年 9 月上线。
- **仓库上架推荐系统**：Python、NSGA-II、模拟退火、多目标优化，将仓储业务问题转为可计算模型。
- **室内定位系统**：Spring Boot、Android、Wi-Fi/蓝牙指纹和加权 KNN，完成离线采集与在线定位。
- **Hadoop → Databricks 迁移**：HDFS、Auto Loader、Delta Lake、Hive、Python，处理增量入湖和数据一致性。

## 技术栈与证据

| 能力方向 | 主要技术 | 项目证据 |
|---|---|---|
| Java 后端 | Java 21、Spring Boot、WebFlux、Spring AI、JDBC | 银行额度中心、Portfolio RAG、EnterpriseRAG（独立仓库）、TrendCopy、Spring Vibe Bench |
| RAG 检索 | Markdown 语义切块、PGVector、FTS/BM25、RRF、reranker、ACL | Portfolio RAG、EnterpriseRAG（独立仓库） |
| Agent 工程 | Tool Calling、MCP、Agent Loop、权限模式、SSE/NDJSON | Portfolio RAG、LocalAgent |
| 数据与云 | PostgreSQL、Supabase、Databricks、Delta Lake、Azure Blob | Life Adventure、TrendCopy、美国道富项目 |
| 产品与前端 | Vue 3、TypeScript、Next.js、React、HTML/CSS/JavaScript | 个人主页、EnterpriseRAG（独立仓库）、Life Adventure、LocalAgent |
| 评测与安全 | retrieval/generation/citation 指标、静态规则、RLS、危险命令拦截 | EnterpriseRAG（独立仓库）、Spring Vibe Bench、Life Adventure、LocalAgent |
| 部署与交付 | Docker、Vercel、Railway、Harness CI/CD、GitHub Actions | 多个公开项目与美国道富工作经历 |

## 工程风格

马驰倾向于先把边界写清楚，再扩展功能：没有真实 benchmark 就标记未执行；模型不能改写量化事实；Agent 工具受权限和路径限制；个人数据用 RLS 隔离；静态扫描只提供证据，不冒充完整安全审计。这种风格体现出他对可验证性、失败模式和真实部署约束的重视。

他也习惯用项目验证新技术，而不是停留在概念层。GitHub 上的项目从 Spring/算法应用，逐步覆盖 RAG、Agent runtime、AI 代码审查、个性化产品、认证、支付、评测和部署，反映出较强的自主学习与端到端交付能力。

## 个人兴趣与理念

马驰的人生格言是 “better tomorrow”，个人主页曾写道“人嘛活得开心最重要~”。他持续记录技术探索和生活思考，关注 AI、自动化、算法与人机协作，也喜爱坂本龙一的音乐。早期实践包括 GitHub Copilot、RPA、Go、Stable Diffusion 和算法学习。

## 职业目标

马驰希望继续从事 Java 后端或 AI Agent 工程岗位。理想工作既重视稳定的软件工程、数据与权限边界，也允许他继续建设具有检索、工具调用、评测、可观测性和可靠执行链路的 LLM 应用。
