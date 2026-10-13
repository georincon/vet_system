<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import {
  asignarListaEspera, createListaEspera, deleteListaEspera, listListaEspera, updateListaEspera,
} from '@/api/agenda'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import CitaDialog, { type CitaDefaults } from '@/components/agenda/CitaDialog.vue'
import PacienteSelect from '@/components/agenda/PacienteSelect.vue'
import { useAuthStore } from '@/stores/auth'
import { useCatalogosStore } from '@/stores/catalogos'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaFecha } from '@/utils/fecha'
import type { Cita, EstadoListaEspera, FranjaPreferida, ListaEspera, ListaEsperaRequest } from '@/types'

const emit = defineEmits<{ changed: [] }>()
const auth = useAuthStore()
const ui = useUiStore()
const catalogos = useCatalogosStore()
catalogos.ensure().catch(() => undefined)

const items = ref<ListaEspera[]>([])
const loading = ref(false)
const estado = ref<EstadoListaEspera | null>('ACTIVA')

const dialog = ref(false)
const editando = ref<ListaEspera | null>(null)
const saving = ref(false)
const error = ref('')
const form = reactive({
  pacienteId: null as number | null,
  tipoCitaId: null as number | null,
  veterinarioId: null as number | null,
  fechaDeseadaDesde: '',
  fechaDeseadaHasta: '',
  franjaPreferida: 'CUALQUIERA' as FranjaPreferida,
  prioridad: 3,
  notas: '',
})

const confirmarEliminar = ref<ListaEspera | null>(null)
const citaDialog = ref(false)
const asignando = ref<ListaEspera | null>(null)
const citaDefaults = ref<CitaDefaults>({})

const puedeEditar = computed(() => auth.can.agendaAdmin)
const ESTADOS = [
  { title: 'Activas', value: 'ACTIVA' }, { title: 'Asignadas', value: 'ASIGNADA' },
  { title: 'Canceladas', value: 'CANCELADA' }, { title: 'Vencidas', value: 'VENCIDA' },
]
const FRANJAS = [
  { title: 'Mañana', value: 'MANANA' }, { title: 'Tarde', value: 'TARDE' }, { title: 'Cualquiera', value: 'CUALQUIERA' },
]
const FRANJA_LABEL: Record<string, string> = { MANANA: 'Mañana', TARDE: 'Tarde', CUALQUIERA: 'Cualquiera' }

async function load() {
  loading.value = true
  try {
    items.value = await listListaEspera(estado.value ?? undefined)
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo cargar la lista de espera'))
  } finally {
    loading.value = false
  }
}

function abrir(item?: ListaEspera) {
  editando.value = item ?? null
  error.value = ''
  Object.assign(form, {
    pacienteId: item?.paciente.id ?? null, tipoCitaId: item?.tipoCita?.id ?? null,
    veterinarioId: item?.veterinario?.id ?? null, fechaDeseadaDesde: item?.fechaDeseadaDesde ?? '',
    fechaDeseadaHasta: item?.fechaDeseadaHasta ?? '', franjaPreferida: item?.franjaPreferida ?? 'CUALQUIERA',
    prioridad: item?.prioridad ?? 3, notas: item?.notas ?? '',
  })
  dialog.value = true
}

async function guardar() {
  if (!form.pacienteId) { error.value = 'Seleccione el paciente'; return }
  saving.value = true
  error.value = ''
  try {
    const body: ListaEsperaRequest = {
      pacienteId: form.pacienteId, tipoCitaId: form.tipoCitaId, veterinarioId: form.veterinarioId,
      fechaDeseadaDesde: form.fechaDeseadaDesde || null, fechaDeseadaHasta: form.fechaDeseadaHasta || null,
      franjaPreferida: form.franjaPreferida, prioridad: form.prioridad, notas: form.notas.trim(),
    }
    if (editando.value) await updateListaEspera(editando.value.id, body)
    else await createListaEspera(body)
    ui.showSuccess('Lista de espera actualizada')
    dialog.value = false
    await load()
  } catch (e) {
    error.value = errorText(e, 'No se pudo guardar')
  } finally {
    saving.value = false
  }
}

async function eliminar() {
  const it = confirmarEliminar.value
  if (!it) return
  try {
    await deleteListaEspera(it.id)
    ui.showSuccess('Entrada cancelada')
    confirmarEliminar.value = null
    await load()
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo cancelar la entrada'))
  }
}

function asignar(it: ListaEspera) {
  asignando.value = it
  citaDefaults.value = {
    fecha: it.fechaDeseadaDesde ?? undefined,
    horaInicio: it.franjaPreferida === 'TARDE' ? '14:00' : '09:00',
    veterinarioId: it.veterinario?.id ?? null,
    paciente: it.paciente,
    tipoCitaId: it.tipoCita?.id ?? null,
  }
  citaDialog.value = true
}

async function onCitaCreada(cita: Cita) {
  const it = asignando.value
  if (!it) return
  try {
    await asignarListaEspera(it.id, cita.id)
    ui.showSuccess(`Paciente asignado a la cita ${cita.codigo}`)
  } catch (e) {
    ui.showError(errorText(e, 'La cita se creó pero no se pudo marcar la entrada como asignada'))
  }
  asignando.value = null
  await load()
  emit('changed')
}

