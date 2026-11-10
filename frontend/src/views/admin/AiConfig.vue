<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Connection, Delete, Edit, Plus, Select } from '@element-plus/icons-vue'
import {
  activateAiModel,
  createAiModel,
  deleteAiModel,
  listAiModels,
  testAiModel,
  updateAiModel,
  type AiModelSaveReq,
  type AiModelVO,
} from '@/api/ai'

/**
 * AI 模型配置（ADMIN）。
 *
 * 多条模型可并存，但全局只有一条 active；API Key 只写不读，编辑时留空表示不修改。
 * 周报生成固定使用当前启用模型。
 */
const loading = ref(false)
const saving = ref(false)
const models = ref<AiModelVO[]>([])
const dialogVisible = ref(false)
const editing = ref<AiModelVO | null>(null)
const formRef = ref<FormInstance>()
const testingId = ref<number | null>(null)

const form = reactive({
  name: '',
  provider: 'OPENAI_COMPATIBLE',
  baseUrl: '',
  apiKey: '',
  model: '',
  temperature: 0.3,
  maxTokens: 2048,
  timeoutSeconds: 60,
  enabled: true,
})

const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请输入 Base URL', trigger: 'blur' }],
  model: [{ required: true, message: '请输入模型名', trigger: 'blur' }],
}

const dialogTitle = computed(() => (editing.value ? '编辑模型' : '新增模型'))
const activeModel = computed(() => models.value.find((m) => m.active))

/** Element Plus 表格插槽的 row 是宽松类型，这里收敛为 AiModelVO 便于调用。 */
function asModel(row: unknown): AiModelVO {
  return row as AiModelVO
}

