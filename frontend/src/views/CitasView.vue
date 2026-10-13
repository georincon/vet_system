<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { onMounted } from 'vue'
import { createCita, listCitas, listEstadosCita, updateCita } from '@/api/citas'
import { listPacientes } from '@/api/pacientes'
import { listUsuarios } from '@/api/usuarios'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import type { Cita, CitaRequest, EstadoCita, Paciente, Usuario } from '@/types'

interface FormHandle {
  validate: () => Promise<{ valid: boolean }>
  reset: () => void
}

const ui = useUiStore()

const items = ref<Cita[]>([])
const loading = ref(false)
const params = reactive({ fecha: '', page: 0, size: 20, totalElements: 0, totalPages: 0 })

const dialog = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormHandle | null>(null)

const form = reactive<CitaRequest>({
  pacienteId: undefined as unknown as number,
  veterinarioId: undefined as unknown as number,
  fecha: new Date().toISOString().slice(0, 10),
  horaInicio: '09:00',
  horaFin: null,
  motivo: '',
  notas: '',
  estadoCitaId: null,
})

const estados = ref<EstadoCita[]>([])
const pacientes = ref<Paciente[]>([])
const veterinarios = ref<Usuario[]>([])
const pacientesLoading = ref(false)
const veterinariosLoading = ref(false)

const estadoOptions = computed(() => estados.value.map((e) => ({ title: e.nombre, value: e.id })))
const pacienteOptions = computed(() =>
  pacientes.value.map((p) => ({ title: `${p.codigo} - ${p.nombre}`, value: p.id })),
)
const veterinarioOptions = computed(() =>
  veterinarios.value.filter((v) => v.activo).map((v) => ({ title: v.nombre, value: v.id })),
)

function horaLabel(hora: string) {
  return hora?.slice(0, 5) || '—'
}

function estadoColor(cita: Cita): string {
  const codigo = cita.estadoCita?.codigo
  switch (codigo) {
    case 'ATENDIDA': return 'success'
    case 'CANCELADA':
    case 'NO_ASISTIO': return 'error'
    case 'EN_CURSO': return 'info'
    case 'CONFIRMADA': return 'primary'
    default: return 'warning'
  }
}

async function load() {
  loading.value = true
  try {
    const page = await listCitas(params.fecha || undefined, params.page, params.size)
    items.value = page.items
    params.totalElements = page.totalElements
    params.totalPages = page.totalPages
  } catch (error) {
    ui.showError(errorText(error, 'No se pudieron cargar las citas'))
  } finally {
    loading.value = false
  }
}

async function loadEstados() {
  try { estados.value = await listEstadosCita() } catch (error) { ui.showError(errorText(error, 'No se pudieron cargar los estados')) }
}
async function loadPacientes() {
  pacientesLoading.value = true
  try { const page = await listPacientes(undefined, 0, 200); pacientes.value = page.items } catch (error) { ui.showError(errorText(error, 'No se pudieron cargar los pacientes')) } finally { pacientesLoading.value = false }
}
async function loadVeterinarios() {
  veterinariosLoading.value = true
  try { const page = await listUsuarios(undefined, 0, 200); veterinarios.value = page.items } catch (error) { ui.showError(errorText(error, 'No se pudieron cargar los usuarios')) } finally { veterinariosLoading.value = false }
}

function resetForm() {
  form.pacienteId = undefined as unknown as number
  form.veterinarioId = undefined as unknown as number
  form.fecha = new Date().toISOString().slice(0, 10)
  form.horaInicio = '09:00'
  form.horaFin = null
  form.motivo = ''
  form.notas = ''
  form.estadoCitaId = null
}

function openCreate() { editingId.value = null; resetForm(); formRef.value?.reset(); dialog.value = true }
function openEdit(cita: Cita) {
  editingId.value = cita.id
  form.pacienteId = cita.paciente.id
  form.veterinarioId = cita.veterinario.id
  form.fecha = cita.fecha
  form.horaInicio = cita.horaInicio?.slice(0,5) || '09:00'
  form.horaFin = cita.horaFin ? cita.horaFin.slice(0,5) : null
  form.motivo = cita.motivo || ''
  form.notas = cita.notas || ''
  form.estadoCitaId = cita.estadoCita?.id || null
  dialog.value = true
}

async function save() {
  const result = await formRef.value?.validate()
  if (!result?.valid) return
  saving.value = true
  try {
    const payload: CitaRequest = { pacienteId: form.pacienteId, veterinarioId: form.veterinarioId, fecha: form.fecha, horaInicio: form.horaInicio, horaFin: form.horaFin || null, motivo: form.motivo || null, notas: form.notas || null, estadoCitaId: form.estadoCitaId || null }
    if (editingId.value == null) { await createCita(payload); ui.showSuccess('Cita creada correctamente') } else { await updateCita(editingId.value, payload); ui.showSuccess('Cita actualizada correctamente') }
    dialog.value = false; await load()
  } catch (error) { ui.showError(errorText(error, 'No se pudo guardar la cita')) } finally { saving.value = false }
}

onMounted(async () => { await Promise.all([load(), loadEstados(), loadPacientes(), loadVeterinarios()]) })
watch(() => params.fecha, () => { params.page = 0; void load() })
</script>

