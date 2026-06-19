<script setup lang="ts">
defineEmits<{ 'scroll-to-chat': [] }>()
</script>

<template>
  <section class="min-h-screen flex flex-col justify-center py-24">
    <h1 class="text-[72px] font-bold leading-tight tracking-tight text-[#111111] dark:text-[#f5f5f5]">
      马驰 <span class="text-[#0C447C] dark:text-[#5499E0]">/</span> Mac Ma
    </h1>

    <p class="text-2xl text-[#666666] dark:text-[#999999] mt-4 font-normal">
      Java Backend Engineer · 4 Years · Ningbo
    </p>

    <p class="text-lg text-[#444444] dark:text-[#bbbbbb] mt-8 max-w-[720px] leading-relaxed">
      毕业于浙江工业大学软件工程专业，先后在杭州云融、乐歌人体工学及美国道富担任后端开发工程师。
      熟悉 Spring Boot / Java 后端开发，有银行金融系统、WMS 仓储系统及大规模数据迁移（Hadoop → Databricks）实战经验。
      对 AIGC 和大模型应用落地有浓厚兴趣，正在探索 AI 与金融科技的结合。
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
