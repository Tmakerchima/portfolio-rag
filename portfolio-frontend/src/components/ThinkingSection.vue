<script setup lang="ts">
const themes = [
  {
    index: '01',
    title: 'Retrieval before generation',
    text: '先把召回、权限、排序与证据链做扎实，再讨论模型如何组织答案。可靠的 RAG 首先是检索工程。',
  },
  {
    index: '02',
    title: 'Agents need boundaries',
    text: 'Tool Calling 的价值不在于“自主”，而在于清晰的工具契约、可观察的执行过程与可控的失败方式。',
  },
  {
    index: '03',
    title: 'Production is the product',
    text: '延迟、成本、权限和回滚不是上线后的补丁；它们决定了 AI 能力能否真正进入日常工作流。',
  },
]
</script>

<template>
  <section id="thinking" class="editorial-section thinking-section" aria-labelledby="thinking-title">
    <div class="section-aside">
      <p class="section-index">03</p>
      <p class="section-label">Thinking</p>
    </div>

    <div class="section-body">
      <div class="section-heading-row">
        <h2 id="thinking-title" class="section-title">How I think<br />about applied AI.</h2>
        <p class="section-intro">
          不把模型能力当作魔法。更关心证据、边界和系统在真实约束中的表现。
        </p>
      </div>

      <ol class="thinking-list">
        <li v-for="theme in themes" :key="theme.index" class="thinking-item">
          <span>{{ theme.index }}</span>
          <h3>{{ theme.title }}</h3>
          <p>{{ theme.text }}</p>
        </li>
      </ol>

      <a
        class="text-action thinking-link"
        href="https://tmakerchima.github.io"
        target="_blank"
        rel="noopener noreferrer"
      >
        Read notes on my blog <span aria-hidden="true">↗</span>
      </a>
    </div>
  </section>
</template>
