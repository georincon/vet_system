<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { bitacoraRecordatorio, cancelarRecordatorio, listRecordatorios } from '@/api/comunicaciones'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'
import { CANALES, ESTADOS_RECORDATORIO, type CanalMensaje, type EnvioLog, type EstadoRecordatorio, type Recordatorio } from '@/types/fase3'
import { ESTADO_ENVIO } from './estados'

const ui = useUiStore()
const items = ref<Recordatorio[]>([])
const loading = ref(false)
const error = ref('')
const f = reactive({ estado: null as EstadoRecordatorio | null, canal: null as CanalMensaje | null, tipo: null as string | null, desde: '', hasta: '', page: 1, pages: 0, total: 0 })
const TIPOS = [{ title: 'Todos', value: null }, { title: 'Vacuna próxima', value: 'VACUNA_PROXIMA' }, { title: 'Desparasitación', value: 'DESPARASITACION_PROXIMA' }, { title: 'Cita', value: 'CITA' }]

const headers = [
  { title: 'Programado para', key: 'programadoPara', sortable: false },
  { title: 'Tutor / paciente', key: 'tutor', sortable: false },
  { title: 'Regla', key: 'regla', sortable: false },
  { title: 'Canal', key: 'canal', sortable: false },
  { title: 'Estado', key: 'estado', sortable: false },
  { title: 'Intentos', key: 'intentos', sortable: false },
  { title: '', key: 'acciones', sortable: false, align: 'end' as const },
]

async function cargar() {
  loading.value = true
  error.value = ''
  try {
    const r = await listRecordatorios({ estado: f.estado, canal: f.canal, tipo: f.tipo, desde: f.desde, hasta: f.hasta, page: f.page - 1, size: 15 })
    items.value = r.items; f.pages = r.totalPages; f.total = r.totalElements
  } catch (e) { error.value = errorText(e, 'No se pudieron cargar los recordatorios') } finally { loading.value = false }
}
function buscar() { f.page = 1; void cargar() }

const cancelar = ref<Recordatorio | null>(null)
const cancelando = ref(false)
async function confirmarCancelar() {
  if (!cancelar.value) return
  cancelando.value = true
  try {
    await cancelarRecordatorio(cancelar.value.id)
    ui.showSuccess('Recordatorio cancelado')
    cancelar.value = null
    await cargar()
  } catch (e) { ui.showError(errorText(e, 'No se pudo cancelar (solo se cancelan los pendientes)')); cancelar.value = null; await cargar() } finally { cancelando.value = false }
}

const detalle = ref<Recordatorio | null>(null)
const bitacora = ref<EnvioLog[]>([])
const cargandoBit = ref(false)
async function verDetalle(r: Recordatorio) {
  detalle.value = r; bitacora.value = []; cargandoBit.value = true
  try { bitacora.value = await bitacoraRecordatorio(r.id) } catch (e) { ui.showError(errorText(e, 'No se pudo cargar la bitácora')) } finally { cargandoBit.value = false }
}
defineExpose({ recargar: cargar })
onMounted(cargar)
</script>

