<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type Locale = 'zh' | 'en'
type Role = 'public' | 'engineering' | 'finance' | 'hr' | 'admin'
type Strategy = 'HYBRID' | 'VECTOR' | 'KEYWORD' | 'HYBRID_RERANK'

interface Source {
  source_type: string
  source: string
  title: string
  document_id: string
  chunk_id: string
  chunk: string
  rank: number
  score: number
}

interface Metrics {
  request_id: string
  strategy: Strategy
  vector_ms: number
  fts_ms: number
  rrf_ms: number
  rerank_ms: number
  llm_ms: number
  total_ms: number
  candidate_count: number
  final_context_count: number
  fallback?: string | null
}

interface Health {
  status: string
  message?: string
  active_corpus_id?: string | null
  dataset_version?: string
  document_count?: number
  expected_documents?: number
  chunk_count?: number
  embedded_chunk_count?: number
  failed_count?: number
  vector_backend?: string
  embedding_model?: string
  embedding_dimension?: number
  source_distribution?: Record<string, number>
  vector_ready?: boolean
  fts_ready?: boolean
  benchmark?: { status?: string }
}

const apiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const locale = ref<Locale>('zh')
const question = ref('What are the default limits for multipart uploads?')
const role = ref<Role>('engineering')
const strategy = ref<Strategy>('HYBRID')
const answer = ref('')
const sources = ref<Source[]>([])
const metrics = ref<Metrics | null>(null)
const error = ref('')
const loading = ref(false)
const expandedSource = ref<string | null>(null)
const health = ref<Health | null>(null)
const healthLoading = ref(false)

