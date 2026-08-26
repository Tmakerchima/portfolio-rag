# Portfolio RAG

一个简洁的个人作品集 AI 问答网站。用户可以直接询问我的经历、项目与技术方向，也可以查询近期 GitHub 趋势。

[在线主页](https://tmakerchima.cn/) · [GitHub](https://github.com/Tmakerchima/portfolio-rag)

> 本项目只维护 Portfolio RAG。企业知识库产品已拆分为独立项目：[EnterpriseRAG](https://github.com/Tmakerchima/EnterpriseRAG)。

## 功能

- 基于 `about-mac.md` 回答个人经历、项目与技术栈问题
- 基于 `github-trend.md` 检索近期 GitHub 热门项目与趋势
- 使用 Markdown 语义切块、内存 BM25 和元数据意图完成轻量检索
- 通过 SSE 流式返回答案、来源与工具调用状态
- 按需调用博客工具和 GitHub MCP 获取动态信息
- 由后端统一提供推荐问题

## 工作流程

```text
Vue 3 主页
    ↓ POST /api/chat
Spring Boot + Spring AI
    ↓ 检索有限上下文
about-mac.md / github-trend.md
    ↓
Qwen 生成答案 → SSE 返回前端
```

博客、Star、Issue、PR 等实时问题不会依赖静态知识文件，后端会在需要时调用对应工具。趋势文档带有快照日期与有效期，过期内容不会被当作当前事实。

## 项目结构

```text
portfolio-frontend/   Vue 3 个人主页
portfolio-rag/        Java 21 / Spring Boot 后端
scripts/              GitHub 趋势更新与校验脚本
```

## 本地运行

后端所需环境变量：

```text
DASHSCOPE_API_KEY=<server-side secret>
SUPABASE_DB_PASSWORD=<server-side secret>
GITHUB_MCP_PAT=<optional server-side secret>
PORTFOLIO_CORS_ALLOWED_ORIGINS=http://localhost:5173
```

启动后端：

```powershell
cd portfolio-rag
mvn spring-boot:run
```

启动前端：

```powershell
cd portfolio-frontend
npm install
npm run dev
```

前端使用 `VITE_API_BASE` 指向后端地址。密钥只能配置在服务端，不能写入前端环境变量或提交到仓库。

## 知识更新

- 个人信息与项目经历：`portfolio-rag/src/main/resources/knowledge/about-mac.md`
- GitHub 趋势快照：`portfolio-rag/src/main/resources/knowledge/github-trend.md`
- 推荐问题：`portfolio-rag/src/main/resources/portfolio-recommendations.json`

运行测试：

```powershell
cd portfolio-rag
mvn test
```

## EnterpriseRAG

EnterpriseRAG 已拥有独立的代码、文档与发布流程，不属于 Portfolio RAG 的产品功能。企业知识库、权限检索、评测和可观测性相关内容请前往 [Tmakerchima/EnterpriseRAG](https://github.com/Tmakerchima/EnterpriseRAG)。
