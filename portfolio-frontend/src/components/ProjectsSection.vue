<script setup lang="ts">
const liveUrl =
  import.meta.env.VITE_ENTERPRISE_RAG_LIVE_URL?.trim() || 'https://enterprise-rag-frontend-seven.vercel.app'

const pipeline = [
  { step: 'Query', detail: '租户 / ACL 上下文' },
  { step: 'Hybrid', detail: 'PGVector + BM25' },
  { step: 'Fuse', detail: 'RRF 融合' },
  { step: 'Rerank', detail: '可插拔重排' },
  { step: 'Answer', detail: '带引用流式输出' },
]

const features = [
  { title: '权限先于检索', text: '按租户与 ACL 过滤候选文档，回答只基于用户有权访问的资料。' },
  { title: '混合召回', text: '向量语义检索与 ParadeDB BM25 词法检索并行，再用 RRF 融合排序。' },
  { title: '可追溯回答', text: 'grounded SSE 流式生成，每个结论都能回到具体来源片段。' },
  { title: '评测闭环', text: 'versioned corpus 与 retrieval / citation 指标，让每次改动都可验证。' },
]
</script>

<template>
  <section id="projects" class="content-section" aria-labelledby="projects-title">
    <div class="section-header">
      <p class="eyebrow">Selected work</p>
      <h2 id="projects-title">One system, built end to end.</h2>
    </div>

    <article class="project-feature">
      <div class="project-item">
        <span class="project-number">01</span>
        <div class="project-copy">
          <h3>EnterpriseRAG</h3>
          <p>
            带权限控制、混合检索与可插拔重排的企业知识库助手：在真实的数据与权限边界下，给出简洁、可验证、可追溯的回答。
          </p>
          <small>Java 21 · Spring AI · PGVector · ParadeDB BM25 · RRF · Vue 3</small>
        </div>
        <div class="project-links">
          <a :href="liveUrl" target="_blank" rel="noopener noreferrer">
            Live <span aria-hidden="true">↗</span>
          </a>
          <a href="https://github.com/Tmakerchima/EnterpriseRAG" target="_blank" rel="noopener noreferrer">
            GitHub <span aria-hidden="true">↗</span>
          </a>
        </div>
      </div>

      <ol class="pipeline" aria-label="EnterpriseRAG 检索链路">
        <li v-for="(node, index) in pipeline" :key="node.step">
          <span class="pipeline-index">{{ String(index + 1).padStart(2, '0') }}</span>
          <strong>{{ node.step }}</strong>
          <small>{{ node.detail }}</small>
        </li>
      </ol>

      <div class="feature-grid">
        <div v-for="feature in features" :key="feature.title" class="feature">
          <h4>{{ feature.title }}</h4>
          <p>{{ feature.text }}</p>
        </div>
      </div>
    </article>

    <a class="quiet-link" href="https://github.com/tmakerchima" target="_blank" rel="noopener noreferrer">
      More on GitHub <span aria-hidden="true">→</span>
    </a>
  </section>
</template>
