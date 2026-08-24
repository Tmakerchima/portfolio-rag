<script setup lang="ts">
defineEmits<{ 'scroll-to-chat': [] }>()
</script>

<template>
  <section id="top" class="hero-section" aria-labelledby="hero-title">
    <div class="hero-kicker reveal reveal-one">
      <span>Java Backend</span>
      <span>AI Agents</span>
      <span>RAG Systems</span>
    </div>

    <h1 id="hero-title" class="hero-title reveal reveal-two">
      I build reliable systems<br />
      that make AI <em>useful.</em>
    </h1>

    <div class="hero-intro reveal reveal-three">
      <p class="hero-deck">
        我是马驰，一名把企业级后端经验带入 AI 产品工程的开发者。
        关注检索质量、Agent 工作流，以及模型能力如何真正进入生产系统。
      </p>

      <button class="text-action" type="button" @click="$emit('scroll-to-chat')">
        和我的 Portfolio Agent 聊聊
        <span aria-hidden="true">→</span>
      </button>
    </div>

    <div class="hero-foot reveal reveal-four" aria-label="个人简介摘要">
      <p><span>01 / Practice</span>Java · Spring Boot · Distributed systems</p>
      <p><span>02 / Focus</span>RAG · Agents · MCP · Retrieval</p>
      <p><span>03 / Based</span>Shanghai, China · Open to thoughtful work</p>
    </div>
  </section>
</template>
