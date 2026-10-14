<script setup lang="ts">
import { reactive, ref } from 'vue'
import { createSigno, deleteSigno, updateSigno } from '@/api/consultas'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'
import type { SignoVital, SignoVitalRequest } from '@/types'

const props = defineProps<{ consultaId: number; signos: SignoVital[]; readonly: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const ui = useUiStore()

type Campo = { key: keyof SignoVitalRequest; label: string; type: 'number' | 'text'; step?: string }
const CAMPOS: Campo[] = [
  { key: 'temperatura', label: 'Temperatura (°C)', type: 'number', step: '0.1' },
  { key: 'frecuenciaCardiaca', label: 'FC (ppm)', type: 'number' },
  { key: 'frecuenciaRespiratoria', label: 'FR (rpm)', type: 'number' },
  { key: 'pesoKg', label: 'Peso (kg)', type: 'number', step: '0.01' },
  { key: 'condicionCorporal', label: 'Condición corporal', type: 'text' },
  { key: 'puntajeCondicionCorporal', label: 'Puntaje CC (1-9)', type: 'number' },
  { key: 'mucosas', label: 'Mucosas', type: 'text' },
  { key: 'tiempoLlenadoCapilar', label: 'TLLC (s)', type: 'number', step: '0.1' },
  { key: 'escalaDolor', label: 'Dolor (0-10)', type: 'number' },
  { key: 'hidratacionPct', label: 'Hidratación (%)', type: 'number', step: '0.1' },
  { key: 'presionSistolica', label: 'Presión sistólica', type: 'number' },
  { key: 'spo2', label: 'SpO2 (%)', type: 'number' },
  { key: 'glucosaMgDl', label: 'Glucosa (mg/dL)', type: 'number' },
  { key: 'notas', label: 'Notas', type: 'text' },
]

const dialog = ref(false)
const editando = ref<SignoVital | null>(null)
const saving = ref(false)
const error = ref('')
const eliminar = ref<SignoVital | null>(null)
const form = reactive<Record<string, string | number | null>>({})

function abrir(s?: SignoVital) {
  editando.value = s ?? null
  error.value = ''
  for (const c of CAMPOS) form[c.key] = (s?.[c.key] as string | number | null | undefined) ?? null
  dialog.value = true
}

async function guardar() {
  const body: Record<string, string | number> = {}
  for (const c of CAMPOS) {
    const v = form[c.key]
    if (v === null || v === undefined || v === '') continue
    body[c.key] = c.type === 'number' ? Number(v) : String(v)
  }
  if (Object.keys(body).length === 0) { error.value = 'Registre al menos un valor'; return }
  saving.value = true
  error.value = ''
  try {
    if (editando.value) await updateSigno(props.consultaId, editando.value.id, body as SignoVitalRequest)
    else await createSigno(props.consultaId, body as SignoVitalRequest)
    ui.showSuccess('Signos vitales guardados')
    dialog.value = false
    emit('changed')
  } catch (e) {
    error.value = errorText(e, 'No se pudieron guardar los signos vitales')
  } finally {
    saving.value = false
  }
}

async function borrar() {
  if (!eliminar.value) return
  try {
    await deleteSigno(props.consultaId, eliminar.value.id)
    eliminar.value = null
    emit('changed')
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo eliminar la toma'))
  }
}

const fmt = (v: unknown) => (v === null || v === undefined || v === '' ? '—' : String(v))
</script>

<template>
  <div>
    <div class="d-flex align-center mb-2">
      <strong class="flex-grow-1">Signos vitales</strong>
      <v-btn v-if="!readonly" size="small" color="primary" variant="tonal" prepend-icon="mdi-plus" @click="abrir()">
        Registrar toma
      </v-btn>
    </div>
    <v-table v-if="signos.length" density="compact" class="border rounded">
      <thead>
        <tr>
          <th>Hora</th><th>Temp</th><th>FC</th><th>FR</th><th>Peso</th><th>CC</th><th>Dolor</th><th>Mucosas</th><th>SpO2</th><th></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="s in signos" :key="s.id">
          <td>{{ etiquetaInstante(s.registradoEn) }}<div class="text-caption text-medium-emphasis">{{ s.registradoPor }}</div></td>
          <td>{{ fmt(s.temperatura) }}</td><td>{{ fmt(s.frecuenciaCardiaca) }}</td><td>{{ fmt(s.frecuenciaRespiratoria) }}</td>
          <td>{{ fmt(s.pesoKg) }}</td><td>{{ fmt(s.puntajeCondicionCorporal ?? s.condicionCorporal) }}</td>
          <td>{{ fmt(s.escalaDolor) }}</td><td>{{ fmt(s.mucosas) }}</td><td>{{ fmt(s.spo2) }}</td>
          <td class="text-no-wrap">
            <template v-if="!readonly">
              <v-btn size="x-small" variant="text" icon="mdi-pencil" title="Editar" @click="abrir(s)" />
              <v-btn size="x-small" variant="text" icon="mdi-delete-outline" color="error" title="Eliminar" @click="eliminar = s" />
            </template>
          </td>
        </tr>
      </tbody>
    </v-table>
    <div v-else class="text-medium-emphasis text-body-2">Sin tomas registradas.</div>

    <v-dialog v-model="dialog" max-width="720" scrollable>
      <v-card>
        <v-card-title class="pt-4">{{ editando ? 'Editar toma' : 'Registrar signos vitales' }}</v-card-title>
        <v-card-text>
          <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
          <v-row dense>
            <v-col v-for="c in CAMPOS" :key="c.key" cols="12" sm="6" md="4">
              <v-text-field v-model="form[c.key]" :label="c.label" :type="c.type" :step="c.step" />
            </v-col>
          </v-row>
        </v-card-text>
        <v-card-actions class="px-4 pb-4">
          <v-spacer />
          <v-btn variant="text" @click="dialog = false">Cancelar</v-btn>
          <v-btn color="primary" variant="flat" :loading="saving" @click="guardar">Guardar</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    <ConfirmDialog :model-value="!!eliminar" title="Eliminar toma" message="¿Eliminar esta toma de signos vitales?"
      confirm-text="Eliminar" @update:model-value="!$event && (eliminar = null)" @confirm="borrar" />
  </div>
</template>
