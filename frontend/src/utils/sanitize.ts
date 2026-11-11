/**
 * 富文本 HTML 消毒（零依赖，基于浏览器原生 DOMParser）。
 *
 * 背景：日志正文 / 周报正文以 HTML 落库，渲染链路目前靠 Tiptap 的 inert DOMParser +
 * ProseMirror schema 白名单兜底。这是**单点防线**：一旦以后换了渲染器、把内容塞进
 * `v-html`、或导出成静态页，未消毒的历史数据就会变成存储型 XSS。
 *
 * 因此这里在「HTML 进入编辑器之前」再加一层保守消毒，与后端 RichTextGuard 的
 * 危险载荷拒绝共同构成第二、第三道防线。策略是**只删明确危险的东西**，其余标签、
 * 属性（Tiptap 依赖大量 `data-type` / `data-checked` / `class` / `style`）原样保留，
 * 避免破坏 details 折叠、任务列表、表格、高亮、缩进、附件卡片等既有内容。
 *
 * 已知边界：DOMParser 不会执行脚本，但会规范化序列化结果（补 tbody、属性加引号）。
 * ⚠️ 数据副作用：被删标签（iframe/video/audio/svg/style 等）连同内部文本**整段消失**。
 * `toEditorHtml` 在载入编辑器前就跑本函数，而编辑器内容会作为 `lastSaved` 基线，
 * 因此打开含此类内容的历史文档并保存，会**静默永久移除**这些嵌入内容。载入时应先用
 * `containsDangerousRichText` 检测并提示用户，避免在不知情下丢失数据。
 */

/** 连同内部文本一起移除的标签（details/summary 是业务节点，不能列入）。 */
const DANGEROUS_SELECTOR = [
  'script',
  'style',
  'iframe',
  'object',
  'embed',
  'applet',
  'link',
  'meta',
  'base',
  'form',
  'button',
  'select',
  'textarea',
  'svg',
  'math',
  'template',
  'noscript',
  'frame',
  'frameset',
  'audio',
  'video',
  'source',
  'track',
  'portal',
  'marquee',
].join(',')

/** 无条件移除的属性（事件属性按前缀另行判断）。 */
const FORBIDDEN_ATTRS = new Set(['srcdoc', 'formaction', 'ping', 'attributionsrc', 'xmlns'])

/** 承载 URL 的属性，需过协议白名单。 */
const URL_ATTRS = new Set(['href', 'src', 'xlink:href', 'action', 'poster', 'background', 'cite', 'longdesc'])

/** WHATWG URL 会忽略 tab/newline/CR，检测前必须先剔除控制字符与空白。 */
// eslint-disable-next-line no-control-regex -- 本正则刻意匹配控制字符用于剥离危险 URL
const CONTROL_OR_SPACE = /[\u0000-\u0020\u007f]+/g
const UNSAFE_PROTOCOL = /^(?:javascript|vbscript|file|blob)\s*:/i
const SAFE_SCHEME = /^(?:https?:|mailto:|tel:)/i
const ANY_SCHEME = /^[a-z][a-z0-9+.-]*:/i
/** 历史数据里可能有 base64 位图；Tiptap Image 关闭了 allowBase64，这里只放行位图。 */
const SAFE_DATA_IMAGE = /^data:image\/(?:png|jpe?g|gif|webp);base64,[a-z0-9+/=]+$/i
const UNSAFE_CSS = /url\s*\(|expression\s*\(|@import|behaviou?r\s*:|javascript\s*:/i

/** 任务列表由 Tiptap 渲染成 `<input type=checkbox>`；除这几个属性外一律剥掉。 */
const INPUT_ALLOWED_ATTRS = new Set(['type', 'checked', 'disabled'])

function isSafeUrl(raw: string): boolean {
  const compact = raw.replace(CONTROL_OR_SPACE, '')
  if (!compact) return true
  if (SAFE_DATA_IMAGE.test(compact)) return true
  if (UNSAFE_PROTOCOL.test(compact)) return false
  // 相对路径 / 锚点 / 站内绝对路径允许；其余显式 scheme 一律拒绝。
  return !ANY_SCHEME.test(compact) || SAFE_SCHEME.test(compact)
}

/** 检测 HTML 是否含会被本消毒器整段删除的危险标签（用于载入时提示，避免静默数据丢失）。 */
export function containsDangerousRichText(html: string): boolean {
  if (!html || typeof DOMParser === 'undefined') return false
  const doc = new DOMParser().parseFromString(html, 'text/html')
  // 必须从 documentElement 查：以 <style>/<link>/<meta> 开头的片段会被解析进 <head>，
  // 只查 body 会漏检（这些标签恰恰在危险名单里，漏检就不提示、保存时静默丢失）。
  return doc.documentElement.querySelector(DANGEROUS_SELECTOR) !== null
}

/** 消毒富文本片段；无 DOMParser 的环境（SSR）原样返回，由后端防线兜底。 */
export function sanitizeRichTextHtml(html: string): string {
  if (!html) return ''
  if (typeof DOMParser === 'undefined') return html

  const doc = new DOMParser().parseFromString(html, 'text/html')
  for (const el of Array.from(doc.body.querySelectorAll(DANGEROUS_SELECTOR))) el.remove()

  for (const el of Array.from(doc.body.querySelectorAll('*'))) {
    const tag = el.tagName.toLowerCase()
    for (const attr of Array.from(el.attributes)) {
      const name = attr.name.toLowerCase()
      if (
        name.startsWith('on') ||
        FORBIDDEN_ATTRS.has(name) ||
        (tag === 'input' && !INPUT_ALLOWED_ATTRS.has(name)) ||
        (URL_ATTRS.has(name) && !isSafeUrl(attr.value)) ||
        (name === 'style' && UNSAFE_CSS.test(attr.value))
      ) {
        el.removeAttribute(attr.name)
      }
    }
    // Tiptap TaskItem 的 checkbox 不允许承载任意 type。
    if (tag === 'input' && el.getAttribute('type')?.toLowerCase() !== 'checkbox') {
      el.setAttribute('type', 'checkbox')
    }
    if (el.getAttribute('target') === '_blank') {
      const rel = el.getAttribute('rel') ?? ''
      if (!/\bnoopener\b/.test(rel)) el.setAttribute('rel', `${rel} noopener noreferrer`.trim())
    }
  }
  return doc.body.innerHTML
}
