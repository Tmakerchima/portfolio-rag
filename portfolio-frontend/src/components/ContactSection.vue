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
  <footer id="contact" class="site-footer" aria-labelledby="contact-title">
    <div class="footer-kicker">
      <span>05</span>
      <span>Get in touch</span>
    </div>

    <div class="footer-main">
      <h2 id="contact-title">Let’s build something<br /><em>useful.</em></h2>

      <div class="contact-links">
        <a
          v-for="link in links"
          :key="link.label"
          :href="link.href"
          target="_blank"
          rel="noopener noreferrer"
        >
          <span>{{ link.label }}</span>
          <strong>{{ link.desc }}</strong>
          <i aria-hidden="true">↗</i>
        </a>
      </div>
    </div>

    <div class="footer-meta">
      <p>© 2026 马驰 / Mac Ma</p>
      <p>Built with Spring AI + Vue 3</p>
      <a href="#top">Back to top ↑</a>
    </div>
  </footer>
</template>
