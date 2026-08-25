---
document_type: github_trend
snapshot_date: 2026-08-25
expires_at: 2026-09-08
analysis_date: 2026-08-25
analysis_expires_at: 2026-09-08
source_url: https://github.com/trending
time_window: weekly+monthly
---
# GitHub Trend 追踪 — AI Agent、RAG 与开发者工具

> 快照日期：2026-08-25
> 数据窗口：GitHub Trending · This week + This month
> 官方来源：https://github.com/trending?since=weekly 与 https://github.com/trending?since=monthly
> 入选规则：满足以下任一条件即可进入候选：最近一周 Trending、最近一月 Trending，或与 Agent 相关且累计 Star ≥ 50,000。累计 Star 取自 Trending 页面或 GitHub Repository Search API，并在 Repository API 可用时复核。
> 说明：这是带日期的趋势快照，不是实时排行榜。周/月新增只表示抓取当时的 Trending 页面数据；需要当前 Star、提交、Issue 或 PR 时，应调用 GitHub 工具重新查询。

## 最新自动快照

<!-- TREND_SNAPSHOT_START -->
| 仓库 | 入选信号 | 累计 Stars | 周/月新增 | 简介 |
|---|---|---:|---:|---|
| [harry0703/MoneyPrinterTurbo](https://github.com/harry0703/MoneyPrinterTurbo) | 近一周 / 高 Star | 116,193 | 周 +10,647 | 利用 AI 大模型和自动化工作流，根据主题或关键词一键生成高清短视频。Generate HD short videos from a topic or keyword with an automated AI workflow. |
| [TencentCloud/TencentDB-Agent-Memory](https://github.com/TencentCloud/TencentDB-Agent-Memory) | 近一月 | 24,359 | 月 +15,093 | TencentDB Agent Memory is a team-level memory hub for AI Agents — turning conversations, docs, and code into four reusable memory assets (Chat Memory, Skill, LLM-Wiki, Code-Graph) that are governed, shared, and equipped across agents and frameworks. |
| [ayghri/i-have-adhd](https://github.com/ayghri/i-have-adhd) | 近一月 | 24,039 | 月 +14,346 | A skill to stop your coding agent from burying the answer. ADHD-friendly output. |
| [volcengine/OpenViking](https://github.com/volcengine/OpenViking) | 近一周 / 近一月 | 33,129 | 周 +4,048 / 月 +5,784 | Self-evolving Context Database for AI Agents. Unify Agent Memory, Knowledge RAG and Skills. |
| [zhaoxuya520/reverse-skill](https://github.com/zhaoxuya520/reverse-skill) | 近一月 | 28,975 | 月 +19,889 | Reverse Engineering / Authorized Penetration Testing / Security Research Skill Router Pack AI-powered routing + On-demand toolchain bootstrapping + Self-evolving knowledge base Supports Claude Code, Kiro, Cursor, Cline, and other AI coding clients 逆向/渗透/安全技能路由包 - AI 自动路由 + 按需自举工具链 + 自动进化经验库 \| 支持 Claude Code / Kiro / Cursor / Cline 等代码 AI 客户端 |
| [anthropics/claude-plugins-community](https://github.com/anthropics/claude-plugins-community) | 近一周 | 1,505 | 周 +877 | Community plugin marketplace for Claude Cowork and Claude Code. Read-only mirror — submit plugins at clau.de/plugin-directory-submission. |
| [microsoft/AI-For-Beginners](https://github.com/microsoft/AI-For-Beginners) | 近一月 / 高 Star | 66,862 | 月 +14,152 | 12 Weeks, 24 Lessons, AI for All! |
| [virgiliojr94/book-to-skill](https://github.com/virgiliojr94/book-to-skill) | 近一月 | 25,371 | 月 +15,714 | Turn any technical book PDF into a Claude Code skill — ready to study, reference, and use while you work. |
<!-- TREND_SNAPSHOT_END -->

本表由定时任务合并 GitHub Trending weekly 与 monthly 页面，并用 Repository API 补充累计 Star；下方深度观察由人工维护，并保留独立的分析日期和有效期。

## GitHub 趋势总览

本周与马驰技术方向最相关的信号，不是又出现一个单纯的聊天 UI，而是 Agent 工程正在向基础设施层深入：上下文被做成可观测的数据系统，跨 Agent 记忆与交接成为独立能力，运行时强调本地优先和审计记录，插件/Skill 开始形成标准化生态，安全扫描也从模型扩展到 MCP、Agent 和 Skill 供应链。

这些趋势与马驰现有项目形成直接对应：Portfolio RAG 与 EnterpriseRAG 关注检索和证据；LocalAgent 关注本地运行、权限与工具记录；Spring Vibe Bench 关注 AI 生成代码的确定性审查；Life Adventure 与 TrendCopy 则体现 Agent 在垂直产品工作流中的落地。

## GitHub 热门仓库观察

### volcengine/OpenViking — Agent Context Database

- GitHub：https://github.com/volcengine/OpenViking
- 本周 Trending 增量快照：约 4,048 stars this week
- 方向：把 memory、resource 和 skill 统一为 `viking://` 虚拟文件系统，并使用 L0 摘要、L1 概览、L2 详情进行分层加载。
- 值得关注：检索不再只是“向量库 top-k”，而是可浏览、可分层、可观察的 context engineering。每次检索保留路径轨迹，便于调试为什么取到某段上下文。
- 与马驰项目的关系：Portfolio RAG 可借鉴分层上下文和 retrieval trace；EnterpriseRAG 可继续加强“候选 → 最终上下文”的可解释轨迹与 token budget。

### akitaonrails/ai-memory — 跨 Coding Agent 长期记忆

- GitHub：https://github.com/akitaonrails/ai-memory
- 本周 Trending 增量快照：约 2,520 stars this week
- 方向：通过生命周期 hooks、MCP 和 Markdown wiki，让 Claude Code、Codex、Cursor、Gemini CLI 等不同 Agent 共享长期记忆与 bounded handoff。
- 值得关注：社区开始把“会话结束后如何保留决策、失败路径和待办”当作独立基础设施问题，而不是继续无限扩大单次上下文窗口。
- 与马驰项目的关系：LocalAgent 已有任务隔离和上下文管理，下一步可以探索结构化 handoff、会话摘要和跨模型可移植记忆，同时保持敏感内容过滤与用户控制。

### cursor/plugins 与 anthropics/claude-plugins-community — 插件和 Skill 生态

- GitHub：https://github.com/cursor/plugins
- GitHub：https://github.com/anthropics/claude-plugins-community
- 本周 Trending 增量快照：Cursor plugins 约 1,832；Claude community plugins 约 877 stars this week
- 方向：把 Skills、Rules、MCP、工具连接与工作流封装成可安装、带 manifest、可审查的插件单元。
- 值得关注：Agent 能力正在从项目内 prompt 复制，走向带版本、依赖、权限和分发渠道的能力包。插件安全扫描和兼容性验证会随生态一起变重要。
- 与马驰项目的关系：LocalAgent 的工具与工作流可抽象为 manifest 驱动的能力；Spring Vibe Bench 可以提供插件或 Skill 的确定性安全检查规则。

### apache/maka — 本地优先、可审计的 Agent Workspace

- GitHub：https://github.com/apache/maka
- 本周 Trending 增量快照：约 1,313 stars this week
- 方向：在本地 Agent workspace 中记录模型消息、工具调用、工具结果、权限决策和终止事件，并保存追加式执行记录。
- 值得关注：Agent runtime 的竞争点逐渐从“能不能调用工具”转向“能否审批、终止、恢复、审计和评测”。
- 与马驰项目的关系：LocalAgent 已经具备 Plan/Edits/Auto 权限、停止控制和运行记录；可以继续补充 append-only event log、崩溃恢复与可重放的评测用例。

### Tencent/AI-Infra-Guard — Agent、MCP 与 Skill 安全

- GitHub：https://github.com/Tencent/AI-Infra-Guard
- 本周 Trending 增量快照：约 1,212 stars this week
- 方向：覆盖 Agent Scan、MCP Scan、Skill Scan、AI 基础设施漏洞与 jailbreak evaluation，并关注 tool poisoning、凭据外泄、命令注入和 Skill 供应链风险。
- 值得关注：AI 安全边界从 prompt 注入扩展到完整工具链。MCP server 和 Skill 都可能引入执行、数据外传与依赖风险，因此需要白名单、静态分析、动态隔离和审计证据。
- 与马驰项目的关系：Spring Vibe Bench 可以从 Spring 源码发布检查扩展到 MCP 配置、Agent Skill manifest 和危险工具声明；Portfolio RAG 的 GitHub MCP 也应继续保持最小权限和失败降级。

### eneskirca/nodeterm — 并行 Agent 的空间化工作台

- GitHub：https://github.com/eneskirca/nodeterm
- 本周 Trending 增量快照：约 529 stars this week
- 方向：把多个终端和 AI Agent 会话放在可拖拽画布与看板中，用持久 tmux session、状态钩子和上下文计量管理并行工作。
- 值得关注：当开发者同时运行多个 Agent 时，新的瓶颈变成会话可见性、权限等待、上下文消耗与任务状态，而不仅是模型能力。
- 与马驰项目的关系：LocalAgent 的三栏工作台和任务队列可以继续探索多任务状态总览，但应先保证 workspace 隔离、资源限制与明确的人工接管点。

## GitHub 趋势信号

### 趋势一：Context Engineering 从技巧变成系统

OpenViking、ai-memory 等项目表明，上下文正在形成独立的数据层：需要分层、预算、检索轨迹、生命周期和长期记忆。对 RAG 项目而言，下一阶段不只是换 embedding 模型，而是回答“上下文从哪里来、为什么被选中、何时过期、如何压缩、如何验证”。

### 趋势二：Agent Runtime 进入可靠性竞争

Maka 与 nodeterm 代表的方向强调权限、停止、恢复、审计、多任务和人工接管。Agent 产品要进入真实开发流程，必须把每次工具调用视为可记录、可拒绝、可重试但不能静默重复的系统事件。

### 趋势三：Skill 与 Plugin 成为能力分发单元

Cursor 和 Claude 的插件仓库把 prompt、规则、MCP 与工具配置封装到 manifest 驱动的包中。新的工程问题包括版本兼容、权限声明、依赖来源、安装/卸载、更新策略和供应链安全。

### 趋势四：安全扫描覆盖完整 Agent 供应链

AI-Infra-Guard 的热度说明，安全关注点已经扩展到 Agent、MCP、Skill、模型服务和工具执行层。只做 prompt 过滤远远不够；工程上需要最小权限、配置扫描、敏感数据隔离、命令审计和明确的确认边界。

## 与马驰项目的下一步结合

1. **Portfolio RAG / EnterpriseRAG**：增加检索轨迹、知识更新时间、source-level freshness 和 context budget 展示；对趋势问题明确返回快照日期。
2. **LocalAgent**：设计 append-only 运行日志、跨会话 handoff 和可恢复任务，同时维持 Plan/Edits/Auto 的权限差异。
3. **Spring Vibe Bench**：探索 MCP/Skill 配置规则，例如危险命令、过宽权限、明文凭据、未知远程 server 和缺少版本固定。

## GitHub Trend 更新规则

- 每次更新先查看 GitHub 官方 Trending 的 daily 与 weekly 页面，再阅读候选仓库 README，不能只根据仓库名称猜测用途。
- 优先保留与 Java、AI Agent、RAG、MCP、开发者工具、安全和本地模型相关的 5～8 个仓库，不追求完整搬运排行榜。
- 记录快照日期和时间窗口；star 增量必须标注为当时快照，不把它写成长期事实。
- 新快照应替换过期数字，并检查仓库是否改名、归档、转移或改变产品边界。
- 问答涉及“现在”“实时”“准确 star/Issue/PR”时，必须调用 GitHub 工具；本文只用于解释阶段性趋势及其与马驰项目的关系。
