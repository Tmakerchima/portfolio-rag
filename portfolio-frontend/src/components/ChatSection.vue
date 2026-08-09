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
  <section id="chat" class="py-24 border-t border-[#eeeeee] dark:border-[#222222]">
    <div class="max-w-[720px] mx-auto">

      <!-- 标题 -->
      <h2 class="text-[42px] md:text-[48px] font-bold text-[#111111] dark:text-[#f5f5f5] mb-4">
        Ask anything
      </h2>
      <p class="mb-10 text-sm leading-relaxed text-[#777777] dark:text-[#999999]">
        这是一个使用完整简历上下文，并可调用 Function Calling 与 GitHub MCP 的简历 AI Agent。
      </p>

      <!-- 预设问题胶囊 -->
      <div class="flex flex-wrap gap-3 mb-8">
        <button
          v-for="p in presets"
          :key="p"
          @click="ask(p)"
          class="px-4 py-2 text-sm border border-[#dddddd] dark:border-[#333333]
                 text-[#444444] dark:text-[#aaaaaa]
                 hover:border-[#0C447C] dark:hover:border-[#5499E0]
                 hover:text-[#0C447C] dark:hover:text-[#5499E0]
                 transition-colors duration-200"
        >
          {{ p }}
        </button>
      </div>

      <!-- 输入框 -->
      <div class="flex flex-col sm:flex-row gap-3">
        <input
          v-model="question"
          @keydown.enter="ask()"
          type="text"
          placeholder="输入你的问题..."
          class="flex-1 px-4 py-3 border border-[#dddddd] dark:border-[#333333]
                 bg-white dark:bg-[#111111] text-[#111111] dark:text-[#f5f5f5]
                 focus:outline-none focus:border-[#0C447C] dark:focus:border-[#5499E0]
                 transition-colors duration-200 text-base"
        />
        <button
          @click="ask()"
          :disabled="loading"
          class="px-6 py-3 bg-[#0C447C] dark:bg-[#5499E0] text-white font-bold
                 hover:opacity-90 disabled:opacity-40 transition-opacity duration-200"
        >
          发送
        </button>
      </div>

      <!-- 加载态：三个跳动的点 -->
      <div v-if="loading" class="mt-10 flex gap-2">
        <span class="w-2 h-2 bg-[#0C447C] dark:bg-[#5499E0] rounded-full animate-bounce [animation-delay:0ms]"></span>
        <span class="w-2 h-2 bg-[#0C447C] dark:bg-[#5499E0] rounded-full animate-bounce [animation-delay:150ms]"></span>
        <span class="w-2 h-2 bg-[#0C447C] dark:bg-[#5499E0] rounded-full animate-bounce [animation-delay:300ms]"></span>
      </div>

      <!-- 流式回答 -->
      <div v-if="answer" class="mt-10">
        <div class="text-[#111111] dark:text-[#f5f5f5] text-base leading-relaxed whitespace-pre-wrap">{{ answer }}</div>

        <!-- 只展示本轮实际调用的动态工具。 -->
        <div v-if="toolsUsed.length" class="mt-4 flex flex-wrap gap-2">
          <span
            v-for="(t, i) in toolsUsed"
            :key="i"
            class="text-xs px-2 py-1 border border-[#dddddd] dark:border-[#333333] text-[#666666] dark:text-[#999999]"
          >
            {{ t.origin === 'mcp' ? '🔌 MCP' : '⚙️ Function Calling' }}{{ t.name ? ' · ' + t.name : '' }}
          </span>
        </div>

      </div>

    </div>
  </section>
</template>
