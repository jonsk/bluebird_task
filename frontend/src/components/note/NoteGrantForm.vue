<script setup lang="ts">
import { computed, ref } from 'vue'
import BbUserPicker from '@/components/bb/BbUserPicker.vue'
import type { UserVO } from '@/api/user'
import type { NoteGrantMode, NoteGrantRole } from '@/types/note'

/**
 * NoteGrantForm — 授权发起（三模式）：
 * - OWNER：我给某人授权（选被授权人），创建即生效；
 * - SUPERIOR：我读下属日志（选下属作为 grantor），待下属确认；
 * - ADMIN：管理员代授（同时指定 grantor 与 grantee），创建即生效。
 * 用户选择统一走 `BbUserPicker`：OWNER/ADMIN 用全量；SUPERIOR 用 `scope=subordinate` 单选。
 */
const mode = defineModel<NoteGrantMode>('mode', { required: true })
const role = defineModel<NoteGrantRole>('role', { default: 'VIEWER' })

const props = withDefaults(
  defineProps<{
    submitting?: boolean
    /** 是否显示 ADMIN 模式（仅管理员） */
    adminMode?: boolean
    /** 下属开关关闭时禁用 SUPERIOR 模式 */
    subordinateEnabled?: boolean
  }>(),
  { submitting: false, adminMode: false, subordinateEnabled: true },
)

const emit = defineEmits<{
  (e: 'submit', payload: { mode: NoteGrantMode; grantorId?: number; granteeId?: number; role: NoteGrantRole }): void
  (e: 'cancel'): void
}>()

const pickerOpen = ref(false)
const pickerTarget = ref<'grantor' | 'grantee'>('grantee')
const grantor = ref<UserVO | null>(null)
const grantee = ref<UserVO | null>(null)

const modeOptions = computed(() => {
  const list: Array<{ value: NoteGrantMode; label: string; hint: string }> = [
    { value: 'OWNER', label: '我授权给他人', hint: '把「我的日志」只读/可编辑授权给对方，创建即生效' },
    { value: 'SUPERIOR', label: '我要看下属日志', hint: '选择下属，待其确认后生效' },
  ]
  if (props.adminMode) list.push({ value: 'ADMIN', label: '管理员代授权', hint: '指定日志主人与查看人，创建即生效' })
  return list
})

const modeHint = computed(() => modeOptions.value.find((m) => m.value === mode.value)?.hint ?? '')

function openPicker(target: 'grantor' | 'grantee'): void {
  pickerTarget.value = target
  pickerOpen.value = true
}

function onPicked(users: UserVO[]): void {
  const u = users[users.length - 1] ?? null
  if (pickerTarget.value === 'grantor') grantor.value = u
  else grantee.value = u
}

const canSubmit = computed(() => {
  if (props.submitting) return false
  if (mode.value === 'OWNER') return grantee.value?.id != null
  if (mode.value === 'SUPERIOR') return props.subordinateEnabled && grantor.value?.id != null
  return grantor.value?.id != null && grantee.value?.id != null
})

function submit(): void {
  if (!canSubmit.value) return
  const payload: { mode: NoteGrantMode; grantorId?: number; granteeId?: number; role: NoteGrantRole } = {
    mode: mode.value,
    role: role.value ?? 'VIEWER',
  }
  if (mode.value === 'OWNER') payload.granteeId = Number(grantee.value!.id)
  if (mode.value === 'SUPERIOR') payload.grantorId = Number(grantor.value!.id)
  if (mode.value === 'ADMIN') {
    payload.grantorId = Number(grantor.value!.id)
    payload.granteeId = Number(grantee.value!.id)
  }
  emit('submit', payload)
}

/** 切换模式即重置已选，避免把 OWNER 的被授权人误带到 SUPERIOR 的 grantor。 */
function onModeChange(): void {
  grantor.value = null
  grantee.value = null
}
</script>

<template>
  <div class="grant-form" data-test="note-grant-form">
    <el-radio-group v-model="mode" class="grant-form__modes" @change="onModeChange">
      <el-radio-button v-for="m in modeOptions" :key="m.value" :value="m.value" :disabled="m.value === 'SUPERIOR' && !subordinateEnabled">
        {{ m.label }}
      </el-radio-button>
    </el-radio-group>
    <p class="grant-form__hint">{{ modeHint }}</p>

    <div class="grant-form__row">
      <template v-if="mode === 'SUPERIOR'">
        <span class="grant-form__label">选择下属</span>
        <el-input :model-value="grantor?.name ?? ''" readonly placeholder="请选择下属" class="grant-form__user" />
        <el-button @click="openPicker('grantor')">选择</el-button>
      </template>

      <template v-else-if="mode === 'OWNER'">
        <span class="grant-form__label">被授权人</span>
        <el-input :model-value="grantee?.name ?? ''" readonly placeholder="请选择人员" class="grant-form__user" />
        <el-button @click="openPicker('grantee')">选择</el-button>
      </template>

      <template v-else>
        <span class="grant-form__label">日志主人</span>
        <el-input :model-value="grantor?.name ?? ''" readonly placeholder="选择日志主人" class="grant-form__user" />
        <el-button @click="openPicker('grantor')">选择</el-button>
        <span class="grant-form__label">查看人</span>
        <el-input :model-value="grantee?.name ?? ''" readonly placeholder="选择查看人" class="grant-form__user" />
        <el-button @click="openPicker('grantee')">选择</el-button>
      </template>

      <span class="grant-form__label">权限</span>
      <el-select v-model="role" class="grant-form__role">
        <el-option label="只读" value="VIEWER" />
        <el-option label="可编辑" value="EDITOR" />
      </el-select>
    </div>

    <p v-if="mode === 'SUPERIOR' && !subordinateEnabled" class="grant-form__warn">
      未开启「上级可读下属日志」，暂不能发起下属授权
    </p>

    <div class="grant-form__ops">
      <el-button type="primary" :disabled="!canSubmit" :loading="submitting" @click="submit">发起授权</el-button>
      <el-button @click="emit('cancel')">取消</el-button>
    </div>

    <BbUserPicker
      v-model="pickerOpen"
      :multiple="false"
      :scope="pickerTarget === 'grantor' && mode === 'SUPERIOR' ? 'subordinate' : 'all'"
      :picked-users="[]"
      @confirm="onPicked"
    />
  </div>
</template>

<style scoped>
.grant-form__modes {
  margin-bottom: 6px;
}
.grant-form__hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--bb-text-muted);
}
.grant-form__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.grant-form__label {
  font-size: 13px;
  color: var(--bb-text-secondary);
}
.grant-form__user {
  width: 180px;
}
.grant-form__role {
  width: 110px;
}
.grant-form__warn {
  margin: 10px 0 0;
  font-size: 12px;
  color: var(--el-color-warning);
}
.grant-form__ops {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}
</style>
