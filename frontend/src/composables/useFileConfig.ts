import { ref, onUnmounted } from 'vue'
import { fetchFileConfig } from '@/api/file'
import type { FileConfig } from '@/api/file'

const FALLBACK: FileConfig = { maxSize: 50 * 1024 * 1024, allowedExt: [] }

/** 附件配置加载（失败回退常量，不阻塞上传入口）。 */
export function useFileConfig() {
  const config = ref<FileConfig>(FALLBACK)
  const loading = ref(false)
  const error = ref<Error | null>(null)
  let seq = 0

  async function load(): Promise<void> {
    const s = ++seq
    loading.value = true
    error.value = null
    try {
      const c = await fetchFileConfig()
      if (s !== seq) return
      config.value = c
    } catch (e) {
      if (s !== seq) return
      error.value = e as Error
      config.value = FALLBACK
    } finally {
      if (s === seq) loading.value = false
    }
  }

  onUnmounted(() => {
    seq++
  })

  return { config, loading, error, load }
}
