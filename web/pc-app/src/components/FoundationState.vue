<template>
  <div class="wh-state" :class="`wh-state--${kind}`" :role="kind === 'error' ? 'alert' : 'status'"
       :aria-live="kind === 'error' ? 'assertive' : 'polite'" :aria-busy="kind === 'loading'">
    <span v-if="kind === 'loading'" class="wh-loading-mark" aria-hidden="true">…</span>
    <p>{{ message || defaults[kind] }}</p>
    <p v-if="detail" class="wh-note">{{ detail }}</p>
    <button v-if="kind === 'error' && retryable" type="button" class="wh-button" :disabled="disabled" @click="$emit('retry')">重试</button>
  </div>
</template>
<script setup lang="ts">
withDefaults(defineProps<{
  kind: 'loading' | 'error' | 'empty'; message?: string; detail?: string; retryable?: boolean; disabled?: boolean
}>(), { message: '', detail: '', retryable: false, disabled: false })
defineEmits<{ retry: [] }>()
const defaults = { loading: '正在读取…', error: '读取失败，请重试', empty: '暂无记录' }
</script>
