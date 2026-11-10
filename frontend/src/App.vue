<script setup lang="ts">
// 应用根组件：仅承载路由出口。用 ElConfigProvider 统一 Element Plus 中文 locale
// （时间选择器/日历/分页等组件的文案默认英文，需 zh-cn 本地化）。
import { watch } from 'vue'
import { useRoute } from 'vue-router'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { applyThemeToDom, useAppStore } from '@/stores/app'

const app = useAppStore()
const route = useRoute()

/**
 * 主题落地的唯一入口：登录 / 回调页盖一层「强制浅色」（route.meta.forceLight），
 * 其余页面按用户选择的主题。store 只在 <html> 上写一次，路由切换时重算即可，
 * 这样深色用户从登录页进主界面不会残留浅色。
 */
watch(
  () => [app.theme, route.meta.forceLight] as const,
  ([theme, forceLight]) => applyThemeToDom(forceLight ? 'light-classic' : theme),
  { immediate: true },
)
</script>

<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
