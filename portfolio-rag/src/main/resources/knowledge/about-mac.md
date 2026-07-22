# 马驰 — 完整个人档案

## 基本信息

- 姓名：马驰（英文名 Mac Ma，网络 ID：machi / tmakerchima）
- 年龄：27岁（生日：12月23日）
- 性别：男
- 意向城市：宁波、杭州、上海
- 联系电话：17816110155
- 邮箱：709851837@qq.com
- 个人博客：https://tmakerchima.github.io
- GitHub：https://github.com/tmakerchima
- 求职意向：Java 后端工程师 / AI Agent 工程师

---

## 人生理念与性格

马驰的人生格言是"better tomorrow"，个人主页上写道："人嘛活得开心最重要~"。这句话体现了他积极乐观的人生态度——追求成长与进步的同时，不忘享受生活。

他是一个技术热情高涨、喜欢尝鲜的人：在 GitHub Copilot 刚推出时（2022年6月）就立刻上手体验，同年10月在自己的 1660Ti GPU 上跑通了 Stable Diffusion 图像生成，同年6月还体验了阿里巴巴 RPA 自动化工具，2026年3月与 Codex AI 合作写博客。这种对新技术的敏锐嗅觉和实践精神，贯穿了他的职业成长轨迹。

马驰还有一颗文艺的心，是日本音乐大师坂本龙一（Ryuichi Sakamoto）的粉丝，在坂本龙一先生2023年辞世后，他专程在博客写下了悼文"晚安坂本龙一先生"，表达深切的怀念。

他的博客已累计发布27篇文章，内容横跨技术探索、算法学习、生活随感，是一个愿意把思考分享出来的人。

---

## 教育背景

**浙江工业大学 · 软件工程（本科）** 2017-09 ~ 2021-06

就读于浙江工业大学软件工程专业，学业表现良好，GPA 位列专业前50%。数学基础扎实，获得**全国数学竞赛二等奖**，展现出较强的理论功底。已通过英语四级，具备良好的英语读写与理解能力——这一能力在后来加入美国道富的工作中得到了充分运用。

系统学习了计算机网络、软件工程、操作系统、数据结构等核心课程，积极参与创新实践，曾获校级**"创新杯"三等奖**。

---

## 职业生涯

### 第一阶段：杭州云融有限公司 — Java 后端工程师（2021-11 ~ 2023-06）

毕业后的第一份工作，马驰加入了杭州云融有限公司，投身金融科技领域。在这里，他参与了**宁波银行额度中心项目**的核心开发，亲历了一个银行级系统从开发到上线的完整过程。这段经历让他深刻理解了企业级系统对稳定性、合规性和可维护性的要求。

同期还参与了小微项目的开发，系统成功上线并稳定运行，积累了扎实的 Java 后端工程实践。

### 第二阶段：乐歌人体工学 — WCS 开发工程师（2023-10 ~ 2024-04）

马驰加入乐歌人体工学，参与 WMS 仓储管理系统的对接与开发，以及公司自研项目的研发。在这段时间，他同步开发了个人项目——**仓库上架推荐系统**，将工作中对仓储业务的理解与自主研发的 NSGA-II 遗传算法相结合，形成了一套完整的智能推荐解决方案并开源到 GitHub。

### 第三阶段：美国道富（State Street）— 软件开发工程师（2024-06 ~ 2026-01）

马驰职业生涯的重要里程碑。美国道富是全球最大的托管银行之一，马驰在这里全程使用英语工作，参与了多个企业级数据工程项目：

- 主导 **Hadoop HDFS 至 Databricks 的数据迁移**，利用 Auto Loader 实现增量数据入湖，转换 Delta Lake 格式，解决 Hive 元数据兼容性难题
- 参与 **PAM Data Repository** 开发，完成 Azure 与 Databricks 的鉴权配置，搭建 Harness CI/CD 体系
- 负责 Azure Blob Storage 文件上传 Java 接口开发与测试

这段经历让马驰具备了国际化大型金融机构的研发视野，也让他的技术栈从传统 Java 后端拓展到了云计算、大数据和 DevOps 领域。

---

## 当前职业定位

马驰的定位是 **Java 后端工程师 / AI Agent 工程师**。Java 与 Spring Boot 仍是他的工程基本盘：他有银行级系统交付、WMS 对接、云平台接口开发和大规模数据迁移经验，理解稳定性、数据一致性、接口设计、测试和 CI/CD。

