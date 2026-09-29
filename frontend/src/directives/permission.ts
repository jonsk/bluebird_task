import type { App, Directive } from 'vue'
import { useAuthStore } from '@/stores/auth'
import type { RoleCode } from '@/utils/constants'

/**
 * v-permission 指令（03 §4.3）：按 role_code 控制按钮显隐（无按钮级权限表）。
 * 用法：<el-button v-permission="['ADMIN']">…</el-button>
 */
const permission: Directive<HTMLElement, RoleCode[]> = {
  mounted(el, binding) {
    const roles = binding.value
    if (roles && roles.length > 0 && !useAuthStore().hasRole(...roles)) {
      el.parentNode?.removeChild(el)
    }
  },
}

export function setupDirectives(app: App): void {
  app.directive('permission', permission)
}
