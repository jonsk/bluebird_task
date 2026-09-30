<script setup lang="ts">
// 应用根组件：仅承载路由出口。用 ElConfigProvider 统一 Element Plus 中文 locale
// （时间选择器/日历/分页等组件的文案默认英文，需 zh-cn 本地化）。
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>

<template>
  <el-config-provider :locale="zhCn">
    <router-view />
  </el-config-provider>
</template>
