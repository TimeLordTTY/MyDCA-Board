<template>
  <section class="card wh-card" aria-labelledby="appearance-title">
    <h3 id="appearance-title">外观</h3>
    <p class="sub">选择阅读时的颜色与明暗。</p>
    <div class="wh-form-row">
      <label class="wh-label" for="appearance-mode">明暗模式</label>
      <select id="appearance-mode" v-model="appearance.mode" class="wh-input" @change="save">
        <option value="light">浅色</option><option value="dark">深色</option>
        <option value="system">跟随系统</option><option value="legacy">旧版外观</option>
      </select>
      <label class="wh-label" for="appearance-palette">配色</label>
      <select id="appearance-palette" v-model="appearance.palette" class="wh-input" :disabled="appearance.mode === 'legacy'" @change="save">
        <option value="cream">奶油薄荷</option><option value="blue">辅助蓝</option><option value="peach">暖桃</option>
      </select>
    </div>
    <p class="wh-note" role="status" aria-live="polite">{{ feedback }}</p>
  </section>
</template>
<script setup lang="ts">
import { reactive, ref } from 'vue'
import { getAppearanceController } from '../styles/appearance'
const controller = getAppearanceController()
const appearance = reactive(controller.get())
const feedback = ref('')
function save() {
  controller.set(appearance)
  feedback.value = '外观已应用'
}
</script>
