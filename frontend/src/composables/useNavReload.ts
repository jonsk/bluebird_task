import { ref } from 'vue'

/**
 * 左侧菜单点击信号。
 *
 * 点当前页面对应的菜单项时 `router.push` 是「重复导航」不会重新挂载组件，
 * 页面就收不到任何通知（比如「每周工作记录」需要每次点菜单都重新校验当周记录）。
 * 这里用一个模块级 ref 广播点击，页面按路由名自行决定要不要重新取数。
 */
export interface NavSelectSignal {
  /** 目标路由 name（与 routes.ts 一致） */
  name: string
  /** 时间戳：同名路由连续点击也要能触发（值每次都变） */
  at: number
}

export const navSelect = ref<NavSelectSignal | null>(null)

/** 菜单项被点击时调用（无论是否需要真的跳转）。 */
export function notifyNavSelect(name: string): void {
  navSelect.value = { name, at: Date.now() }
}
