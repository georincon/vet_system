<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { createCita, updateCita } from '@/api/citas'
import PacienteSelect from '@/components/agenda/PacienteSelect.vue'
import { useCatalogosStore } from '@/stores/catalogos'
import { useUiStore } from '@/stores/ui'
import { aMinutos, deMinutos, hoy } from '@/utils/fecha'
import { errorText } from '@/utils/errors'
import type { Cita, CitaRequest, PacienteResumen } from '@/types'

export interface CitaDefaults {
  fecha?: string
  horaInicio?: string
  veterinarioId?: number | null
  salaId?: number | null
  pacienteId?: number | null
  paciente?: PacienteResumen | null
  tipoCitaId?: number | null
  motivo?: string
}

const props = defineProps<{ modelValue: boolean; cita?: Cita | null; defaults?: CitaDefaults }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [cita: Cita] }>()

const ui = useUiStore()
const catalogos = useCatalogosStore()
catalogos.ensure().catch((e) => ui.showError(errorText(e, 'No se pudieron cargar los catálogos de agenda')))

interface FormHandle { validate: () => Promise<{ valid: boolean }> }
const formRef = ref<FormHandle | null>(null)
const saving = ref(false)
const error = ref('')
const horaFinManual = ref(false)

const form = reactive({
  pacienteId: null as number | null,
  veterinarioId: null as number | null,
  fecha: hoy(),
  horaInicio: '09:00',
  horaFin: '09:30',
  tipoCitaId: null as number | null,
  salaId: null as number | null,
  motivo: '',
  notas: '',
})
const pacienteInicial = ref<PacienteResumen | null>(null)

const editando = computed(() => !!props.cita)
const salaOriginal = computed(() => props.cita?.sala?.id ?? null)

const vetOptions = computed(() => catalogos.veterinarios.map((v) => ({ title: v.nombre, value: v.id })))
const salaOptions = computed(() =>
  catalogos.salas.filter((s) => s.activo && s.tipo !== 'ESPERA').map((s) => ({ title: `${s.nombre} (${s.tipo.toLowerCase()})`, value: s.id })),
)
const tipoOptions = computed(() =>
  catalogos.tiposCita.map((t) => ({ title: `${t.nombre} · ${t.duracionMinutos} min`, value: t.id })),
)
const duracion = computed(() => catalogos.tiposCita.find((t) => t.id === form.tipoCitaId)?.duracionMinutos ?? 30)

function recalcularFin() {
  if (horaFinManual.value || !form.horaInicio) return
  form.horaFin = deMinutos(aMinutos(form.horaInicio) + duracion.value)
}
watch(() => [form.horaInicio, form.tipoCitaId], recalcularFin)

watch(
  () => props.modelValue,
  (abierto) => {
    if (!abierto) return
    error.value = ''
    horaFinManual.value = false
    const c = props.cita
    if (c) {
      Object.assign(form, {
        pacienteId: c.paciente.id, veterinarioId: c.veterinario.id, fecha: c.fecha,
        horaInicio: c.horaInicio.slice(0, 5), horaFin: c.horaFin ? c.horaFin.slice(0, 5) : '',
        tipoCitaId: c.tipoCita?.id ?? null, salaId: c.sala?.id ?? null,
        motivo: c.motivo ?? '', notas: c.notas ?? '',
      })
      pacienteInicial.value = c.paciente
      horaFinManual.value = true
    } else {
      const d = props.defaults ?? {}
      Object.assign(form, {
        pacienteId: d.pacienteId ?? d.paciente?.id ?? null, veterinarioId: d.veterinarioId ?? null,
        fecha: d.fecha ?? hoy(), horaInicio: d.horaInicio ?? '09:00', horaFin: '',
        tipoCitaId: d.tipoCitaId ?? null, salaId: d.salaId ?? null, motivo: d.motivo ?? '', notas: '',
      })
      pacienteInicial.value = d.paciente ?? null
      recalcularFin()
    }
  },
)

const requerido = (msg: string) => (v: unknown) => (v !== null && v !== undefined && v !== '') || msg

async function guardar() {
  const res = await formRef.value?.validate()
  if (!res?.valid) return
  if (form.horaFin && aMinutos(form.horaFin) <= aMinutos(form.horaInicio)) {
    error.value = 'La hora de fin debe ser posterior a la de inicio'
    return
  }
  saving.value = true
  error.value = ''
  try {
    // En edición, '' limpia el valor (null/ausente no lo modifica).
    const base: CitaRequest = {
      pacienteId: form.pacienteId as number,
      veterinarioId: form.veterinarioId as number,
      fecha: form.fecha,
      horaInicio: form.horaInicio,
      horaFin: form.horaFin || undefined,
      tipoCitaId: form.tipoCitaId,
      salaId: form.salaId,
      motivo: form.motivo.trim(),
      notas: form.notas.trim(),
    }
    let saved: Cita
    if (props.cita) {
      if (salaOriginal.value && form.salaId == null) base.quitarSala = true
      saved = await updateCita(props.cita.id, base)
      ui.showSuccess('Cita actualizada correctamente')
    } else {
      saved = await createCita(base)
      ui.showSuccess(`Cita ${saved.codigo} creada`)
    }
    emit('saved', saved)
    emit('update:modelValue', false)
  } catch (e) {
    // 409: solape de agenda / bloqueo; se muestra dentro del diálogo para poder corregir la hora.
    error.value = errorText(e, 'No se pudo guardar la cita')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="720" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">{{ editando ? `Editar cita ${cita?.codigo}` : 'Nueva cita' }}</v-card-title>
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-4" :text="error" />
        <v-form ref="formRef" @submit.prevent="guardar">
          <v-row dense>
            <v-col cols="12" md="6">
              <PacienteSelect
                v-model="form.pacienteId"
                :inicial="pacienteInicial"
                :rules="[requerido('Seleccione el paciente')]"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-select
                v-model="form.veterinarioId"
                label="Veterinario"
                :items="vetOptions"
                :rules="[requerido('Seleccione el veterinario')]"
                prepend-inner-icon="mdi-doctor"
              />
            </v-col>
            <v-col cols="12" md="6">
              <v-select v-model="form.tipoCitaId" label="Tipo de cita" :items="tipoOptions" clearable />
            </v-col>
            <v-col cols="12" md="6">
              <v-select v-model="form.salaId" label="Sala" :items="salaOptions" clearable prepend-inner-icon="mdi-door" />
            </v-col>
            <v-col cols="12" sm="4">
              <v-text-field v-model="form.fecha" label="Fecha" type="date" :rules="[requerido('Ingrese la fecha')]" />
            </v-col>
            <v-col cols="6" sm="4">
              <v-text-field v-model="form.horaInicio" label="Hora inicio" type="time" step="900" :rules="[requerido('Ingrese la hora')]" />
            </v-col>
            <v-col cols="6" sm="4">
              <v-text-field
                v-model="form.horaFin"
                label="Hora fin"
                type="time"
                step="900"
                :hint="`Duración sugerida: ${duracion} min`"
                persistent-hint
                @update:model-value="horaFinManual = true"
              />
            </v-col>
            <v-col cols="12"><v-text-field v-model="form.motivo" label="Motivo" /></v-col>
            <v-col cols="12"><v-textarea v-model="form.notas" label="Notas" rows="2" auto-grow /></v-col>
          </v-row>
        </v-form>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn color="primary" variant="flat" :loading="saving" @click="guardar">
          {{ editando ? 'Guardar cambios' : 'Crear cita' }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