const copy = {
  zh: {
    brand: '企业知识库',
    language: '语言',
    kicker: 'ENTERPRISE KNOWLEDGE / RAG',
    title: '让每一个答案，\n都有证据。',
    lede: '面向企业内部知识的检索与问答工作台。增量索引、ACL、PostgreSQL FTS 与 PGVector，在一个清晰的证据链里协同工作。',
    architecture: '向量 + 关键词 → 融合排序 → grounded answer',
    queryKicker: 'QUERY CONSOLE',
    queryTitle: '向知识库提问',
    live: '在线',
    statusReady: '语料已就绪',
    statusLoading: '检查中…',
    statusUnavailable: '后端不可用',
    statusMigration: '等待数据库迁移',
    statusEmpty: '暂无 ACTIVE 语料',
    statusIngesting: '语料导入中',
    corpusKicker: 'CORPUS OVERVIEW',
    corpusTitle: '当前语料状态',
    documents: '文档',
    expected: '目标文档',
    chunks: '切片',
    embedded: '已向量化',
    failed: '失败',
    backend: '向量后端',
    model: 'Embedding 模型',
    benchmark: '评估',
    notMeasured: '尚未测量',
    queryUnavailable: '当前 ACTIVE 语料未就绪，查询暂不可用。',
    question: '问题',
    role: '角色',
    strategy: '检索策略',
    run: '开始检索',
    retrieving: '检索中…',
    aclNote: '先执行权限过滤，再进行排序',
    examples: '试试这些问题',
    responseKicker: 'GROUNDED RESPONSE',
    responseTitle: '回答',
    emptyTitle: '你的回答会出现在这里。',
    emptyNote: '来源与延迟指标会随流式回答返回。',
    evidenceKicker: 'EVIDENCE',
    evidenceTitle: '检索来源',
    evidenceEmpty: '运行一次查询，查看文档级证据。',
    observabilityKicker: 'OBSERVABILITY',
    observabilityTitle: '链路指标',
    metricsEmpty: '回答完成后显示检索和模型耗时。',
    candidates: '候选片段',
    contextChunks: '上下文片段',
    footer: 'EnterpriseRAG · 企业知识库检索演示',
    api: 'API',
    notConfigured: '未配置',
    missingApi: '未配置后端地址。请在 Vercel 项目中设置 VITE_API_BASE_URL。',
    networkError: '无法连接后端。请检查 Railway 服务是否正在运行。',
    backendDown: '后端暂时不可用（Railway 返回 502）。请先恢复后端服务。',
    timeout: '请求超时。后端可能正在冷启动或等待模型响应。',
    requestFailed: '请求失败',
    public: 'Public / 公开',
    engineering: 'Engineering / 工程',
    finance: 'Finance / 财务',
    hr: 'HR / 人力',
    admin: 'Admin / 管理员',
    publicDescription: '仅查看公开文档',
    engineeringDescription: '工程文档与公开文档',
    financeDescription: '财务文档与公开文档',
    hrDescription: '人力文档与公开文档',
    adminDescription: '查看全部授权演示数据',
    hybrid: 'Hybrid RRF / 混合',
    vector: 'Vector / 向量',
    keyword: 'Keyword / 关键词',
    rerank: 'Hybrid + reranker / 混合重排',
  },
  en: {
    brand: 'Enterprise knowledge',
    language: 'Language',
    kicker: 'ENTERPRISE KNOWLEDGE / RAG',
    title: 'Every answer\nwith evidence.',
    lede: 'A focused workspace for searching internal knowledge. Incremental indexing, ACLs, PostgreSQL FTS and PGVector work together as one observable evidence chain.',
    architecture: 'vector + keyword → fusion ranking → grounded answer',
    queryKicker: 'QUERY CONSOLE',
    queryTitle: 'Ask the knowledge base',
    live: 'live',
    statusReady: 'corpus ready',
    statusLoading: 'checking…',
    statusUnavailable: 'backend unavailable',
    statusMigration: 'migration required',
    statusEmpty: 'no ACTIVE corpus',
    statusIngesting: 'ingestion in progress',
    corpusKicker: 'CORPUS OVERVIEW',
    corpusTitle: 'Active corpus',
    documents: 'documents',
    expected: 'expected',
    chunks: 'chunks',
    embedded: 'embedded',
    failed: 'failed',
    backend: 'vector backend',
    model: 'embedding model',
    benchmark: 'evaluation',
    notMeasured: 'not measured yet',
    queryUnavailable: 'The ACTIVE corpus is not ready; querying is paused.',
    question: 'Question',
    role: 'Role',
    strategy: 'Retrieval strategy',
    run: 'Run query',
    retrieving: 'Retrieving…',
    aclNote: 'Authorization is applied before ranking',
    examples: 'Try a question',
    responseKicker: 'GROUNDED RESPONSE',
    responseTitle: 'Answer',
    emptyTitle: 'Your grounded answer will appear here.',
    emptyNote: 'Sources and latency metrics arrive with the stream.',
    evidenceKicker: 'EVIDENCE',
    evidenceTitle: 'Retrieved sources',
    evidenceEmpty: 'Run a query to inspect document-level evidence.',
    observabilityKicker: 'OBSERVABILITY',
    observabilityTitle: 'Pipeline metrics',
    metricsEmpty: 'Retrieval and model timings appear after the answer completes.',
    candidates: 'candidates',
    contextChunks: 'context chunks',
    footer: 'EnterpriseRAG · enterprise knowledge retrieval demo',
    api: 'API',
    notConfigured: 'not configured',
    missingApi: 'The backend URL is not configured. Set VITE_API_BASE_URL in the Vercel project.',
    networkError: 'Cannot reach the backend. Check that the Railway service is running.',
    backendDown: 'The backend is unavailable (Railway returned 502). Restore the backend service first.',
    timeout: 'The request timed out. The backend may be cold-starting or waiting for the model.',
    requestFailed: 'Request failed',
    public: 'Public',
    engineering: 'Engineering',
    finance: 'Finance',
    hr: 'HR',
    admin: 'Admin',
    publicDescription: 'Public documents only',
    engineeringDescription: 'Engineering + public documents',
    financeDescription: 'Finance + public documents',
    hrDescription: 'HR + public documents',
    adminDescription: 'All authorized demo data',
    hybrid: 'Hybrid RRF',
    vector: 'Vector only',
    keyword: 'Keyword only',
    rerank: 'Hybrid + reranker',
  },
} as const

type CopyKey = keyof typeof copy.en
const t = (key: CopyKey) => copy[locale.value][key]

