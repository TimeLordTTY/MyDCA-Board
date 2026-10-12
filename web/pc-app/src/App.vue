<template>
  <router-view :key="sessionRevision" />
</template>

<script setup lang="ts">
import { sessionRevision } from './auth/session'
</script>
