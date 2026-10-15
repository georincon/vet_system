<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listEnvios } from '@/api/comunicaciones'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'
import { ESTADOS_RECORDATORIO, type EnvioLog, type EstadoRecordatorio } from '@/types/fase3'
import { ESTADO_ENVIO } from './estados'

const items = ref<EnvioLog[]>([])
const loading = ref(false)
const error = ref('')
const f = reactive({ estado: null as EstadoRecordatorio | null, page: 1, pages: 0, total: 0 })
const headers = [
  { title: 'Fecha', key: 'enviadoEn', sortable: false },
  { title: 'Destinatario', key: 'destinatario', sortable: false },
  { title: 'Asunto', key: 'asunto', sortable: false },
  { title: 'Canal / proveedor', key: 'canal', sortable: false },
  { title: 'Estado', key: 'estado', sortable: false },
]
async function cargar() {
  loading.value = true; error.value = ''
  try {
    const r = await listEnvios({ estado: f.estado, page: f.page - 1, size: 15 })
    items.value = r.items; f.pages = r.totalPages; f.total = r.totalElements
  } catch (e) { error.value = errorText(e, 'No se pudo cargar la bitácora') } finally { loading.value = false }
}
defineExpose({ recargar: cargar })
onMounted(cargar)
</script>

<template>
  <div>
    <div class="d-flex ga-2 mb-2 align-center">
      <v-select v-model="f.estado" :items="ESTADOS_RECORDATORIO" label="Estado" clearable density="comfortable" hide-details style="max-width: 260px" @update:model-value="f.page = 1; cargar()" />
      <v-spacer /><span class="text-caption text-medium-emphasis">{{ f.total }} envío(s)</span>
    </div>
    <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-2" :text="error" />
    <v-data-table :headers="headers" :items="items" :loading="loading" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin envíos registrados">
      <template #item.enviadoEn="{ item }">{{ etiquetaInstante(item.enviadoEn) }}</template>
      <template #item.canal="{ item }">{{ item.canal }}<div class="text-caption text-medium-emphasis">{{ item.proveedor ?? '—' }}</div></template>
      <template #item.estado="{ item }">
        <v-chip size="small" variant="tonal" :color="ESTADO_ENVIO[item.estado]?.color ?? 'grey'" :text="ESTADO_ENVIO[item.estado]?.text ?? item.estado" />
        <div v-if="item.error" class="text-caption text-error">{{ item.error }}</div>
      </template>
    </v-data-table>
    <div v-if="f.pages > 1" class="pa-3 d-flex justify-center"><v-pagination v-model="f.page" :length="f.pages" :total-visible="7" @update:model-value="cargar" /></div>
  </div>
</template>