const roles = computed(() => [
  { value: 'public' as Role, label: t('public'), description: t('publicDescription') },
  { value: 'engineering' as Role, label: t('engineering'), description: t('engineeringDescription') },
  { value: 'finance' as Role, label: t('finance'), description: t('financeDescription') },
  { value: 'hr' as Role, label: t('hr'), description: t('hrDescription') },
  { value: 'admin' as Role, label: t('admin'), description: t('adminDescription') },
])

const examples = computed(() => locale.value === 'zh'
  ? [
      'What are the default limits for multipart uploads?',
      'How should an EU region outage fail over, and what are the recovery targets?',
      'What is the recommended two-stage process for rotating signing credentials?',
    ]
  : [
      'What are the default limits for multipart uploads?',
      'How should an EU region outage fail over, and what are the recovery targets?',
      'What is the recommended two-stage process for rotating signing credentials?',
    ])

const selectedRole = computed(() => roles.value.find((item) => item.value === role.value))
const statusKey = computed(() => {
  if (healthLoading.value) return 'statusLoading'
  if (!health.value) return 'statusUnavailable'
  if (health.value.status === 'READY' || health.value.status === 'DEGRADED') return 'statusReady'
  if (health.value.status === 'MIGRATION_REQUIRED') return 'statusMigration'
  if (['STAGING', 'EMBEDDING', 'INGESTING', 'INDEXING', 'VALIDATING'].includes(health.value.status)) return 'statusIngesting'
  return 'statusEmpty'
})
const queryReady = computed(() => health.value?.status === 'READY' || health.value?.status === 'DEGRADED')
const statusTone = computed(() => queryReady.value ? 'ready' : 'blocked')
const statusLabel = computed(() => t(statusKey.value as CopyKey))

async function refreshHealth() {
  if (!apiBase) return
  healthLoading.value = true
  try {
    const response = await fetch(`${apiBase}/api/enterprise/health`, { headers: { Accept: 'application/json' } })
    const payload = await response.json() as Health
    health.value = payload
  } catch {
    health.value = null
  } finally {
    healthLoading.value = false
  }
}

onMounted(refreshHealth)

