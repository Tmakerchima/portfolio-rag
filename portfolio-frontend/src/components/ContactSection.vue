<script setup lang="ts">
const links = [
  {
    label: 'GitHub',
    href: 'https://github.com/tmakerchima',
    desc: 'tmakerchima',
  },
  {
    label: 'Blog',
    href: 'https://tmakerchima.github.io',
    desc: 'tmakerchima.github.io',
  },
  {
    label: 'Email',
    href: 'mailto:709851837@qq.com',
    desc: '709851837@qq.com',
  },
]
</script>

<template>
  <section id="contact" class="py-24 border-t border-[#eeeeee] dark:border-[#222222]">
    <h2 class="text-[48px] font-bold text-[#111111] dark:text-[#f5f5f5] mb-12">
      Get in touch
    </h2>

    <div class="flex flex-col gap-6 max-w-[480px]">
      <a
        v-for="link in links"
        :key="link.label"
        :href="link.href"
        target="_blank"
        rel="noopener noreferrer"
        class="flex items-center justify-between py-4 border-b border-[#eeeeee] dark:border-[#222222]
               group hover:border-[#0C447C] dark:hover:border-[#5499E0] transition-colors duration-200"
      >
        <span class="text-sm text-[#999999] w-16">{{ link.label }}</span>
        <span class="text-base text-[#111111] dark:text-[#f5f5f5]
                     group-hover:text-[#0C447C] dark:group-hover:text-[#5499E0]
                     transition-colors duration-200">
          {{ link.desc }}
        </span>
        <span class="text-[#cccccc] group-hover:text-[#0C447C] dark:group-hover:text-[#5499E0]
                     transition-colors duration-200">→</span>
      </a>
    </div>

    <p class="mt-16 mb-8 text-sm text-[#cccccc] dark:text-[#444444]">
      © 2026 马驰 · Built with Spring AI + Vue 3
    </p>
  </section>
</template>
