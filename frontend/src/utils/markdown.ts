/**
 * Markdown 纯文本化工具。
 *
 * 后端 `NoteQueryService.summary()` 下发的是原文前 200 码位（不做解析），
 * 列表卡片 / 右栏小列表 / 新建草稿卡片直接展示会露出 `##`、`**`、围栏等符号。
 * 这里在展示层做一次轻量清洗，只用于摘要，不参与存储与搜索。
 */
export function stripMarkdown(src?: string | null): string {
  if (!src) return ''

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
