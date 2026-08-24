<script setup lang="ts">
import { ref } from 'vue'

const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8080'

interface ToolUsage {
  origin: string
  name: string
}

// Ignore source frames from an older backend during a rolling deployment.
const LEGACY_SOURCES_MARKER = '@@SOURCES@@'
const TOOLS_MARKER = '@@TOOLS@@'

const question = ref('')
const answer = ref('')
const loading = ref(false)
const toolsUsed = ref<ToolUsage[]>([])

const presets = [
  '四年 Java 经验里最自豪的项目？',
  '这个 AI Agent 作品集是怎么实现的？',
  '最近做了哪些 AI 项目？',
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
      if (!content) return

      if (content.startsWith(LEGACY_SOURCES_MARKER)) {
        return
      }

      if (content.startsWith(TOOLS_MARKER)) {
        try {
          const raw: string[] = JSON.parse(content.slice(TOOLS_MARKER.length))
          toolsUsed.value = raw.map((s) => {
            const idx = s.indexOf(':')
            return idx >= 0
              ? { origin: s.slice(0, idx), name: s.slice(idx + 1) }
              : { origin: s, name: '' }
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
      for (const event of events) {
        handleSseEvent(event)
      }
    }
    sseBuffer += decoder.decode().replace(/\r\n/g, '\n')
    if (sseBuffer.trim()) handleSseEvent(sseBuffer)
  } catch (e) {
    answer.value = '请求出错，请确认后端服务正在运行。'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section id="chat" class="editorial-section chat-section" aria-labelledby="chat-title">
    <div class="section-aside">
      <p class="section-index">02</p>
      <p class="section-label">Portfolio agent</p>
    </div>

    <div class="section-body chat-layout">
      <div class="chat-intro">
        <p class="chat-note">An alternate way to browse this portfolio</p>
        <h2 id="chat-title" class="section-title">Ask about<br /><em>my work.</em></h2>
        <p class="section-intro">
          使用完整简历上下文，可调用 Function Calling 与 GitHub MCP。回答会以流式方式自然出现。
        </p>
      </div>

      <div class="chat-workspace" :aria-busy="loading">
        <p class="prompt-label">Start with a question</p>
        <div class="prompt-list">
          <button v-for="p in presets" :key="p" type="button" @click="ask(p)">
            <span>{{ p }}</span>
            <span aria-hidden="true">→</span>
          </button>
        </div>

        <form class="question-form" @submit.prevent="ask()">
          <label class="sr-only" for="portfolio-question">输入想了解的问题</label>
          <input
            id="portfolio-question"
            v-model="question"
            type="text"
            placeholder="输入你的问题…"
            autocomplete="off"
            :disabled="loading"
          />
          <button type="submit" :disabled="loading">
            {{ loading ? 'Thinking' : 'Ask' }}
            <span aria-hidden="true">{{ loading ? '…' : '↗' }}</span>
          </button>
        </form>

        <div v-if="loading" class="chat-loading" role="status" aria-label="正在生成回答">
          <span />
          <span />
          <span />
        </div>

        <div v-if="answer" class="chat-answer" aria-live="polite">
          <p class="answer-label">Response</p>
          <div class="answer-copy">{{ answer }}</div>

          <div v-if="toolsUsed.length" class="tools-used" aria-label="本轮使用的工具">
            <span v-for="(t, i) in toolsUsed" :key="i">
              {{ t.origin === 'mcp' ? 'MCP' : 'Function' }}{{ t.name ? ' / ' + t.name : '' }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>
