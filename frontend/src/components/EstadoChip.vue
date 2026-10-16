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
  // Fase 4
  BORRADOR: { color: 'grey', text: 'Borrador' },
  ENVIADO: { color: 'info', text: 'Enviado' },
  ENVIADA: { color: 'info', text: 'Enviada' },
  ACEPTADO: { color: 'primary', text: 'Aceptado' },
  RECHAZADO: { color: 'error', text: 'Rechazado' },
  FACTURADO: { color: 'success', text: 'Facturado' },
  EMITIDA: { color: 'info', text: 'Emitida' },
  PARCIAL: { color: 'warning', text: 'Parcial' },
  PAGADA: { color: 'success', text: 'Pagada' },
  ANULADA: { color: 'error', text: 'Anulada' },
  RECIBIDA: { color: 'success', text: 'Recibida' },
  CERRADO: { color: 'grey-darken-1', text: 'Cerrado', icon: 'mdi-lock-outline' },
  ABIERTO: { color: 'success', text: 'Abierto', icon: 'mdi-lock-open-variant-outline' },
  PENDIENTE: { color: 'warning', text: 'Pendiente' },
  DISPONIBLE: { color: 'success', text: 'Disponible' },
  CUARENTENA: { color: 'warning', text: 'Cuarentena' },
  RETIRADO: { color: 'grey', text: 'Retirado' },
  // Etapa A: laboratorio, imagen, patología, procedimientos
  SOLICITADA: { color: 'warning', text: 'Solicitada', icon: 'mdi-clipboard-clock-outline' },
  MUESTRA_TOMADA: { color: 'secondary', text: 'Muestra tomada', icon: 'mdi-test-tube' },
  EN_PROCESO: { color: 'info', text: 'En proceso', icon: 'mdi-progress-clock' },
  COMPLETADA: { color: 'success', text: 'Completada', icon: 'mdi-check-circle-outline' },
  SOLICITADO: { color: 'warning', text: 'Solicitado' },
  RESULTADO: { color: 'info', text: 'Con resultado (borrador)' },
  VALIDADO: { color: 'success', text: 'Validado', icon: 'mdi-check-decagram' },
  CANCELADO: { color: 'error', text: 'Cancelado' },
  CORREGIDO: { color: 'grey', text: 'Corregido (reemplazado)', icon: 'mdi-history' },
  REALIZADA: { color: 'success', text: 'Realizada' },
  APLAZADA: { color: 'warning', text: 'Aplazada' },
  FIRMADO: { color: 'success', text: 'Firmado', icon: 'mdi-draw-pen' },
  // Etapa B: fórmula, plan, controles, envíos
  VIGENTE: { color: 'success', text: 'Vigente', icon: 'mdi-check-decagram' },
  CUMPLIDA: { color: 'grey', text: 'Cumplida' },
  ANULADA_DOC: { color: 'error', text: 'Anulada' },
  ACTIVO: { color: 'primary', text: 'Activo' },
  REALIZADO: { color: 'success', text: 'Realizado' },
  COMPLETADO: { color: 'success', text: 'Completado' },
  AGENDADO: { color: 'primary', text: 'Agendado', icon: 'mdi-calendar-check-outline' },
  ENVIADO_SIMULADO: { color: 'warning', text: 'Enviado (simulado)', icon: 'mdi-test-tube' },
  FALLIDO: { color: 'error', text: 'Fallido', icon: 'mdi-alert-circle-outline' },
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
