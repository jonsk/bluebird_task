<script setup lang="ts">
import { onMounted } from 'vue'
import { Expand } from '@element-plus/icons-vue'
import BbFilterRail from '@/components/bb/BbFilterRail.vue'
import { useAppStore } from '@/stores/app'
import { useTaskStore } from '@/stores/task'

/**
 * 唯一布局（旧 `.main-box`，03 §5.2）：左栏 BbFilterRail + 主区 router-view。
 * 旧系统无顶部 header —— 用户块/退出在左栏顶部；左栏可由 Fold 整体收起（旧 AppMain 的展开按钮）。
 */
const app = useAppStore()
const taskStore = useTaskStore()

onMounted(() => {
  void taskStore.fetchCounts()
})
</script>

<template>
  <div class="layout">
    <BbFilterRail v-show="!app.leftCollapsed" />

    <main class="layout__main">
      <el-tooltip v-if="app.leftCollapsed" content="展开菜单" placement="bottom" effect="light">
        <el-icon class="layout__expand" data-test="rail-expand" @click="app.toggleLeft()"><Expand /></el-icon>
      </el-tooltip>
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: var(--bg-secondary);
}
.layout__main {
  position: relative;
  flex: 1;
  min-width: 0;
  height: 100vh;
  overflow: auto;
}
.layout__expand {
  position: absolute;
  top: 18px;
  left: 10px;
  z-index: 10;
  font-size: 18px;
  color: var(--bb-text-secondary);
  cursor: pointer;
}
</style>
