<script setup lang="ts">
import { ref } from 'vue'

const expanded = ref<number | null>(null)

const projects = [
  {
    name: '额度中心项目',
    period: '2021-11 ~ 2022-09',
    tags: ['Java', 'Spring Boot', 'easyflow', 'ESB', '宁波银行'],
    highlight: '帮助宁波银行网贷额度系统成功上线，兼容多渠道统一额度管理。',
    detail: '基于 easyflow 构建统一网贷额度中心，通过 ESB 接口实现高效数据交换，针对微信渠道设计个性化流程。方案兼容多渠道，统一额度管理与访问控制，于 2022 年 9 月成功上线，运行稳定。',
  },
  {
    name: 'Hadoop → Databricks 数据迁移',
    period: '2024-07 ~ 2025-01',
    tags: ['Hadoop HDFS', 'Databricks', 'Delta Lake', 'Python', '美国道富'],
    highlight: '将企业级 HDFS 数据迁移至 Databricks，利用 Auto Loader 实现增量入湖。',
    detail: '利用 Databricks Auto Loader 实现增量数据入湖，转换为 Delta Lake 格式优化查询性能。优化并行任务调度，解决 Hive 元数据兼容性问题，通过校验脚本保障数据一致性。',
  },
  {
    name: '仓库上架推荐系统',
    period: '2023-10 ~ 2024-01',
    tags: ['Python', 'NSGA-II', '模拟退火', '算法', 'GitHub开源'],
    highlight: '自研遗传算法（NSGA-II）智能推荐货物摆放方案，已在 GitHub 开源。',
    detail: '系统采用 Python 开发，核心算法包括 NSGA-II 遗传算法和模拟退火算法，综合考虑库存成本、货物需求和上架效率，智能推荐最优货物摆放方案。已在 GitHub 开源，便于社区参与贡献。',
  },
  {
    name: '室内定位系统',
    period: '2024-01 ~ 2024-04',
    tags: ['Spring Boot', 'Android', 'Weight KNN', 'Wi-Fi指纹', '蓝牙'],
    highlight: '基于 Wi-Fi 指纹和 Weight KNN 算法实现高精度室内定位，Web 实时展示位置。',
    detail: '离线阶段用安卓端采集室内 Wi-Fi 信号强度、蓝牙等指纹数据，在线阶段采用 Weight KNN 算法进行加权模式匹配，实现高精度位置估算，结果通过前端网页实时展示。后端基于 Spring Boot 构建。',
  },
]
</script>

<template>
  <section id="projects" class="py-24 border-t border-[#eeeeee] dark:border-[#222222]">
    <h2 class="text-[48px] font-bold text-[#111111] dark:text-[#f5f5f5] mb-12">
      Projects
    </h2>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
      <div
        v-for="(p, i) in projects"
        :key="i"
        class="border border-[#eeeeee] dark:border-[#222222] p-6 cursor-pointer
               hover:border-[#0C447C] dark:hover:border-[#5499E0]
               transition-colors duration-200"
        @click="expanded = expanded === i ? null : i"
      >
        <!-- 项目名 + 时间 -->
        <div class="flex justify-between items-start mb-3">
          <h3 class="text-lg font-bold text-[#111111] dark:text-[#f5f5f5]">{{ p.name }}</h3>
          <span class="text-xs text-[#999999] ml-4 shrink-0">{{ p.period }}</span>
        </div>

        <!-- 技术栈胶囊 -->
        <div class="flex flex-wrap gap-2 mb-4">
          <span
            v-for="tag in p.tags"
            :key="tag"
            class="px-2 py-1 text-xs bg-[#f5f5f5] dark:bg-[#1a1a1a]
                   text-[#0C447C] dark:text-[#5499E0]"
          >
            {{ tag }}
          </span>
        </div>

        <!-- 一句话亮点 -->
        <p class="text-sm text-[#444444] dark:text-[#aaaaaa] leading-relaxed">
          {{ p.highlight }}
        </p>

        <!-- 展开详情 -->
        <div
          v-if="expanded === i"
          class="mt-4 pt-4 border-t border-[#eeeeee] dark:border-[#222222]
                 text-sm text-[#666666] dark:text-[#888888] leading-relaxed"
        >
          {{ p.detail }}
        </div>

        <div class="mt-4 text-xs text-[#cccccc] dark:text-[#444444]">
          {{ expanded === i ? '收起 ▴' : '展开详情 ▾' }}
        </div>
      </div>
    </div>
  </section>
</template>
