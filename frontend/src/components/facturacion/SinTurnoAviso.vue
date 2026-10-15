<script setup lang="ts">
defineProps<{ texto?: string }>()
const emit = defineEmits<{ abrir: [] }>()
</script>

<template>
  <v-alert type="warning" variant="tonal" class="mb-4" icon="mdi-cash-lock" title="No tiene un turno de caja abierto">
    {{ texto ?? 'Para cobrar (POS, pagos y abonos) primero abra su turno de caja con el monto inicial.' }}
    <div class="mt-2"><v-btn size="small" variant="flat" color="primary" prepend-icon="mdi-cash-register" @click="emit('abrir')">Abrir caja</v-btn></div>
  </v-alert>
</template>
