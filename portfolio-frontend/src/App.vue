<script setup lang="ts">
import HeroSection from './components/HeroSection.vue'
import ChatSection from './components/ChatSection.vue'
import ProjectsSection from './components/ProjectsSection.vue'
import ContactSection from './components/ContactSection.vue'

function scrollToChat() {
  document.getElementById('chat')?.scrollIntoView({ behavior: 'smooth' })
}
</script>

<template>
  <div class="site-shell">
    <header class="site-header">
      <a class="site-identity" href="#top" aria-label="马驰作品集首页">马驰</a>

      <nav class="site-nav" aria-label="主导航">
        <a href="#projects">Work</a>
        <a href="#chat">Ask</a>
        <a href="https://github.com/tmakerchima" target="_blank" rel="noopener noreferrer">
          GitHub <span aria-hidden="true">↗</span>
        </a>
      </nav>
    </header>

    <main class="site-main">
      <HeroSection @scroll-to-chat="scrollToChat" />
      <ProjectsSection />
      <ChatSection />
      <ContactSection />
    </main>
  </div>
</template>
