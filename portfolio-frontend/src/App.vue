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
  <div class="min-h-screen bg-white text-[#111111] dark:bg-[#0a0a0a] dark:text-[#f5f5f5]">
    <main class="max-w-[1200px] mx-auto px-6">
      <HeroSection @scroll-to-chat="scrollToChat" />
      <ChatSection />
      <ProjectsSection />
      <ContactSection />
    </main>
  </div>
</template>
