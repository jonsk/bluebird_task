/**
 * 展示与字符串小工具。
 *
 * 这些函数原先在 NoteEditor / WeeklyReportView / NoteAttachmentList / NoteAttachmentNode /
 * BbAttachmentList / api-file / api-download 各写了一份（口径还略有差异），统一收敛到这里。
 * 纯函数、无 Vue / 网络依赖，可安全用于单测环境。
 */

/** 后端 updatedAt / 本地时间统一显示为 HH:mm；非法日期返回空串。 */
export function formatClock(input?: string | Date | null): string {
  const date = input ? new Date(input) : new Date()
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleTimeString('zh-CN', {
    hour12: false,
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** 文件体积：B / KB / MB（0 或空值返回空串，供「无大小」场景省略展示）。 */
export function formatFileSize(size?: number | null): string {
  if (!size || size <= 0) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

/**
 * 解析 Content-Disposition 中的文件名（优先 RFC 5987 的 filename*=UTF-8''）。
 * 后端可能返回带引号或裸值；解码失败时回退到普通 filename，再失败用 fallback。
 */
export function parseContentDisposition(disposition?: string | null, fallback = 'download'): string {
  if (!disposition) return fallback
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
  if (star?.[1]) {
    try {
      return decodeURIComponent(star[1])
    } catch {
      /* 落到普通 filename */
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition)
  return plain?.[1]?.trim() || fallback
}

/**
 * 站内跳转白名单：只接受「以单个 / 开头」的相对路径。
 *
 * 后端 OidcLoginController 同样拒绝 `//`，前后端口径保持一致。这里额外防：
 * - `//evil.com`、`/\\evil.com`（浏览器会当成协议相对 URL）；
 * - 百分号编码绕过（`/ %2f%2fevil.com`、`/%2f%2fevil.com` 解码后再校验）；
 * - 控制字符与反斜杠（部分浏览器会归一化成路径分隔符）；
 * - 任何显式 scheme（`javascript:`、`http:`…）。
 *
 * 返回原始（未解码）值，保留 query / hash 里的合法编码。
 */
export function safeRedirect(target: unknown, fallback = '/index'): string {
  if (typeof target !== 'string' || !target) return fallback
  const value = target.trim()
  // eslint-disable-next-line no-control-regex -- 刻意匹配控制字符与反斜杠，防路径穿越
  if (!value.startsWith('/') || /[\u0000-\u001f\u007f\\]/.test(value)) return fallback

  let decoded: string
  try {
    decoded = decodeURIComponent(value)
  } catch {
    decoded = value
  }
  // eslint-disable-next-line no-control-regex -- 解码后再次剔除控制字符与反斜杠
  if (/[\u0000-\u001f\u007f\\]/.test(decoded)) return fallback
  if (!decoded.startsWith('/') || decoded.startsWith('//') || /^[a-z][a-z0-9+.-]*:/i.test(decoded)) {
    return fallback
  }
  return value
}

/** 统一错误提示：BizError / HttpError 都是 Error 子类；字符串（如 ElMessageBox 的 cancel）不回显。 */
export function toErrorMessage(e: unknown, fallback = '操作失败'): string {
  if (e instanceof Error) return e.message || fallback
  return fallback
}
