<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listControlados } from '@/api/inventario'
import { errorText } from '@/utils/errors'
import { cant } from '@/utils/dinero'
import { etiquetaInstante } from '@/utils/fecha'
import type { Controlado } from '@/types/fase4'
import ItemSelect from './ItemSelect.vue'

const items = ref<Controlado[]>([])
const loading = ref(false)
const error = ref('')
const f = reactive({ itemId: null as number | null, tipo: null as string | null, desde: '', hasta: '', page: 1, pages: 0, total: 0 })
const headers = [
  { title: 'Fecha', key: 'ocurridoEn', sortable: false }, { title: 'Ítem', key: 'itemNombre', sortable: false },
  { title: 'Lote', key: 'numeroLote', sortable: false }, { title: 'Tipo', key: 'tipo', sortable: false },
  { title: 'Cantidad', key: 'cantidad', sortable: false, align: 'end' as const }, { title: 'Saldo', key: 'saldo', sortable: false, align: 'end' as const },
  { title: 'Paciente', key: 'paciente', sortable: false }, { title: 'Veterinario', key: 'veterinario', sortable: false },
  { title: 'Observaciones', key: 'observaciones', sortable: false },
]
async function cargar() {
  loading.value = true; error.value = ''
  try {
    const r = await listControlados({ itemId: f.itemId, tipo: f.tipo, desde: f.desde, hasta: f.hasta, page: f.page - 1, size: 15 })
    items.value = r.items; f.pages = r.totalPages; f.total = r.totalElements
  } catch (e) { error.value = errorText(e, 'No se pudo cargar el libro de controlados') } finally { loading.value = false }
}
function filtrar() { f.page = 1; void cargar() }
onMounted(cargar)
</script>

<template>
  <div>
    <v-alert type="info" variant="tonal" density="compact" class="mb-3" text="Libro de medicamentos de control especial: se escribe automáticamente con cada movimiento y no puede modificarse." />
    <div class="d-flex flex-wrap ga-2 mb-2 align-center">
      <div class="vet-filtro-ancho flex-grow-1"><ItemSelect v-model="f.itemId" label="Medicamento" density="comfortable" hide-details :controla-stock="true" @update:model-value="filtrar" /></div>
      <v-select v-model="f.tipo" :items="['ENTRADA', 'SALIDA', 'DESECHO', 'AJUSTE']" label="Tipo" clearable hide-details density="comfortable" class="vet-filtro" @update:model-value="filtrar" />
      <v-text-field v-model="f.desde" type="date" label="Desde" hide-details density="comfortable" class="vet-filtro" @update:model-value="filtrar" />
      <v-text-field v-model="f.hasta" type="date" label="Hasta" hide-details density="comfortable" class="vet-filtro" @update:model-value="filtrar" />
    </div>
    <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-2" :text="error" />
    <v-data-table :headers="headers" :items="items" :loading="loading" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin movimientos de controlados">
      <template #item.ocurridoEn="{ item }">{{ etiquetaInstante(item.ocurridoEn) }}</template>
      <template #item.itemNombre="{ item }">{{ item.itemNombre }}<div class="text-caption text-medium-emphasis">{{ item.itemCodigo }}</div></template>
      <template #item.tipo="{ item }"><v-chip size="small" variant="tonal" :color="item.tipo === 'ENTRADA' ? 'success' : 'warning'" :text="item.tipo" /></template>
      <template #item.cantidad="{ item }">{{ cant(item.cantidad) }}</template>
      <template #item.saldo="{ item }"><strong>{{ cant(item.saldo) }}</strong></template>
      <template #item.paciente="{ item }">{{ item.paciente ?? '—' }}</template>
      <template #item.veterinario="{ item }">{{ item.veterinario?.nombre ?? '—' }}</template>
    </v-data-table>
    <div class="d-flex align-center justify-space-between mt-2">
      <span class="text-caption text-medium-emphasis">{{ f.total }} registro(s)</span>
      <v-pagination v-if="f.pages > 1" v-model="f.page" :length="f.pages" :total-visible="7" @update:model-value="cargar" />
    </div>
  </div>
</template>
