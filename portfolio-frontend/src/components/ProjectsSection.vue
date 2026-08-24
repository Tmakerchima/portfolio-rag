<script setup lang="ts">
import { ref } from 'vue'

const expanded = ref<number | null>(null)

interface ProjectLink {
  label: string
  href: string
}

interface Project {
  name: string
  period: string
  tags: string[]
  highlight: string
  detail: string
  featured: boolean
  links?: ProjectLink[]
}

const projects: Project[] = [
  {
    name: 'EnterpriseRAG · 企业知识库助手',
    period: '2026 · 个人项目',
    tags: ['V2', 'Java 21', 'Python Worker', 'PGVector', 'PostgreSQL FTS', 'RRF', 'Vue 3'],
    highlight: 'V2 企业知识库 RAG：700-token 结构化切块、向量 + 关键词混合检索，并按 ACL 控制证据范围。',
    detail: '生产环境当前使用 V2 generation：Python Worker 离线完成 5,000 份文档的 token-aware chunking（700 tokens、80-token overlap）和 1,024 维 Embedding，Contextualizer 保持关闭以控制成本与延迟。在线查询从 ACTIVE corpus 做 PGVector + PostgreSQL FTS 双路召回，经 RRF 和可插拔 reranker 后，再让 Qwen 只基于有限原文证据回答；tenant、department、access level 权限过滤在检索阶段执行。旧 V1 generation 已退休。',
    featured: true,
    links: [
      { label: 'GitHub', href: 'https://github.com/Tmakerchima/portfolio-rag' },
      ...(import.meta.env.VITE_ENTERPRISE_RAG_LIVE_URL?.trim()
        ? [{ label: 'Live site', href: import.meta.env.VITE_ENTERPRISE_RAG_LIVE_URL.trim() }]
        : []),
    ],
  },
  {
    name: 'TrendCopy AI · 多平台内容 Agent',
    period: '2026 · 个人项目',
    tags: ['Java', 'Spring Boot', 'Qwen', 'Firecrawl', 'Supabase'],
    highlight: '把公开的 AI / 产品趋势转化为小红书、X/Twitter 与 Newsletter 的可发布内容。',
    detail: '构建“趋势采集 → 信息整理 → LLM 生成 → 多平台适配”的内容工作流。后端采用 Spring Boot 接入 Qwen 与 Firecrawl，使用 Supabase 持久化用户、订阅和用量数据，并实现邮箱登录、Google OAuth 与支付链路；前端部署在 Vercel，后端部署在 Railway。',
    featured: true,
    links: [
      { label: 'Live site', href: 'https://trendcopy.asia' },
      { label: 'GitHub', href: 'https://github.com/Tmakerchima/trendcopyAI' },
    ],
  },
  {
    name: 'FundLens · AI 趋势研究助手',
    period: '2026 · 个人项目',
    tags: ['JavaScript', 'Qwen', 'Quant Research', 'Backtesting', 'Vercel'],
    highlight: '覆盖中国场外基金、A 股与美股，用量化信号和 AI 证据审查辅助研究。',
    detail: '将趋势、动量、均值回归、风险区间和走步式样本外验证组合为可解释的研究流程，并用 Qwen 审查用户提供的财报、公告与新闻证据，明确区分量化信号与事实材料。项目同时处理真实费率、持仓状态和风险提示，避免把模型输出包装成收益保证。',
    featured: true,
    links: [
      { label: 'Live site', href: 'https://fundprediction.vercel.app/' },
      { label: 'GitHub', href: 'https://github.com/Tmakerchima/fundPrediction' },
    ],
  },
  {
    name: 'Local Build Agent · 本地编码 Agent',
    period: '2026 · 个人项目',
    tags: ['Python', 'Ollama', 'Qwythos 9B', 'Agent Runtime', 'Local Tools'],
    highlight: '在本机运行的 Codex 风格编码 Agent，结合 Web 工作台、命令行和受控 Workspace 工具完成真实开发任务。',
    detail: '参考 grok-build 的分层思路构建本地 Agent runtime：使用 Ollama 驱动 Qwythos 9B 模型，通过统一的消息循环调用文件读写、路径检查、补丁修改和 PowerShell 命令工具。项目提供响应式三栏 Web 工作台、任务历史、流式 NDJSON 对话和安全边界，默认只绑定本机服务，不依赖云端模型或 npm/PyPI 运行时。',
    featured: true,
    links: [
      { label: 'Live site', href: 'https://local-agent-azure.vercel.app/' },
      { label: 'GitHub', href: 'https://github.com/Tmakerchima/localAgent' },
    ],
  },
  {
    name: '额度中心项目',
    period: '2021-11 ~ 2022-09',
    tags: ['Java', 'Spring Boot', 'easyflow', 'ESB', '宁波银行'],
    highlight: '帮助宁波银行网贷额度系统成功上线，兼容多渠道统一额度管理。',
    detail: '基于 easyflow 构建统一网贷额度中心，通过 ESB 接口实现高效数据交换，针对微信渠道设计个性化流程。方案兼容多渠道，统一额度管理与访问控制，于 2022 年 9 月成功上线，运行稳定。',
    featured: false,
  },
  {
    name: 'Hadoop → Databricks 数据迁移',
    period: '2024-07 ~ 2025-01',
    tags: ['Hadoop HDFS', 'Databricks', 'Delta Lake', 'Python', '美国道富'],
    highlight: '将企业级 HDFS 数据迁移至 Databricks，利用 Auto Loader 实现增量入湖。',
    detail: '利用 Databricks Auto Loader 实现增量数据入湖，转换为 Delta Lake 格式优化查询性能。优化并行任务调度，解决 Hive 元数据兼容性问题，通过校验脚本保障数据一致性。',
    featured: false,
  },
  {
    name: '仓库上架推荐系统',
    period: '2023-10 ~ 2024-01',
    tags: ['Python', 'NSGA-II', '模拟退火', '算法', 'GitHub开源'],
    highlight: '自研遗传算法（NSGA-II）智能推荐货物摆放方案，已在 GitHub 开源。',
    detail: '系统采用 Python 开发，核心算法包括 NSGA-II 遗传算法和模拟退火算法，综合考虑库存成本、货物需求和上架效率，智能推荐最优货物摆放方案。已在 GitHub 开源，便于社区参与贡献。',
    featured: false,
  },
  {
    name: '室内定位系统',
    period: '2024-01 ~ 2024-04',
    tags: ['Spring Boot', 'Android', 'Weight KNN', 'Wi-Fi指纹', '蓝牙'],
    highlight: '基于 Wi-Fi 指纹和 Weight KNN 算法实现高精度室内定位，Web 实时展示位置。',
    detail: '离线阶段用安卓端采集室内 Wi-Fi 信号强度、蓝牙等指纹数据，在线阶段采用 Weight KNN 算法进行加权模式匹配，实现高精度位置估算，结果通过前端网页实时展示。后端基于 Spring Boot 构建。',
    featured: false,
    links: [
      { label: 'GitHub', href: 'https://github.com/Tmakerchima/indoorPositioning' },
    ],
  },
]
</script>