async function ask(questionOverride?: string) {
  if (questionOverride) question.value = questionOverride
  const trimmed = question.value.trim()
  if (!trimmed || loading.value) return

  if (!apiBase) {
    error.value = t('missingApi')
    return
  }
  if (!queryReady.value) {
    error.value = t('queryUnavailable')
    return
  }

  loading.value = true
  answer.value = ''
  sources.value = []
  metrics.value = null
  error.value = ''
  expandedSource.value = null
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 60_000)

  try {
    const response = await fetch(`${apiBase}/api/enterprise/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ question: trimmed, role: role.value, strategy: strategy.value }),
      signal: controller.signal,
    })
    if (!response.ok) {
      if (response.status === 502 || response.status === 503) {
        let message = 'BACKEND_UNAVAILABLE'
        try {
          const payload = await response.clone().json() as { message?: string }
          if (payload.message) message = payload.message
        } catch { /* keep localized fallback */ }
        throw new Error(message)
      }
      throw new Error(`${t('requestFailed')} (${response.status})`)
    }
    if (!response.body) throw new Error('Streaming response is unavailable')

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const result = await reader.read()
      buffer += decoder.decode(result.value || new Uint8Array(), { stream: !result.done })
      const events = buffer.split(/\r?\n\r?\n/)
      buffer = events.pop() || ''
      events.forEach(handleEvent)
      if (result.done) {
        if (buffer.trim()) handleEvent(buffer)
        break
      }
    }
  } catch (requestError) {
    if (requestError instanceof DOMException && requestError.name === 'AbortError') {
      error.value = t('timeout')
    } else if (requestError instanceof TypeError) {
      error.value = t('networkError')
    } else if (requestError instanceof Error && (requestError.message === 'BACKEND_UNAVAILABLE' || requestError.message.includes('migration'))) {
      error.value = t('backendDown')
    } else {
      error.value = requestError instanceof Error ? requestError.message : t('requestFailed')
    }
  } finally {
    window.clearTimeout(timeout)
    loading.value = false
  }
}

function handleEvent(event: string) {
  const data = event
    .split(/\r?\n/)
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')
  if (!data) return

  try {
    if (data.startsWith('@@SOURCES@@')) {
      const frame = JSON.parse(data.slice('@@SOURCES@@'.length)) as { sources: Source[] }
      sources.value = frame.sources || []
    } else if (data.startsWith('@@METRICS@@')) {
      metrics.value = JSON.parse(data.slice('@@METRICS@@'.length)) as Metrics
    } else if (data.startsWith('@@ERROR@@')) {
      const frame = JSON.parse(data.slice('@@ERROR@@'.length)) as { message?: string }
      error.value = frame.message || t('requestFailed')
    } else {
      answer.value += data
    }
  } catch {
    answer.value += data
  }
}
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <a class="brand" href=".">
        <span class="brand-mark">ER</span>
        <span>
          <strong>EnterpriseRAG</strong>
          <small>{{ t('brand') }}</small>
        </span>
      </a>
      <div class="topbar-actions">
        <span class="status" :class="statusTone"><i /> {{ health?.status || t('statusUnavailable') }} · {{ statusLabel }}</span>
        <label class="language-control">
          <span>{{ t('language') }}</span>
          <select v-model="locale" aria-label="Language">
            <option value="zh">中文</option>
            <option value="en">English</option>
          </select>
        </label>
      </div>
    </header>

    <main class="page-content">
      <section class="hero">
        <p class="eyebrow">{{ t('kicker') }}</p>
        <h1>{{ t('title') }}</h1>
        <p class="lede">{{ t('lede') }}</p>
        <p class="architecture-line">{{ t('architecture') }}</p>
      </section>

      <section class="corpus-card card">
        <div class="section-heading compact">
          <div>
            <p class="eyebrow">{{ t('corpusKicker') }}</p>
            <h2>{{ t('corpusTitle') }}</h2>
          </div>
          <span class="request-id">{{ health?.dataset_version || '—' }}</span>
        </div>
        <div v-if="health" class="corpus-grid">
          <div><span>{{ t('documents') }}</span><strong>{{ health.document_count ?? 0 }}</strong><small>/ {{ health.expected_documents ?? 0 }}</small></div>
          <div><span>{{ t('chunks') }}</span><strong>{{ health.chunk_count ?? 0 }}</strong></div>
          <div><span>{{ t('embedded') }}</span><strong>{{ health.embedded_chunk_count ?? 0 }}</strong></div>
          <div><span>{{ t('failed') }}</span><strong>{{ health.failed_count ?? 0 }}</strong></div>
        </div>
        <div v-else class="metric-placeholder">{{ t('statusUnavailable') }}</div>
        <div v-if="health" class="corpus-footer">
          <span>{{ t('backend') }}: {{ health.vector_backend || '—' }}</span>
          <span>{{ t('model') }}: {{ health.embedding_model || '—' }}</span>
          <span>{{ t('benchmark') }}: {{ health.benchmark?.status === 'NOT_MEASURED_YET' ? t('notMeasured') : health.benchmark?.status || t('notMeasured') }}</span>
        </div>
      </section>

      <section class="query-card card">
        <div class="section-heading">
          <div>
            <p class="eyebrow">{{ t('queryKicker') }}</p>
            <h2>{{ t('queryTitle') }}</h2>
          </div>
          <span class="live-pill" :class="statusTone"><i /> {{ statusLabel }}</span>
        </div>
        <form @submit.prevent="ask()">
          <label for="question">{{ t('question') }}</label>
          <textarea id="question" v-model="question" rows="4" :disabled="loading" />
          <div class="form-row">
            <label>
              {{ t('role') }}
              <select v-model="role" :disabled="loading">
                <option v-for="item in roles" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
            </label>
            <label>
              {{ t('strategy') }}
              <select v-model="strategy" :disabled="loading">
                <option value="HYBRID">{{ t('hybrid') }}</option>
                <option value="VECTOR">{{ t('vector') }}</option>
                <option value="KEYWORD">{{ t('keyword') }}</option>
                <option value="HYBRID_RERANK">{{ t('rerank') }}</option>
              </select>
            </label>
            <button type="submit" :disabled="loading || !question.trim() || !queryReady">
              <span v-if="loading" class="spinner" />
              {{ loading ? t('retrieving') : t('run') }}
            </button>
          </div>
          <p class="selection-note">{{ selectedRole?.description }} · {{ t('aclNote') }}</p>
          <p v-if="!queryReady" class="selection-note warning">{{ health?.message || t('queryUnavailable') }}</p>
        </form>
        <div class="examples">
          <span>{{ t('examples') }}</span>
          <button v-for="example in examples" :key="example" type="button" @click="ask(example)">
            {{ example }}
          </button>
        </div>
        <div class="api-note">{{ t('api') }} · {{ apiBase || t('notConfigured') }}</div>
      </section>

      <section class="answer-card card">
        <div class="section-heading compact">
          <div>
            <p class="eyebrow">{{ t('responseKicker') }}</p>
            <h2>{{ t('responseTitle') }}</h2>
          </div>
          <span v-if="metrics" class="request-id">{{ metrics.request_id.slice(0, 8) }}</span>
        </div>
        <div v-if="error" class="error-message" role="alert">{{ error }}</div>
        <div v-else-if="answer" class="answer-copy">{{ answer }}</div>
        <div v-else class="empty-state">
          <span class="empty-icon">✳</span>
          <p>{{ t('emptyTitle') }}</p>
          <small>{{ t('emptyNote') }}</small>
        </div>
      </section>

      <section class="results-grid">
        <div class="card sources-panel">
          <div class="section-heading compact">
            <div>
              <p class="eyebrow">{{ t('evidenceKicker') }}</p>
              <h2>{{ t('evidenceTitle') }} <span>{{ sources.length }}</span></h2>
            </div>
          </div>
          <div v-if="sources.length" class="source-list">
            <article v-for="source in sources" :key="source.chunk_id" class="source-item">
              <button type="button" class="source-title" @click="expandedSource = expandedSource === source.chunk_id ? null : source.chunk_id">
                <span class="source-type">{{ source.source_type }}</span>
                <strong>{{ source.title || source.document_id }}</strong>
                <span class="source-rank">#{{ source.rank }} · {{ source.score.toFixed(3) }}</span>
              </button>
              <p v-if="expandedSource === source.chunk_id">{{ source.chunk }}</p>
            </article>
          </div>
          <p v-else class="muted">{{ t('evidenceEmpty') }}</p>
        </div>

        <div class="card metrics-panel">
          <div class="section-heading compact">
            <div>
              <p class="eyebrow">{{ t('observabilityKicker') }}</p>
              <h2>{{ t('observabilityTitle') }}</h2>
            </div>
          </div>
          <div v-if="metrics" class="metric-grid">
            <div><span>Vector</span><strong>{{ metrics.vector_ms }}<small>ms</small></strong></div>
            <div><span>FTS</span><strong>{{ metrics.fts_ms }}<small>ms</small></strong></div>
            <div><span>RRF</span><strong>{{ metrics.rrf_ms }}<small>ms</small></strong></div>
            <div><span>Rerank</span><strong>{{ metrics.rerank_ms }}<small>ms</small></strong></div>
            <div><span>LLM</span><strong>{{ metrics.llm_ms }}<small>ms</small></strong></div>
            <div class="total"><span>Total</span><strong>{{ metrics.total_ms }}<small>ms</small></strong></div>
          </div>
          <div v-else class="metric-placeholder">{{ t('metricsEmpty') }}</div>
          <div v-if="metrics" class="metric-footer">
            <span>{{ metrics.candidate_count }} {{ t('candidates') }}</span>
            <span>{{ metrics.final_context_count }} {{ t('contextChunks') }}</span>
            <span>{{ metrics.strategy }}</span>
            <span v-if="metrics.fallback">fallback: {{ metrics.fallback }}</span>
          </div>
        </div>
      </section>
    </main>

    <footer>
      <span>{{ t('footer') }}</span>
      <span>Vue 3 · Spring AI · PostgreSQL · PGVector</span>
    </footer>
  </div>
</template>
