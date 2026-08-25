<script setup lang="ts">
defineEmits<{ 'scroll-to-chat': [] }>()
</script>

<template>
  <section id="top" class="hero-section" aria-labelledby="hero-title">
    <p class="eyebrow reveal">Java + AI Engineer</p>

    <h1 id="hero-title" class="hero-title reveal reveal-two">
      I build useful<br />AI systems.
    </h1>

    <p class="hero-deck reveal reveal-three">
      我是马驰。用 Java、RAG 与 Agent，把 AI 做成可靠、可上线的产品。
    </p>

    <div class="hero-actions reveal reveal-four">
      <a class="primary-action" href="#projects">查看项目</a>
      <button class="secondary-action" type="button" @click="$emit('scroll-to-chat')">
        问问我的 AI <span aria-hidden="true">→</span>
      </button>
    </div>
  </section>
</template>
