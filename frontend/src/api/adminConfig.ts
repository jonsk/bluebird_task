import { get, post, put } from './http'
import type { components } from '@/types/api'

/**
 * 配置视图类型：从 openapi 生成类型派生（03 §2.7，不手写响应类型），
 * 但把「后端必返、openapi 因未标 required 而生成 optional」的字段收敛为必填，
 * 便于视图层直接用，不必到处写 `?? ''`。
 */
type GenItem = components['schemas']['ConfigItemVO']
type GenGroup = components['schemas']['ConfigGroupVO']

export type ConfigItemVO = Required<
  Pick<GenItem, 'key' | 'label' | 'type' | 'secret' | 'hasSecret' | 'source' | 'requiresRestart'>
> & {
  value: string
  description: string
  options: string[]
}

export type ConfigGroupVO = Required<Pick<GenGroup, 'group' | 'label'>> & {
  description: string
  items: ConfigItemVO[]
}

export type ConfigTestResult = components['schemas']['ConfigTestResult']

function normalizeItem(raw: GenItem): ConfigItemVO {
  return {
    key: raw.key ?? '',
    label: raw.label ?? '',
    type: raw.type ?? 'STRING',
    secret: raw.secret ?? false,
    hasSecret: raw.hasSecret ?? false,
    source: raw.source ?? 'DEFAULT',
    requiresRestart: raw.requiresRestart ?? false,
    value: raw.value ?? '',
    description: raw.description ?? '',
    options: raw.options ?? [],
  }
}

function normalizeGroup(raw: GenGroup): ConfigGroupVO {
  return {
    group: raw.group ?? '',
    label: raw.label ?? '',
    description: raw.description ?? '',
    items: (raw.items ?? []).map(normalizeItem),
  }
}

/** 全部配置分组（密文项 value 恒为掩码 `******`）。 */
export async function listConfigGroups(): Promise<ConfigGroupVO[]> {
  const raw = await get<GenGroup[]>('/admin/config')
  return raw.map(normalizeGroup)
}

export async function getConfigGroup(group: string): Promise<ConfigGroupVO> {
  return normalizeGroup(await get<GenGroup>(`/admin/config/${group}`))
}

/**
 * 保存分组配置。
 *
 * 语义（后端约定）：值 `null` 或掩码 `******` 表示不修改；空串表示清空该项。
 */
export async function saveConfigGroup(
  group: string,
  values: Record<string, string | null>,
): Promise<ConfigGroupVO> {
  return normalizeGroup(await put<GenGroup>(`/admin/config/${group}`, { values }))
}

/** 测试 Casdoor 连通性（拉 .well-known/openid-configuration）。 */
export function testCasdoor(): Promise<ConfigTestResult> {
  return post<ConfigTestResult>('/admin/config/casdoor/test')
}