<template>
  <div>
    <v-row dense class="mb-2">
      <v-col cols="6" md="2"><v-select v-model="f.estado" :items="ESTADOS_RECORDATORIO" label="Estado" clearable density="comfortable" hide-details /></v-col>
      <v-col cols="6" md="2"><v-select v-model="f.canal" :items="CANALES" label="Canal" clearable density="comfortable" hide-details /></v-col>
      <v-col cols="6" md="2"><v-select v-model="f.tipo" :items="TIPOS" label="Tipo" density="comfortable" hide-details /></v-col>
      <v-col cols="6" md="2"><v-text-field v-model="f.desde" type="date" label="Desde" density="comfortable" hide-details clearable /></v-col>
      <v-col cols="6" md="2"><v-text-field v-model="f.hasta" type="date" label="Hasta" density="comfortable" hide-details clearable /></v-col>
      <v-col cols="6" md="2"><v-btn color="primary" variant="flat" block height="48" prepend-icon="mdi-magnify" @click="buscar">Filtrar</v-btn></v-col>
    </v-row>
    <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-2" :text="error" />
    <v-data-table :headers="headers" :items="items" :loading="loading" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin recordatorios">
      <template #item.programadoPara="{ item }">{{ etiquetaInstante(item.programadoPara) }}</template>
      <template #item.tutor="{ item }">
        <div class="font-weight-medium">{{ item.tutor ?? `Tutor #${item.tutorId}` }}</div>
        <div class="text-caption text-medium-emphasis">{{ item.paciente ?? '—' }}</div>
      </template>
      <template #item.regla="{ item }">{{ item.regla ?? item.tipo ?? '—' }}</template>
      <template #item.canal="{ item }"><v-chip size="small" variant="outlined" :text="item.canal" /></template>
      <template #item.estado="{ item }">
        <v-chip size="small" variant="tonal" :color="ESTADO_ENVIO[item.estado]?.color ?? 'grey'" :text="ESTADO_ENVIO[item.estado]?.text ?? item.estado" />
        <div v-if="item.ultimoError" class="text-caption text-error">{{ item.ultimoError }}</div>
      </template>
      <template #item.acciones="{ item }">
        <v-btn size="small" variant="text" icon="mdi-history" title="Ver bitácora de envíos" @click="verDetalle(item)" />
        <v-btn v-if="item.estado === 'PENDIENTE'" size="small" variant="text" color="error" icon="mdi-cancel" title="Cancelar" @click="cancelar = item" />
      </template>
    </v-data-table>
    <div v-if="f.pages > 1" class="pa-3 d-flex justify-center"><v-pagination v-model="f.page" :length="f.pages" :total-visible="7" @update:model-value="cargar" /></div>
    <div class="text-caption text-medium-emphasis px-2">{{ f.total }} recordatorio(s)</div>

    <ConfirmDialog :model-value="!!cancelar" title="Cancelar recordatorio" :message="`¿Cancelar el recordatorio para ${cancelar?.tutor ?? ''}? No se enviará.`" confirm-text="Cancelar recordatorio"
      :loading="cancelando" @update:model-value="!$event && (cancelar = null)" @confirm="confirmarCancelar" />

    <v-dialog :model-value="!!detalle" max-width="720" scrollable @update:model-value="!$event && (detalle = null)">
      <v-card>
        <v-card-title class="pt-4">Bitácora del recordatorio #{{ detalle?.id }}</v-card-title>
        <v-card-text>
          <v-progress-linear v-if="cargandoBit" indeterminate color="primary" />
          <div v-if="!cargandoBit && !bitacora.length" class="text-medium-emphasis py-4 text-center">Aún no hay intentos de envío.</div>
          <v-card v-for="b in bitacora" :key="b.id" border elevation="0" class="mb-2">
            <v-card-text>
              <div class="d-flex flex-wrap ga-2 align-center mb-1">
                <v-chip size="small" variant="tonal" :color="ESTADO_ENVIO[b.estado]?.color ?? 'grey'" :text="ESTADO_ENVIO[b.estado]?.text ?? b.estado" />
                <span class="text-caption">{{ etiquetaInstante(b.enviadoEn) }} · {{ b.canal }} · {{ b.proveedor ?? '—' }}</span>
              </div>
              <div class="text-body-2"><strong>Para:</strong> {{ b.destinatario ?? '—' }}</div>
              <div v-if="b.asunto" class="text-body-2"><strong>Asunto:</strong> {{ b.asunto }}</div>
              <div v-if="b.cuerpo" class="text-body-2 text-medium-emphasis" style="white-space: pre-wrap">{{ b.cuerpo }}</div>
              <div v-if="b.error" class="text-body-2 text-error">{{ b.error }}</div>
            </v-card-text>
          </v-card>
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer /><v-btn variant="text" @click="detalle = null">Cerrar</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