<template>
  <div>
    <div class="vet-header">
      <div><h1>Citas</h1><p>Agenda de citas</p></div>
      <v-btn variant="flat" prepend-icon="mdi-calendar-plus" @click="openCreate">Nueva cita</v-btn>
    </div>

    <v-card elevation="0" border rounded="xl" class="mb-1">
      <v-card-text class="d-flex align-center ga-3 py-4 px-6">
        <v-text-field v-model="params.fecha" label="Filtrar por fecha" type="date" density="comfortable" hide-details clearable variant="outlined" style="max-width: 240px" @click:clear="params.fecha=''" />
        <v-btn variant="tonal" prepend-icon="mdi-filter-variant" size="large" @click="load">Filtrar</v-btn>
      </v-card-text>
    </v-card>

    <v-card elevation="0" border rounded="xl">
      <v-data-table :headers="[{title:'Código',key:'codigo'},{title:'Fecha',key:'fecha'},{title:'Hora',key:'horaInicio'},{title:'Paciente',key:'paciente'},{title:'Veterinario',key:'veterinario'},{title:'Estado',key:'estadoCita'},{title:'Motivo',key:'motivo'},{title:'Acciones',key:'acciones',sortable:false,align:'end'}]" :items="items" :loading="loading" :items-per-page="-1" hide-default-footer density="comfortable">
        <template #item.codigo="{ item }"><v-chip size="small" variant="tonal" :text="item.codigo" /></template>
        <template #item.horaInicio="{ item }">{{ horaLabel(item.horaInicio) }}{{ item.horaFin ? ' - ' + horaLabel(item.horaFin) : '' }}</template>
        <template #item.paciente="{ item }">{{ item.paciente?.nombre }} ({{ item.paciente?.codigo }})</template>
        <template #item.veterinario="{ item }">{{ item.veterinario?.nombre }}</template>
        <template #item.estadoCita="{ item }"><v-chip size="small" variant="tonal" :color="estadoColor(item)" :text="item.estadoCita?.nombre" /></template>
        <template #item.motivo="{ item }">{{ item.motivo || '—' }}</template>
        <template #item.acciones="{ item }"><v-btn size="small" variant="text" icon="mdi-pencil" @click="openEdit(item)" /></template>
        <template #bottom v-if="params.totalPages > 1"><div class="pa-4 d-flex justify-center"><v-pagination v-model="params.page" :length="params.totalPages" :total-visible="7" @update:model-value="load" /></div></template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="dialog" max-width="760" scrollable>
      <v-card rounded="xl" elevation="4">
        <v-card-title class="text-h6 pt-6 px-6">{{ editingId === null ? 'Nueva cita' : 'Editar cita' }}</v-card-title>
        <v-card-text class="px-6">
          <v-form ref="formRef" @submit.prevent="save">
            <v-row dense class="mb-1"><v-col cols="12" sm="6" class="pr-6"><v-select v-model="form.pacienteId" label="Paciente" :items="pacienteOptions" item-title="title" item-value="value" :rules="[(v:any)=>!!v||'Seleccione paciente']" density="comfortable" variant="outlined" :loading="pacientesLoading" /></v-col><v-col cols="12" sm="6" class="pl-6"><v-select v-model="form.veterinarioId" label="Veterinario" :items="veterinarioOptions" item-title="title" item-value="value" :rules="[(v:any)=>!!v||'Seleccione veterinario']" density="comfortable" variant="outlined" :loading="veterinariosLoading" /></v-col></v-row>
            <v-row dense class="mb-1"><v-col cols="12" sm="4" class="pr-6"><v-text-field v-model="form.fecha" label="Fecha" type="date" :rules="[(v:string)=>!!v||'Ingrese fecha']" density="comfortable" variant="outlined" /></v-col><v-col cols="12" sm="4" class="px-3"><v-text-field v-model="form.horaInicio" label="Hora inicio" type="time" :rules="[(v:string)=>!!v||'Ingrese hora inicio']" density="comfortable" variant="outlined" /></v-col><v-col cols="12" sm="4" class="pl-6"><v-text-field v-model="form.horaFin" label="Hora fin" type="time" density="comfortable" variant="outlined" /></v-col></v-row>
            <v-row dense class="mb-1"><v-col cols="12" class="pr-2"><v-select v-model="form.estadoCitaId" label="Estado" :items="estadoOptions" item-title="title" item-value="value" clearable density="comfortable" variant="outlined" /></v-col></v-row>
            <v-row dense class="mb-1"><v-col cols="12" class="pr-2"><v-text-field v-model="form.motivo" label="Motivo" density="comfortable" variant="outlined" /></v-col></v-row>
            <v-row dense class="mb-1"><v-col cols="12" class="pr-2"><v-textarea v-model="form.notas" label="Notas" rows="3" density="comfortable" variant="outlined" auto-grow /></v-col></v-row>
          </v-form>
        </v-card-text>
        <v-card-actions class="px-6 pb-6"><v-spacer /><v-btn variant="text" rounded="lg" @click="dialog=false">Cancelar</v-btn><v-btn color="primary" :loading="saving" rounded="lg" variant="flat" @click="save">{{ editingId===null?'Crear cita':'Guardar cambios' }}</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
