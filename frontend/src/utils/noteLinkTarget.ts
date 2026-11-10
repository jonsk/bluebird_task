/**
 * 站内日志链接（`/notes/123`）的点击识别。
 *
 * 周报正文里的引用由后端渲染成 `<a href="/notes/{id}">`，编辑器里 `openOnClick` 是关掉的，
 * 所以这里单独判断点击目标是不是这种链接，交给调用方决定跳转方式。
 */

export const NOTE_LINK_PATTERN = /^\/notes\/\d+$/

/**
 * 从点击目标向上找最近的 `<a>`，只返回站内日志链接；其它情况一律返回 null，
 * 保证编辑器里普通点击（定位光标）和外部链接的既有行为不受影响。
 */
export function noteLinkHref(target: EventTarget | null, container?: HTMLElement | null): string | null {
  const node = target instanceof Element ? target : target instanceof Node ? target.parentElement : null
  if (!node) return null
  const anchor = node.closest('a')
  if (!(anchor instanceof HTMLAnchorElement)) return null
  if (container && !container.contains(anchor)) return null
  const href = anchor.getAttribute('href') ?? ''
  return NOTE_LINK_PATTERN.test(href) ? href : null
}
