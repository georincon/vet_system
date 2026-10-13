<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ estado?: string | null; label?: string; size?: string }>()

const MAP: Record<string, { color: string; text: string; icon?: string }> = {
  PROGRAMADA: { color: 'warning', text: 'Programada' },
  CONFIRMADA: { color: 'primary', text: 'Confirmada' },
  EN_SALA: { color: 'secondary', text: 'En sala de espera', icon: 'mdi-seat' },
  EN_CURSO: { color: 'info', text: 'En consulta', icon: 'mdi-stethoscope' },
  ATENDIDA: { color: 'success', text: 'Atendida' },
  CANCELADA: { color: 'error', text: 'Cancelada' },
  NO_ASISTIO: { color: 'error', text: 'No asistió' },
  REAGENDADA: { color: 'grey', text: 'Reagendada' },
  ABIERTA: { color: 'info', text: 'Abierta', icon: 'mdi-lock-open-variant-outline' },
  CERRADA: { color: 'grey-darken-1', text: 'Cerrada', icon: 'mdi-lock-outline' },
  ACTIVA: { color: 'primary', text: 'Activa' },
  ASIGNADA: { color: 'success', text: 'Asignada' },
  VENCIDA: { color: 'grey', text: 'Vencida' },
}

const info = computed(() => MAP[props.estado ?? ''] ?? { color: 'grey', text: props.estado ?? '—' })
</script>

<template>
  <v-chip
    :size="size ?? 'small'"
    variant="tonal"
    :color="info.color"
    :prepend-icon="info.icon"
    :text="label ?? info.text"
  />
</template>