在此基础上，他正在将能力延伸到 AI Agent 工程：不只调用模型 API，而是围绕真实任务设计 **RAG 检索、Tool Calling、MCP 工具接入、流式响应、提示词约束、数据持久化和可观测交互**。本作品集本身就是一套已上线的 Spring AI 应用；近期还完成了 TrendCopy AI 和 FundLens 两个面向真实使用场景的 AI 产品。

---

## 项目经历

### 1. 额度中心项目（2021-11 ~ 2022-09，团队开发，宁波银行）

聚焦网贷模块与额度系统对接，基于 **easyflow** 构建统一网贷额度中心，通过 ESB 接口实现高效数据交换，针对微信渠道设计个性化流程。方案兼容多渠道，统一额度管理与访问控制。项目于 2022 年 9 月成功上线，运行稳定。

技术栈：easyflow、ESB接口、Java、Spring Boot、多渠道兼容设计

### 2. Spring Boot Web 项目（2021-02 ~ 2021-06，个人）

基于 Spring Boot、Thymeleaf、AJAX 构建 Web 应用，采用 MySQL 8.0 及 MyBatis-Plus 实现持久化。亮点是**集成 ItemCF 推荐算法**，通过 Java 调用 Python 接口完成模型训练与数据交互，结合异步任务避免阻塞，注重数据库索引优化与安全防护。经过单元、集成及端到端测试。

技术栈：Spring Boot、Thymeleaf、AJAX、MySQL 8.0、MyBatis-Plus、ItemCF推荐算法、Python

### 3. 仓库上架推荐系统（2023-10 ~ 2024-01，个人，已开源）

开发创新的仓库上架推荐系统，核心算法自主研发：**NSGA-II 遗传算法**和**模拟退火算法**，综合考虑库存成本、货物需求和上架效率，智能推荐最优货物摆放方案。已在 GitHub 上开源（github.com/tmakerchima），是马驰公开展示算法能力的代表作。

技术栈：Python、NSGA-II遗传算法、模拟退火算法、多目标优化

### 4. 室内定位系统（2024-01 ~ 2024-04，个人）

开发高效室内定位系统，分离线采集和在线匹配两阶段。安卓端采集 Wi-Fi 信号强度、蓝牙指纹数据，在线阶段采用 **Weight KNN 算法**进行加权模式匹配，实现高精度室内位置估算，结果通过前端网页实时展示。

技术栈：Spring Boot、Android、Weight KNN算法、Wi-Fi指纹、蓝牙定位

### 5. Hadoop HDFS 至 Databricks 数据迁移（2024-07 ~ 2025-01，美国道富，团队）

参与企业级数据迁移：Hadoop HDFS → Microsoft Databricks。利用 **Auto Loader** 实现增量数据入湖，转换为 Delta Lake 格式优化查询性能，优化并行任务调度，解决 Hive 元数据兼容性问题，校验脚本保障数据一致性。

技术栈：Hadoop HDFS、Databricks、Delta Lake、Auto Loader、Hive、Python

### 6. PAM 开发测试（2025-03 ~ 2026-01，美国道富，团队）

完成 Azure 与 Databricks 鉴权配置，搭建并优化 Databricks Pipeline，建立 **Harness CI/CD** 体系提高部署效率，负责 Azure Blob Storage 文件上传 Java 接口开发与测试。

技术栈：Azure、Databricks、Harness CI/CD、Azure Blob Storage、Java

### 7. Portfolio RAG 简历 AI Agent（2026，个人，已上线并开源）

设计并开发当前个人网站的智能问答系统。前端使用 Vue 3 + TypeScript，通过 SSE 展示流式回答；后端使用 **Java 21、Spring Boot、Spring AI 和 WebFlux** 编排多种能力：从 Supabase PGVector 检索简历片段，通过 Function Calling 查询博客动态，并通过 GitHub 官方远程 MCP Server 查询仓库、Issue 与 PR 等实时信息。系统会在前端标记每轮回答实际使用了 RAG、Function Calling 还是 MCP，GitHub MCP 不可用时会容错降级，避免拖垮主问答链路。

部署链路为：前端 Vercel（https://tmakerchima.cn）、后端 Railway、向量数据库 Supabase PostgreSQL + pgvector。代码地址：https://github.com/Tmakerchima/portfolio-rag

技术栈：Java 21、Spring Boot、Spring AI、WebFlux、RAG、PGVector、Function Calling、MCP、Vue 3、TypeScript、Vercel、Railway、Supabase

### 8. TrendCopy AI 多平台内容 Agent（2026，个人，已上线并开源）

将公开的 AI / 产品趋势转化为小红书、X/Twitter 和 Newsletter 的可发布内容，形成“趋势采集、信息整理、LLM 生成、多平台适配”的内容工作流。后端采用 **Java + Spring Boot**，接入 Qwen 与 Firecrawl；使用 Supabase 持久化用户、订阅和用量数据，并实现邮箱登录、Google OAuth 与支付链路。前端部署在 Vercel，后端部署在 Railway。

