/**
 * md-editor-v3 全局配置（按需调用）。
 *
 * 组件默认会在运行时从 `https://unpkg.com` 拉取 highlight.js / prettier /
 * cropperjs / screenfull / mermaid / katex / echarts。这里只把**代码高亮**换成
 * 本地打包的 highlight.js（仅注册常用语言，控制体积）；其余扩展保持组件默认
 * （按需从 CDN 加载，内网可访问外网）。
 *
 * 注意：本模块**不要**在 main.ts 静态导入，否则会把 md-editor-v3 与
 * CodeMirror 一起塞进首屏 chunk；只在异步加载编辑器的分支里调用。
 */
import { config } from 'md-editor-v3'
import hljs from 'highlight.js/lib/core'
import bash from 'highlight.js/lib/languages/bash'
import css from 'highlight.js/lib/languages/css'
import java from 'highlight.js/lib/languages/java'
import javascript from 'highlight.js/lib/languages/javascript'
import json from 'highlight.js/lib/languages/json'
import markdown from 'highlight.js/lib/languages/markdown'
import python from 'highlight.js/lib/languages/python'
import sql from 'highlight.js/lib/languages/sql'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import yaml from 'highlight.js/lib/languages/yaml'
import 'highlight.js/styles/github.css'

const languages = {
  bash,
  css,
  java,
  javascript,
  json,
  markdown,
  python,
  sql,
  typescript,
  xml,
  yaml,
}

let configured = false

/** 幂等：编辑器与只读预览可能先后加载，重复调用不会重复注册语言。 */
export function setupMarkdown(): void {
  if (configured) return
  configured = true

  for (const [name, language] of Object.entries(languages)) {
    hljs.registerLanguage(name, language)
  }

  config({ editorExtensions: { highlight: { instance: hljs } } })
}
