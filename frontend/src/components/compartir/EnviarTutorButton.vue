<script setup lang="ts">
import { ref } from 'vue'
import EnviarTutorDialog from '@/components/compartir/EnviarTutorDialog.vue'
import { usePuedeCompartir } from '@/utils/compartir'
import type { DocumentoCompartible, Envio } from '@/types/etapa'

withDefaults(defineProps<{ pacienteId: number; documentos: DocumentoCompartible[]; label?: string; variant?: 'flat' | 'tonal' | 'text' | 'outlined'; size?: string }>(),
  { label: 'Enviar al tutor', variant: 'tonal', size: 'default' })
const emit = defineEmits<{ enviado: [envio: Envio] }>()
const { puede } = usePuedeCompartir()
const abierto = ref(false)
</script>

<template>
  <span v-if="puede">
    <v-btn :variant="variant" :size="size" color="primary" prepend-icon="mdi-send-outline" @click="abierto = true">{{ label }}</v-btn>
    <EnviarTutorDialog v-model="abierto" :paciente-id="pacienteId" :documentos="documentos" @enviado="emit('enviado', $event)" />
  </span>
</template>
