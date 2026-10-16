<script setup lang="ts">
import { computed } from 'vue'
import { BANDERA_META } from '@/utils/lab'
import type { Bandera } from '@/types/etapa'

const props = defineProps<{ bandera?: Bandera | null; critico?: boolean; size?: string }>()
const meta = computed(() => (props.bandera ? BANDERA_META[props.bandera] : null))
const etiqueta = computed(() => meta.value ? `${meta.value.descripcion}${props.critico && !meta.value.grave ? ' (crítico)' : ''}` : 'Sin bandera')
</script>

<template>
  <v-chip v-if="meta" :size="size ?? 'small'" :color="meta.color" :variant="meta.grave ? 'flat' : 'tonal'" :prepend-icon="meta.icon"
    :aria-label="etiqueta" :title="etiqueta" class="font-weight-bold">
    {{ meta.texto }}
    <span class="vet-sr-only"> {{ etiqueta }}</span>
  </v-chip>
  <span v-else class="text-medium-emphasis" aria-label="Sin bandera">—</span>
</template>
