import js from '@eslint/js'
import tseslint from 'typescript-eslint'
import pluginVue from 'eslint-plugin-vue'
import globals from 'globals'
import prettier from 'eslint-config-prettier'

// 单制品前端 ESLint 配置（flat config，eslint 10）。
// 开启全部 recommended 规则集（js / typescript-eslint / vue），接住真实代码质量问题；
// 仅「与 prettier 冲突的格式规则」交给 prettier 处理（eslint-config-prettier 最后生效）。
export default tseslint.config(
  {
    // 生成产物、第三方、自动生成的声明文件、真实后端联调用例不作校验。
    ignores: [
      'dist',
      'node_modules',
      'public',
      '.output',
      // 真实后端联调用例：需线下环境，不在默认 lint 范围。
      '**/e2e-real/**',
      '**/auto-imports.d.ts',
      '**/components.d.ts',
      'src/types/api.d.ts',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    // .vue 的 <script> 走 TypeScript 解析器。
    files: ['**/*.vue'],
    languageOptions: {
      parserOptions: {
        parser: tseslint.parser,
      },
    },
  },
  {
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node,
      },
    },
    rules: {
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      '@typescript-eslint/no-empty-object-type': 'off',
      // 安全消毒正则主动匹配 [\x00-\x1f] 控制字符，属有意使用；
      // 仅在 sanitize.ts 对应行针对性禁用，规则本身保持开启。
      'no-control-regex': 'error',
      // 以下原为保证存量代码、避免大批量改文件的风格/约定规则；
      // 按「开启全部」要求重新启用，由 vue flat/recommended 决定具体级别。
      'vue/require-default-prop': 'error',
      'vue/attributes-order': 'warn',
    },
  },
  // 必须最后，关闭所有与 prettier 冲突的格式规则。
  prettier,
)
