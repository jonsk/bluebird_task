<script setup lang="ts">
import { computed, ref } from 'vue'
import type { Editor } from '@tiptap/vue-3'

const props = defineProps<{
  editor: Editor
  tick?: number
}>()

const emit = defineEmits<{ (e: 'close'): void }>()

const search = ref('')
const replaceWith = ref('')

const storage = computed(() => {
  void props.tick
  return props.editor.storage.findAndReplace
})

const countText = computed(() => {
  // storage 是 Tiptap 的可变对象；显式依赖 tick，事务后重新取 results。
  void props.tick
  void storage.value
  const total = props.editor.storage.findAndReplace?.results?.length ?? 0
  if (!total) return search.value ? '无结果' : ''
  const current = props.editor.storage.findAndReplace.currentIndex
  const index = current == null ? 0 : current + 1
  return `${index} / ${total}`
})

function setSearch(): void {
  props.editor.chain().focus().setSearchTerm(search.value).run()
}

function setReplace(): void {
  props.editor.chain().focus().setReplaceTerm(replaceWith.value).run()
}

function next(): void {
  props.editor.chain().focus().goToNextResult().run()
}

function prev(): void {
  props.editor.chain().focus().goToPreviousResult().run()
}

function replaceOne(): void {
  props.editor.chain().focus().replace().run()
}

function replaceAll(): void {
  props.editor.chain().focus().replaceAll().run()
}

function close(): void {
  props.editor.chain().focus().clearSearch().run()
  emit('close')
}
</script>

<template>
  <div class="note-find" data-test="note-find-bar">
    <input
      v-model="search"
      class="note-find__input"
      placeholder="查找"
      data-test="note-find-input"
      @input="setSearch"
      @keydown.enter.prevent="next"
    />
    <span class="note-find__count" data-test="note-find-count">{{ countText }}</span>
    <button type="button" class="note-find__btn" title="上一个" @click="prev">↑</button>
    <button type="button" class="note-find__btn" title="下一个" @click="next">↓</button>
    <input
      v-model="replaceWith"
      class="note-find__input note-find__input--replace"
      placeholder="替换为"
      data-test="note-find-replace"
      @input="setReplace"
    />
    <button type="button" class="note-find__btn" data-test="note-find-replace-one" @click="replaceOne">替换</button>
    <button type="button" class="note-find__btn" data-test="note-find-replace-all" @click="replaceAll">全部替换</button>
    <label class="note-find__check">
      <input
        type="checkbox"
        :checked="storage?.caseSensitive"
        @change="editor.chain().focus().setCaseSensitive(($event.target as HTMLInputElement).checked).run()"
      />
      区分大小写
    </label>
    <label class="note-find__check">
      <input
        type="checkbox"
        :checked="storage?.wholeWord"
        @change="editor.chain().focus().setWholeWord(($event.target as HTMLInputElement).checked).run()"
      />
      全词
    </label>
    <button type="button" class="note-find__close" title="关闭查找" @click="close">×</button>
  </div>
</template>
