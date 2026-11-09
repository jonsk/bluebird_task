/**
 * 日志摘要纯文本化工具。
 *
 * 后端 `contentSummary` 已经是剥离过标记的前 200 码位，但历史数据仍可能是 Markdown。
 * 列表卡片 / 右栏小列表 / 详情页标题统一走这里：
 * - HTML 正文（Tiptap 新格式）→ 去标签；
 * - Markdown 正文（编辑器升级前的历史格式）→ 去语法符号。
 *
 * 只用于展示，不参与存储与搜索。
 */
import { isHtmlContent, plainTextFromHtml } from './richText'

export function stripMarkdown(src?: string | null): string {
  if (!src) return ''

  if (isHtmlContent(src)) {
    return plainTextFromHtml(src).replace(/\s+/g, ' ').trim()
  }

  let text = src.replace(/\r\n?/g, '\n')

  // 代码块 / 围栏
  text = text.replace(/```[\s\S]*?```/g, ' ')
  text = text.replace(/~~~[\s\S]*?~~~/g, ' ')
  // 行内代码
  text = text.replace(/`([^`\n]*)`/g, '$1')
  // 图片 / 链接：保留可读文字
  text = text.replace(/!\[([^\]]*)\]\([^)]*\)/g, '$1')
  text = text.replace(/\[([^\]]*)\]\([^)]*\)/g, '$1')
  // 标题 / 引用 / 列表 / 分隔线
  text = text.replace(/^[ \t]{0,3}#{1,6}[ \t]+/gm, '')
  text = text.replace(/^[ \t]{0,3}>[ \t]?/gm, '')
  text = text.replace(/^[ \t]{0,3}(?:[-*+]|\d+[.)])[ \t]+/gm, '')
  text = text.replace(/^[ \t]{0,3}([-*_])(?:[ \t]*\1){2,}[ \t]*$/gm, ' ')
  // 强调 / 删除线
  text = text.replace(/(\*\*|__)(.*?)\1/g, '$2')
  text = text.replace(/~~(.*?)~~/g, '$1')
  text = text.replace(/(\*|_)(.*?)\1/g, '$2')

  return text.replace(/\s+/g, ' ').trim()
}