网站：https://trendcopy.asia；代码：https://github.com/Tmakerchima/trendcopyAI

技术栈：Java、Spring Boot、Qwen、Firecrawl、Supabase PostgreSQL、Vercel、Railway

### 9. FundLens AI 趋势研究助手（2026，个人，已上线并开源）

覆盖中国场外基金、A 股和美股的趋势研究。项目将趋势、动量、均值回归、风险区间和走步式样本外验证组合为可解释的量化研究流程，并使用 Qwen 审查用户提供的财报、公告、季报或新闻原文，输出事实、风险、催化因素以及它与量化信号是否冲突。模型输出与回测值保持只读，页面明确展示数据与方法限制，不把预测包装成收益保证。

代码：https://github.com/Tmakerchima/fundPrediction

技术栈：JavaScript、Qwen、量化研究、样本外回测、风险建模、Vercel、Railway

### 10. LocalAgent 本地编码 Agent（2026，个人，已上线并开源）

开发一套完全在本机运行的编码 Agent，提供类似 Codex 的浏览器三栏工作台与命令行界面。系统以 **Ollama + Qwen 3.5 9B** 完成本地推理与原生工具调用，浏览器和 CLI 复用同一个 Python Agent runtime；网页服务器将模型步骤、工具调用和最终回答转换为 NDJSON 流式事件。项目只使用 Python 标准库，不依赖 npm、PyPI 或虚拟环境。

Agent 支持 Plan、Edits、Auto 三种权限模式，能够读取与原子写入工作区文件、精确替换内容并执行受控 PowerShell 命令。文件工具限制绝对路径与 `..` 越界，默认阻止删除、磁盘操作、强制 Git 和网络下载等高风险命令；网页服务器与 Ollama 默认仅绑定 `127.0.0.1`。项目还实现了任务队列、停止控制、模型切换、上下文隔离、工具运行记录及自动化测试。

代码：https://github.com/Tmakerchima/localAgent

技术栈：Python、Ollama、Qwen 3.5 9B、Tool Calling、Agent Loop、NDJSON Streaming、PowerShell、HTML/CSS/JavaScript、本地模型安全边界

---

## 技术栈与能力

| 技术 | 熟练度 | 备注 |
|------|--------|------|
| Java | 精通 | 主力语言，贯穿全部后端项目 |
| Spring Boot | 熟练 | 多个项目实战 |
| Hadoop | 85% | 参与大规模数据迁移 |
| Databricks | 80% | Delta Lake、Pipeline、Auto Loader 实战 |
| Azure | 80% | Blob Storage、鉴权配置实战 |
| 算法 | 80% | NSGA-II遗传算法、模拟退火、KNN、ItemCF、LCS |
| AI Agent / LLM 应用 | 熟练 | Spring AI、RAG、Function Calling、MCP、Ollama、本地 Qwen、流式交互、工具编排 |
| Python | 一般 | 推荐算法、数据处理 |
| MySQL / MyBatis-Plus | 熟练 | 多项目实战 |
| PostgreSQL / PGVector | 熟练 | Supabase 托管数据库、向量检索、业务数据持久化 |
| CI/CD (Harness) | 熟练 | 美国道富实战经验 |

---

## 个人兴趣与技术探索

马驰对新技术有着持续的好奇心，体现在他的博客实践记录中：

- **2022年6月**：GitHub Copilot 刚发布就上手体验，写下使用心得
- **2022年6月**：探索阿里巴巴 RPA 自动化工具
- **2022年7月**：自学 Go 语言，写下学习笔记
- **2022年10月**：在自己的 1660Ti 显卡上跑通 Stable Diffusion，体验 AI 图像生成
- **2023年1月**：学习并记录最长公共子序列（LCS）算法
- **2026年3月**：与 Codex AI 合作写博客，探索人机协作写作

除技术外，马驰热爱音乐，是日本著名音乐家坂本龙一的粉丝，2023年坂本龙一去世时，他在博客写下深情悼念。他的生活态度体现在网站标语："人嘛活得开心最重要~"。

马驰喜欢薯哥。

---

## 职业目标

马驰希望继续从事 Java 后端或 AI Agent 工程岗位。适合他的工作应当同时重视工程质量与业务落地：一方面可以发挥 Java、Spring Boot、数据工程和企业级系统经验；另一方面能够继续建设具备检索、工具调用、任务编排与可靠数据链路的 LLM 应用。
