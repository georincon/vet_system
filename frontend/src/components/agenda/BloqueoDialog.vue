<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { createBloqueo } from '@/api/agenda'
import { useCatalogosStore } from '@/stores/catalogos'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { hoy } from '@/utils/fecha'
import type { TipoBloqueo } from '@/types'

const props = defineProps<{
  modelValue: boolean
  defaults?: { fecha?: string; horaInicio?: string; veterinarioId?: number | null; salaId?: number | null }
}>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; saved: [] }>()

const TIPOS: { title: string; value: TipoBloqueo }[] = [
  { title: 'Almuerzo', value: 'ALMUERZO' },
  { title: 'Vacaciones', value: 'VACACIONES' },
  { title: 'Capacitación', value: 'CAPACITACION' },
  { title: 'Mantenimiento', value: 'MANTENIMIENTO' },
  { title: 'Cirugía', value: 'CIRUGIA' },
  { title: 'Personal', value: 'PERSONAL' },
  { title: 'Otro', value: 'OTRO' },
]

const catalogos = useCatalogosStore()
const ui = useUiStore()
const saving = ref(false)
const error = ref('')
const form = reactive({
  tipo: 'ALMUERZO' as TipoBloqueo,
  veterinarioId: null as number | null,
  salaId: null as number | null,
  fechaInicio: hoy(),
  fechaFin: hoy(),
  horaInicio: '12:00',
  horaFin: '13:00',
  motivo: '',
})

watch(() => props.modelValue, (abierto) => {
  if (!abierto) return
  error.value = ''
  const d = props.defaults ?? {}
  Object.assign(form, {
    tipo: 'ALMUERZO', veterinarioId: d.veterinarioId ?? null, salaId: d.salaId ?? null,
    fechaInicio: d.fecha ?? hoy(), fechaFin: d.fecha ?? hoy(),
    horaInicio: d.horaInicio ?? '12:00', horaFin: '', motivo: '',
  })
  const [h, m] = (d.horaInicio ?? '12:00').split(':').map(Number)
  form.horaFin = `${String(Math.min(h + 1, 23)).padStart(2, '0')}:${String(m).padStart(2, '0')}`
})

const vetOptions = computed(() => catalogos.veterinarios.map((v) => ({ title: v.nombre, value: v.id })))
const salaOptions = computed(() => catalogos.salas.filter((s) => s.activo).map((s) => ({ title: s.nombre, value: s.id })))

async function guardar() {
  error.value = ''
  if (!form.veterinarioId && !form.salaId) {
    error.value = 'Indique un veterinario y/o una sala'
    return
  }
  const inicio = `${form.fechaInicio}T${form.horaInicio}:00`
  const fin = `${form.fechaFin}T${form.horaFin}:00`
  if (fin <= inicio) {
    error.value = 'El fin debe ser posterior al inicio'
    return
  }
  saving.value = true
  try {
    await createBloqueo({
      veterinarioId: form.veterinarioId, salaId: form.salaId, inicio, fin, tipo: form.tipo,
      motivo: form.motivo.trim() || undefined,
    })
    ui.showSuccess('Bloqueo creado')
    emit('saved')
    emit('update:modelValue', false)
  } catch (e) {
    error.value = errorText(e, 'No se pudo crear el bloqueo')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="600" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">Bloquear agenda</v-card-title>
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-row dense>
          <v-col cols="12"><v-select v-model="form.tipo" label="Tipo de bloqueo" :items="TIPOS" /></v-col>
          <v-col cols="12" sm="6"><v-select v-model="form.veterinarioId" label="Veterinario" :items="vetOptions" clearable /></v-col>
          <v-col cols="12" sm="6"><v-select v-model="form.salaId" label="Sala" :items="salaOptions" clearable /></v-col>
          <v-col cols="12" sm="6"><v-text-field v-model="form.fechaInicio" label="Desde (fecha)" type="date" /></v-col>
          <v-col cols="12" sm="6"><v-text-field v-model="form.horaInicio" label="Hora inicio" type="time" /></v-col>
          <v-col cols="12" sm="6"><v-text-field v-model="form.fechaFin" label="Hasta (fecha)" type="date" /></v-col>
          <v-col cols="12" sm="6"><v-text-field v-model="form.horaFin" label="Hora fin" type="time" /></v-col>
          <v-col cols="12"><v-text-field v-model="form.motivo" label="Motivo" /></v-col>
        </v-row>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn color="primary" variant="flat" :loading="saving" @click="guardar">Crear bloqueo</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
