<script setup lang="ts">
import { ref } from 'vue'

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080'

interface ToolUsage {
  origin: string
  name: string
}

const LEGACY_SOURCES_MARKER = '@@SOURCES@@'
const TOOLS_MARKER = '@@TOOLS@@'

const question = ref('')
const answer = ref('')
const loading = ref(false)
const toolsUsed = ref<ToolUsage[]>([])

const presets = [
  '马驰最近完成了哪些 GitHub 项目？',
  'EnterpriseRAG 如何保证回答可验证？',
  '最近 GitHub 上有哪些 Agent 趋势？',
]

async function ask(q?: string) {
  const text = q ?? question.value.trim()
  if (!text || loading.value) return

  question.value = text
  answer.value = ''
  loading.value = true
  toolsUsed.value = []

  try {
    const res = await fetch(`${API_BASE}/api/chat`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question: text }),
    })

    if (!res.ok || !res.body) throw new Error('请求失败')

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let sseBuffer = ''

    const handleSseEvent = (event: string) => {
      const content = event
        .split('\n')
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).replace(/^ /, ''))
        .join('\n')
      if (!content || content.startsWith(LEGACY_SOURCES_MARKER)) return

      if (content.startsWith(TOOLS_MARKER)) {
        try {
          const raw: string[] = JSON.parse(content.slice(TOOLS_MARKER.length))
          toolsUsed.value = raw.map((item) => {
            const separator = item.indexOf(':')
            return separator >= 0
              ? { origin: item.slice(0, separator), name: item.slice(separator + 1) }
              : { origin: item, name: '' }
          })
        } catch {
          toolsUsed.value = []
        }
        return
      }

      answer.value += content
    }

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      sseBuffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      const events = sseBuffer.split('\n\n')
      sseBuffer = events.pop() ?? ''
      for (const event of events) handleSseEvent(event)
    }

    sseBuffer += decoder.decode().replace(/\r\n/g, '\n')
    if (sseBuffer.trim()) handleSseEvent(sseBuffer)
  } catch {
    answer.value = '暂时无法连接，请稍后再试。'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section id="chat" class="content-section chat-section" aria-labelledby="chat-title">
    <div class="section-header chat-heading">
      <p class="eyebrow">Portfolio AI</p>
      <h2 id="chat-title">Ask me anything.</h2>
      <p>想快速了解我的经历或项目？直接问就好。</p>
    </div>

    <div class="chat-workspace" :aria-busy="loading">
      <div class="prompt-list" aria-label="推荐问题">
        <button v-for="preset in presets" :key="preset" type="button" @click="ask(preset)">
          {{ preset }}
        </button>
      </div>

      <form class="question-form" @submit.prevent="ask()">
        <label class="sr-only" for="portfolio-question">输入想了解的问题</label>
        <input
          id="portfolio-question"
          v-model="question"
          type="text"
          placeholder="输入一个问题…"
          autocomplete="off"
          :disabled="loading"
        />
        <button type="submit" :disabled="loading" :aria-label="loading ? '正在思考' : '发送问题'">
          {{ loading ? '…' : '→' }}
        </button>
      </form>

      <div v-if="answer" class="chat-answer" aria-live="polite">
        <div class="answer-copy">{{ answer }}</div>

        <div v-if="toolsUsed.length" class="tools-used" aria-label="本轮使用的工具">
          <span v-for="(tool, index) in toolsUsed" :key="index">
            {{ tool.origin === 'mcp' ? 'MCP' : 'Function' }}{{ tool.name ? ' / ' + tool.name : '' }}
          </span>
        </div>
      </div>
    </div>
  </section>
</template>
