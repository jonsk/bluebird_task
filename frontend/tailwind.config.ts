import type { Config } from 'tailwindcss'

// R8：禁用 preflight，避免重置 Element Plus 组件样式；业务 reset 由 styles/tokens.css 精细控制。
export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  corePlugins: {
    preflight: false,
  },
  theme: {
    extend: {
      colors: {
        // 语义色走 Design Tokens（CSS 变量），见 styles/tokens.css
        primary: 'var(--bb-color-primary)',
        success: 'var(--bb-color-success)',
        warning: 'var(--bb-color-warning)',
        danger: 'var(--bb-color-danger)',
        overdue: 'var(--bb-color-overdue)',
        near: 'var(--bb-color-near)',
      },
    },
  },
  plugins: [],
} satisfies Config
