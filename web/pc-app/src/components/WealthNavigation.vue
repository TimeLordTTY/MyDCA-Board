<template>
  <nav class="wealth-navigation" aria-label="财富中枢业务导航">
    <router-link v-for="item in navItems" :key="item.name" :to="item.path"
      class="wealth-nav-link" :class="{ 'is-current': route.name === item.name }"
      :aria-label="item.label" :aria-current="route.name === item.name ? 'page' : undefined"
      :title="item.label" @click="emit('navigate')">
      <component :is="icons[item.icon as keyof typeof icons]" class="wealth-nav-icon" aria-hidden="true" />
      <span class="wealth-nav-label">{{ item.label }}</span>
    </router-link>
  </nav>
</template>

<script setup lang="ts">
import { useRoute } from 'vue-router'
import * as icons from '@element-plus/icons-vue'
import { navItems } from '../layouts/navigation'
const route = useRoute()
const emit = defineEmits<{ navigate: [] }>()
</script>