<template>
  <section id="projects" class="editorial-section projects-section" aria-labelledby="projects-title">
    <div class="section-aside">
      <p class="section-index">01</p>
      <p class="section-label">Selected work</p>
    </div>

    <div class="section-body">
      <div class="section-heading-row projects-heading">
        <h2 id="projects-title" class="section-title">Systems built for<br />the real world.</h2>
        <p class="section-intro">
          从企业级 Java 系统到可上线的 AI 产品。关注工程稳定性，也关注模型能力如何进入真实工作流。
        </p>
      </div>

      <div class="project-index">
        <article
          v-for="(p, i) in projects"
          :key="p.name"
          class="project-row"
          :class="{ 'is-expanded': expanded === i }"
        >
          <button
            class="project-toggle"
            type="button"
            :aria-expanded="expanded === i"
            :aria-controls="`project-detail-${i}`"
            @click="expanded = expanded === i ? null : i"
          >
            <span class="project-number">{{ String(i + 1).padStart(2, '0') }}</span>

            <span class="project-name-block">
              <span v-if="p.featured" class="project-kind">Independent AI project</span>
              <strong>{{ p.name }}</strong>
              <span class="project-period">{{ p.period }}</span>
            </span>

            <span class="project-summary">{{ p.highlight }}</span>

            <span class="project-control" aria-hidden="true">
              {{ expanded === i ? 'Close' : 'View' }} <i>↗</i>
            </span>
          </button>

          <Transition name="project-detail">
            <div
              v-if="expanded === i"
              :id="`project-detail-${i}`"
              class="project-detail"
            >
              <p>{{ p.detail }}</p>
              <div class="project-meta">
                <p class="project-tags" aria-label="技术栈">
                  <span v-for="tag in p.tags" :key="tag">{{ tag }}</span>
                </p>

                <div v-if="p.links?.length" class="project-links">
                  <a
                    v-for="link in p.links"
                    :key="link.href"
                    :href="link.href"
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    {{ link.label }} <span aria-hidden="true">↗</span>
                  </a>
                </div>
              </div>
            </div>
          </Transition>
        </article>
      </div>
    </div>
  </section>
</template>