async function load(): Promise<void> {
  loading.value = true
  try {
    models.value = await listAiModels()
  } catch (e) {
    ElMessage.error((e as Error).message || '模型列表加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

function resetForm(): void {
  form.name = ''
  form.provider = 'OPENAI_COMPATIBLE'
  form.baseUrl = ''
  form.apiKey = ''
  form.model = ''
  form.temperature = 0.3
  form.maxTokens = 2048
  form.timeoutSeconds = 60
  form.enabled = true
}

function openCreate(): void {
  editing.value = null
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: AiModelVO): void {
  editing.value = row
  form.name = row.name
  form.provider = row.provider || 'OPENAI_COMPATIBLE'
  form.baseUrl = row.baseUrl
  form.apiKey = ''
  form.model = row.model
  form.temperature = row.temperature ?? 0.3
  form.maxTokens = row.maxTokens ?? 2048
  form.timeoutSeconds = row.timeoutSeconds ?? 60
  form.enabled = row.enabled
  dialogVisible.value = true
}

async function submit(): Promise<void> {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload: AiModelSaveReq = {
      name: form.name.trim(),
      provider: form.provider,
      baseUrl: form.baseUrl.trim(),
      model: form.model.trim(),
      temperature: form.temperature,
      maxTokens: form.maxTokens,
      timeoutSeconds: form.timeoutSeconds,
      enabled: form.enabled,
    }
    if (form.apiKey.trim()) payload.apiKey = form.apiKey.trim()
    if (editing.value) {
      await updateAiModel(editing.value.id, payload)
    } else {
      await createAiModel(payload)
    }
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function onActivate(row: AiModelVO): Promise<void> {
  try {
    await activateAiModel(row.id)
    ElMessage.success(`已切换为「${row.name}」`)
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message || '切换失败')
  }
}

async function onTest(row: AiModelVO): Promise<void> {
  testingId.value = row.id
  try {
    const result = await testAiModel(row.id)
    if (result.ok) {
      ElMessage.success(`连接正常（${result.latencyMs} ms）：${result.message}`)
    } else {
      ElMessage.warning(result.message || '连接失败')
    }
  } catch (e) {
    ElMessage.error((e as Error).message || '测试失败')
  } finally {
    testingId.value = null
  }
}

async function onDelete(row: AiModelVO): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认删除模型「${row.name}」？`, '删除模型', {
      type: 'warning',
      confirmButtonText: '删除',
    })
  } catch {
    return
  }
  try {
    await deleteAiModel(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}
</script>

<template>
  <div v-loading="loading" class="ai">
    <section class="ai__head">
      <div>
        <h2 class="ai__title">AI 模型</h2>
        <p class="ai__desc">
          周报生成使用「当前模型」。可配置多条，但同一时间只能启用一条；API Key 加密存储且不会回显。
        </p>
      </div>
      <el-button type="primary" :icon="Plus" data-test="ai-model-create" @click="openCreate">
        新增模型
      </el-button>
    </section>

    <el-alert
      v-if="!activeModel"
      type="warning"
      show-icon
      :closable="false"
      title="尚未启用任何模型，周报生成不可用。"
      class="ai__alert"
    />
    <el-alert
      v-else
      type="success"
      show-icon
      :closable="false"
      :title="`当前模型：${activeModel.name}（${activeModel.model}）`"
      class="ai__alert"
    />

    <el-table :data="models" class="ai__table" empty-text="还没有配置模型">
      <el-table-column prop="name" label="名称" min-width="140">
        <template #default="{ row }">
          <div class="ai__name">
            <span>{{ row.name }}</span>
            <el-tag v-if="row.active" size="small" type="success" effect="plain">当前</el-tag>
            <el-tag v-else-if="!row.enabled" size="small" type="info" effect="plain">停用</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="model" label="模型" min-width="150" />
      <el-table-column prop="baseUrl" label="Base URL" min-width="220" show-overflow-tooltip />
      <el-table-column label="参数" width="170">
        <template #default="{ row }">
          <span class="ai__params">T={{ row.temperature }} · {{ row.maxTokens }} tokens</span>
        </template>
      </el-table-column>
      <el-table-column label="密钥" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.hasApiKey ? 'success' : 'info'" effect="plain">
            {{ row.hasApiKey ? '已配置' : '未配置' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" text :loading="testingId === row.id" :icon="Connection" @click="onTest(asModel(row))">
            测试
          </el-button>
          <el-button
            v-if="!row.active"
            size="small"
            text
            :icon="Select"
            :disabled="!row.enabled"
            @click="onActivate(asModel(row))"
          >
            设为当前
          </el-button>
          <el-button size="small" text :icon="Edit" @click="openEdit(asModel(row))">编辑</el-button>
          <el-button
            size="small"
            text
            type="danger"
            :icon="Delete"
            :disabled="row.active"
            @click="onDelete(asModel(row))"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" maxlength="80" placeholder="例如：DeepSeek 生产" />
        </el-form-item>
        <el-form-item label="Base URL" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.deepseek.com 或完整 /chat/completions 地址" />
        </el-form-item>
        <el-form-item label="模型名" prop="model">
          <el-input v-model="form.model" placeholder="deepseek-chat" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="editing?.hasApiKey ? '已配置，留空表示不修改' : '填写模型服务密钥'"
          />
        </el-form-item>
        <el-form-item label="Temperature">
          <el-input-number v-model="form.temperature" :min="0" :max="2" :step="0.1" />
        </el-form-item>
        <el-form-item label="最大 tokens">
          <el-input-number v-model="form.maxTokens" :min="256" :max="32768" :step="256" />
        </el-form-item>
        <el-form-item label="超时（秒）">
          <el-input-number v-model="form.timeoutSeconds" :min="5" :max="300" :step="5" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" data-test="ai-model-save" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ai {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.ai__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.ai__title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--bb-text-primary);
}
.ai__desc {
  margin: 4px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--bb-text-muted);
}
.ai__alert {
  margin: 0;
}
.ai__table {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--bb-radius-sm);
  background: var(--bg-primary);
}
.ai__name {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ai__params {
  font-size: 12px;
  color: var(--bb-text-muted);
}
</style>

