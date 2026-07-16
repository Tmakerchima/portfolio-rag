<script setup lang="ts">
defineEmits<{ 'scroll-to-chat': [] }>()
</script>

<template>
  <section class="min-h-screen flex flex-col justify-center py-20 md:py-24">
    <div class="mb-8 flex flex-wrap gap-2" aria-label="Core skills">
      <span class="border border-[#dbe7f2] bg-[#f4f8fc] px-3 py-1 text-xs font-bold tracking-wide text-[#0C447C]
                   dark:border-[#26384b] dark:bg-[#101923] dark:text-[#78b7f5]">
        JAVA / SPRING BOOT
      </span>
      <span class="border border-[#dbe7f2] bg-[#f4f8fc] px-3 py-1 text-xs font-bold tracking-wide text-[#0C447C]
                   dark:border-[#26384b] dark:bg-[#101923] dark:text-[#78b7f5]">
        AI AGENT / RAG / MCP
      </span>
    </div>

    <h1 class="text-[44px] md:text-[72px] font-bold leading-tight tracking-tight text-[#111111] dark:text-[#f5f5f5]">
      马驰 <span class="text-[#0C447C] dark:text-[#5499E0]">/</span> Mac Ma
    </h1>

    <p class="text-xl md:text-2xl text-[#666666] dark:text-[#999999] mt-4 font-normal">
      Java Backend Engineer <span class="text-[#0C447C] dark:text-[#5499E0]">×</span> AI Agent Engineer
    </p>

    <p class="text-lg text-[#444444] dark:text-[#bbbbbb] mt-8 max-w-[720px] leading-relaxed">
      4 年企业级开发经验，主线是 Java / Spring Boot 后端工程，也在把 LLM 变成真正可用的产品能力。
      做过银行核心业务系统、WMS 与 Hadoop → Databricks 数据迁移，也独立落地了基于 Spring AI 的
      RAG、Tool Calling、MCP 与流式交互应用。
    </p>

    <div class="mt-12">
      <button
        @click="$emit('scroll-to-chat')"
        class="inline-flex items-center gap-2 px-8 py-4 text-lg font-bold
               bg-[#0C447C] dark:bg-[#5499E0] text-white
               hover:opacity-90 transition-opacity duration-200"
      >
        Ask me anything
        <span class="text-xl">→</span>
      </button>
    </div>

    <div class="mt-24 text-[#cccccc] dark:text-[#444444] text-sm tracking-widest">
      SCROLL
    </div>
  </section>
</template>
