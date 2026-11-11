import { describe, expect, it } from 'vitest'
import { validateUploadFile, extensionOf } from '@/utils/upload'
import type { FileConfig } from '@/api/file'

const config: FileConfig = {
  maxSize: 1024 * 1024,
  allowedExt: ['.pdf', '.png', '.docx'],
}

function file(name: string, size = 10, type = 'application/octet-stream'): File {
  const blob = new Blob([new Uint8Array(size)], { type })
  return new File([blob], name, { type })
}

describe('extensionOf', () => {
  it('取最后一段扩展名并小写', () => {
    expect(extensionOf('报告.PDF')).toBe('pdf')
    expect(extensionOf('a.tar.gz')).toBe('gz')
    expect(extensionOf('无扩展名')).toBe('')
    expect(extensionOf(undefined)).toBe('')
  })
})

describe('validateUploadFile', () => {
  it('通过时返回 null', () => {
    expect(validateUploadFile(file('周报.docx'), config)).toBeNull()
  })

  it('超限文件给出带体积上限的提示', () => {
    const msg = validateUploadFile(file('大文件.pdf', 2 * 1024 * 1024), config)
    expect(msg).toContain('大文件.pdf')
    expect(msg).toContain('1.0 MB')
  })

  it('扩展名不在白名单时拒绝并列出允许项', () => {
    const msg = validateUploadFile(file('脚本.exe'), config)
    expect(msg).toContain('类型不支持')
    expect(msg).toContain('.pdf')
  })

  it('配置未限制扩展名时放行任意后缀（仍受高危名单约束）', () => {
    expect(validateUploadFile(file('数据.bin'), { maxSize: 1024, allowedExt: [] })).toBeNull()
    expect(validateUploadFile(file('图表.svg'), { maxSize: 1024, allowedExt: [] })).toContain('高危')
  })

  it('空白名单（空数组）等同于不限制', () => {
    expect(validateUploadFile(file('任意.xyz'), { maxSize: 1024, allowedExt: [] })).toBeNull()
  })

  it('双扩展名伪装（invoice.svg.png）被识别为高危', () => {
    expect(validateUploadFile(file('invoice.svg.png'), config)).toContain('高危')
    expect(validateUploadFile(file('page.html.pdf'), config)).toContain('高危')
  })

  it('SCRIPTABLE_EXT 中的类型即使配置允许也拒绝', () => {
    const permissive: FileConfig = { maxSize: 1024, allowedExt: ['svg', 'htm'] }
    expect(validateUploadFile(file('logo.svg'), permissive)).toContain('高危')
    expect(validateUploadFile(file('page.htm'), permissive)).toContain('高危')
  })

  it('skipExtensionCheck 只跳过白名单，不跳过高危名单', () => {
    expect(
      validateUploadFile(file('粘贴图片.bmp'), config, { skipExtensionCheck: true }),
    ).toBeNull()
    expect(
      validateUploadFile(file('x.svg'), config, { skipExtensionCheck: true }),
    ).toContain('高危')
  })

  it('MIME 白名单不匹配时给出可定制文案', () => {
    const msg = validateUploadFile(file('照片.png', 10, 'text/plain'), config, {
      allowedMime: /^image\//,
      mimeLabel: '不是图片',
    })
    expect(msg).toContain('不是图片')
    expect(
      validateUploadFile(file('照片.png', 10, 'image/png'), config, { allowedMime: /^image\// }),
    ).toBeNull()
  })

  it('大小与扩展名同时不合规时先报体积（提示更可操作）', () => {
    const msg = validateUploadFile(file('big.exe', 2 * 1024 * 1024), config)
    expect(msg).toContain('超过上限')
  })
})