watch(estado, load)
onMounted(load)
defineExpose({ load })
</script>

<template>
  <div>
    <div class="d-flex align-center flex-wrap ga-3 mb-3">
      <v-select v-model="estado" :items="ESTADOS" label="Estado" clearable style="max-width: 220px" />
      <v-spacer />
      <v-btn v-if="puedeEditar" color="primary" variant="flat" prepend-icon="mdi-plus" @click="abrir()">
        Agregar a lista de espera
      </v-btn>
    </div>

    <v-card border elevation="0">
      <v-data-table
        :headers="[
          { title: 'Prioridad', key: 'prioridad' }, { title: 'Paciente', key: 'paciente' },
          { title: 'Tutor', key: 'tutor' }, { title: 'Tipo / veterinario', key: 'tipo' },
          { title: 'Fechas deseadas', key: 'fechas' }, { title: 'Estado', key: 'estado' },
          { title: 'Acciones', key: 'acciones', sortable: false, align: 'end' },
        ]"
        :items="items"
        :loading="loading"
        :items-per-page="-1"
        hide-default-footer
        no-data-text="Sin entradas en la lista de espera"
      >
        <template #item.prioridad="{ item }">
          <v-chip size="small" variant="tonal" :color="item.prioridad <= 2 ? 'error' : item.prioridad === 3 ? 'warning' : 'grey'"
            :text="String(item.prioridad)" />
        </template>
        <template #item.paciente="{ item }">{{ item.paciente.nombre }} <span class="text-medium-emphasis">({{ item.paciente.especie }})</span></template>
        <template #item.tutor="{ item }">{{ item.tutor?.nombre ?? '—' }}<div class="text-caption text-medium-emphasis">{{ item.tutor?.telefono }}</div></template>
        <template #item.tipo="{ item }">
          {{ item.tipoCita?.nombre ?? 'Cualquier tipo' }}
          <div class="text-caption text-medium-emphasis">{{ item.veterinario?.nombre ?? 'Cualquier veterinario' }}</div>
        </template>
        <template #item.fechas="{ item }">
          {{ etiquetaFecha(item.fechaDeseadaDesde) }} – {{ etiquetaFecha(item.fechaDeseadaHasta) }}
          <div class="text-caption text-medium-emphasis">{{ FRANJA_LABEL[item.franjaPreferida ?? 'CUALQUIERA'] }}</div>
        </template>
        <template #item.estado="{ item }"><EstadoChip :estado="item.estado" /></template>
        <template #item.acciones="{ item }">
          <template v-if="puedeEditar && item.estado === 'ACTIVA'">
            <v-btn size="small" variant="tonal" color="primary" prepend-icon="mdi-calendar-plus" @click="asignar(item)">Asignar</v-btn>
            <v-btn size="small" variant="text" icon="mdi-pencil" title="Editar" @click="abrir(item)" />
            <v-btn size="small" variant="text" icon="mdi-delete-outline" color="error" title="Cancelar entrada"
              @click="confirmarEliminar = item" />
          </template>
        </template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="dialog" max-width="640" scrollable>
      <v-card>
        <v-card-title class="pt-4">{{ editando ? 'Editar entrada' : 'Agregar a lista de espera' }}</v-card-title>
        <v-card-text>
          <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
          <v-row dense>
            <v-col cols="12"><PacienteSelect v-model="form.pacienteId" :inicial="editando?.paciente" :disabled="!!editando" /></v-col>
            <v-col cols="12" sm="6"><v-select v-model="form.tipoCitaId" label="Tipo de cita" clearable
              :items="catalogos.tiposCita.map((t) => ({ title: t.nombre, value: t.id }))" /></v-col>
            <v-col cols="12" sm="6"><v-select v-model="form.veterinarioId" label="Veterinario" clearable
              :items="catalogos.veterinarios.map((v) => ({ title: v.nombre, value: v.id }))" /></v-col>
            <v-col cols="6"><v-text-field v-model="form.fechaDeseadaDesde" label="Desde" type="date" /></v-col>
            <v-col cols="6"><v-text-field v-model="form.fechaDeseadaHasta" label="Hasta" type="date" /></v-col>
            <v-col cols="12" sm="6"><v-select v-model="form.franjaPreferida" label="Franja preferida" :items="FRANJAS" /></v-col>
            <v-col cols="12" sm="6"><v-select v-model="form.prioridad" label="Prioridad (1 = máxima)" :items="[1, 2, 3, 4, 5]" /></v-col>
            <v-col cols="12"><v-textarea v-model="form.notas" label="Notas" rows="2" auto-grow /></v-col>
          </v-row>
        </v-card-text>
        <v-card-actions class="px-4 pb-4">
          <v-spacer />
          <v-btn variant="text" @click="dialog = false">Cancelar</v-btn>
          <v-btn color="primary" variant="flat" :loading="saving" @click="guardar">Guardar</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <ConfirmDialog
      :model-value="!!confirmarEliminar"
      title="Cancelar entrada"
      message="La entrada se marcará como cancelada. ¿Desea continuar?"
      confirm-text="Cancelar entrada"
      @update:model-value="!$event && (confirmarEliminar = null)"
      @confirm="eliminar"
    />
    <CitaDialog v-model="citaDialog" :defaults="citaDefaults" @saved="onCitaCreada" />
  </div>
</template>
