/**
 * 工具栏下拉框（字体 / 字号 / 行距）的选项拼装。
 *
 * 选项表是固定的几档，但段落属性是从 HTML 的 `style` 里解析出来的（`noteTypography`），
 * 老数据、Word/网页粘贴都可能带来不在选项表里的值。`<select>` 找不到匹配项时会**显示空白**，
 * 用户看到的就是「下拉框里没字」，所以这里统一把当前值补成一条选项。
 */

export interface SelectOption {
  label: string
  value: string
}

/** `"Songti SC", SimSun, serif` → `Songti SC`；用于给未知字体一个好认的显示名。 */
export function firstFontFamily(value: string): string {
  const first = value.split(',')[0]?.trim() ?? ''
  return first.replace(/^["']|["']$/g, '').trim() || value
}

/**
 * 当前值不在选项表里时，把它插到最前面显示；空值或已存在时原样返回。
 * 返回值永远是同一份选项 + 最多一条补充项，不修改入参。
 */
export function withCurrentValue(
  options: readonly SelectOption[],
  current: string,
  format: (value: string) => string = (value) => value,
): SelectOption[] {
  const value = current.trim()
  if (!value || options.some((item) => item.value === current)) return [...options]
  return [{ label: format(value), value: current }, ...options]
}
