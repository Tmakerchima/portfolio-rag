<script setup lang="ts">
interface ProjectLink {
  label: string
  href: string
}

interface Project {
  name: string
  description: string
  stack: string
  links: ProjectLink[]
}

const projects: Project[] = [
  {
    name: 'EnterpriseRAG',
    description: '带权限控制、混合检索与可插拔重排的企业知识库助手。',
    stack: 'Java 21 · Spring AI · PGVector · RRF',
    links: [
      { label: 'GitHub', href: 'https://github.com/Tmakerchima/EnterpriseRAG' },
      ...(import.meta.env.VITE_ENTERPRISE_RAG_LIVE_URL?.trim()
        ? [{ label: 'Live', href: import.meta.env.VITE_ENTERPRISE_RAG_LIVE_URL.trim() }]
        : []),
    ],
  },
  {
    name: 'TrendCopy AI',
    description: '把实时趋势转化为多平台内容的自动化 Agent。',
    stack: 'Java · Qwen · Firecrawl · Supabase',
    links: [
      { label: 'Live', href: 'https://trendcopy.asia' },
      { label: 'GitHub', href: 'https://github.com/Tmakerchima/trendcopyAI' },
    ],
  },
  {
    name: 'Local Build Agent',
    description: '本地运行、能使用受控工作区工具的编码 Agent。',
    stack: 'Python · Ollama · Agent Runtime',
    links: [
      { label: 'Live', href: 'https://local-agent-azure.vercel.app/' },
      { label: 'GitHub', href: 'https://github.com/Tmakerchima/localAgent' },
    ],
  },
]
</script>

<template>
  <section id="projects" class="content-section" aria-labelledby="projects-title">
    <div class="section-header">
      <p class="eyebrow">Selected work</p>
      <h2 id="projects-title">A few things I’ve built.</h2>
    </div>

    <div class="project-list">
      <article v-for="(project, index) in projects" :key="project.name" class="project-item">
        <span class="project-number">0{{ index + 1 }}</span>
        <div class="project-copy">
          <h3>{{ project.name }}</h3>
          <p>{{ project.description }}</p>
          <small>{{ project.stack }}</small>
        </div>
        <div class="project-links">
          <a
            v-for="link in project.links"
            :key="link.href"
            :href="link.href"
            target="_blank"
            rel="noopener noreferrer"
          >
            {{ link.label }} <span aria-hidden="true">↗</span>
          </a>
        </div>
      </article>
    </div>

    <a class="quiet-link" href="https://github.com/tmakerchima" target="_blank" rel="noopener noreferrer">
      More on GitHub <span aria-hidden="true">→</span>
    </a>
  </section>
</template>
