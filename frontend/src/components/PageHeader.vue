<script setup lang="ts">
defineProps<{ title: string; subtitle?: string }>()
</script>

<template>
  <div class="vet-header">
    <div>
      <h1>{{ title }}</h1>
      <p v-if="subtitle || $slots.subtitle"><slot name="subtitle">{{ subtitle }}</slot></p>
    </div>
    <div class="d-flex align-center flex-wrap ga-2">
      <slot />
    </div>
  </div>
</template>
