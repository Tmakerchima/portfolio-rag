<script setup lang="ts">
import { computed, ref } from 'vue'

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
}

const apiBase = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const question = ref('What is the recommended rollback approach for a failed deployment?')
const role = ref<Role>('engineering')
const strategy = ref<Strategy>('HYBRID')
const answer = ref('')
const sources = ref<Source[]>([])
const metrics = ref<Metrics | null>(null)
const error = ref('')
const loading = ref(false)
const expandedSource = ref<string | null>(null)

const roles: Array<{ value: Role; label: string; description: string }> = [
  { value: 'public', label: 'Public', description: 'Public documents only' },
  { value: 'engineering', label: 'Engineering', description: 'Engineering + public' },
  { value: 'finance', label: 'Finance', description: 'Finance + public' },
  { value: 'hr', label: 'HR', description: 'HR + public' },
  { value: 'admin', label: 'Admin', description: 'All authorized demo data' },
]

const examples = [
  'What are the default limits for multipart uploads?',
  'How should an EU region outage fail over, and what are the recovery targets?',
  'What is the recommended two-stage process for rotating signing credentials?',
]

const selectedRole = computed(() => roles.find((item) => item.value === role.value))

async function ask(questionOverride?: string) {
  if (questionOverride) question.value = questionOverride
  const trimmed = question.value.trim()
  if (!trimmed || loading.value) return

  loading.value = true
  answer.value = ''
  sources.value = []
  metrics.value = null
  error.value = ''
  expandedSource.value = null

  try {
    const response = await fetch(`${apiBase}/api/enterprise/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
      body: JSON.stringify({ question: trimmed, role: role.value, strategy: strategy.value }),
    })
    if (!response.ok) throw new Error(`Request failed (${response.status})`)
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
    error.value = requestError instanceof Error ? requestError.message : 'Request failed'
  } finally {
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
      error.value = frame.message || 'Enterprise request failed'
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
        <span>EnterpriseRAG</span>
      </a>
      <span class="status"><i /> shared Spring Boot backend</span>
    </header>

    <main class="layout">
      <section class="intro">
        <p class="eyebrow">RETRIEVAL SYSTEM / DEMO</p>
        <h1>Enterprise Knowledge<br /><em>Assistant</em></h1>
        <p class="lede">
          A production-shaped RAG pipeline for noisy internal knowledge: incremental indexing,
          PostgreSQL Full-Text Search, PGVector, ACL-aware retrieval and RRF fusion.
        </p>
        <div class="architecture-line">
          <span>VECTOR</span><b>+</b><span>FTS</span><b>→</b><span>RRF</span><b>→</b><span>LLM</span>
        </div>
      </section>

      <section class="control-panel panel">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">QUERY CONSOLE</p>
            <h2>Ask the knowledge base</h2>
          </div>
          <span class="live-pill"><i /> live</span>
        </div>
        <form @submit.prevent="ask()">
          <label for="question">Question</label>
          <textarea id="question" v-model="question" rows="4" :disabled="loading" />
          <div class="form-row">
            <label>
              Role
              <select v-model="role" :disabled="loading">
                <option v-for="item in roles" :key="item.value" :value="item.value">
                  {{ item.label }}
                </option>
              </select>
            </label>
            <label>
              Strategy
              <select v-model="strategy" :disabled="loading">
                <option value="HYBRID">Hybrid RRF</option>
                <option value="VECTOR">Vector only</option>
                <option value="KEYWORD">Keyword only</option>
                <option value="HYBRID_RERANK">Hybrid + reranker</option>
              </select>
            </label>
            <button type="submit" :disabled="loading || !question.trim()">
              <span v-if="loading" class="spinner" />
              {{ loading ? 'Retrieving…' : 'Run query' }}
            </button>
          </div>
          <p class="selection-note">{{ selectedRole?.description }} · ACL is applied before ranking</p>
        </form>
        <div class="examples">
          <span>Try an example</span>
          <button v-for="example in examples" :key="example" type="button" @click="ask(example)">
            {{ example }}
          </button>
        </div>
      </section>

      <section class="answer-section panel">
        <div class="panel-heading compact">
          <div>
            <p class="eyebrow">GROUNDED RESPONSE</p>
            <h2>Answer</h2>
          </div>
          <span v-if="metrics" class="request-id">{{ metrics.request_id.slice(0, 8) }}</span>
        </div>
        <div v-if="error" class="error-message">{{ error }}</div>
        <div v-else-if="answer" class="answer-copy">{{ answer }}</div>
        <div v-else class="empty-state">
          <span class="empty-icon">⌁</span>
          <p>Your grounded answer will appear here.</p>
          <small>Sources and latency metrics arrive with the stream.</small>
        </div>
      </section>

      <section class="results-grid">
        <div class="panel sources-panel">
          <div class="panel-heading compact">
            <div>
              <p class="eyebrow">EVIDENCE</p>
              <h2>Retrieved sources <span>{{ sources.length }}</span></h2>
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
          <p v-else class="muted">Run a query to inspect document-level evidence.</p>
        </div>

        <div class="panel metrics-panel">
          <div class="panel-heading compact">
            <div>
              <p class="eyebrow">OBSERVABILITY</p>
              <h2>Pipeline metrics</h2>
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
          <div v-else class="metric-placeholder">Metrics are emitted after the answer completes.</div>
          <div v-if="metrics" class="metric-footer">
            <span>{{ metrics.candidate_count }} candidates</span>
            <span>{{ metrics.final_context_count }} context chunks</span>
            <span>{{ metrics.strategy }}</span>
          </div>
        </div>
      </section>
    </main>

    <footer>
      <span>EnterpriseRAG · architecture demo</span>
      <span>Vue 3 · Spring AI · PostgreSQL · PGVector</span>
    </footer>
  </div>
</template>
