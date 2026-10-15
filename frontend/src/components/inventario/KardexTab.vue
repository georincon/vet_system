<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listMovimientos } from '@/api/inventario'
import { errorText } from '@/utils/errors'
import { cant, cop } from '@/utils/dinero'
import { etiquetaInstante } from '@/utils/fecha'
import { useAuthStore } from '@/stores/auth'
import type { Movimiento } from '@/types/fase4'
import ItemSelect from './ItemSelect.vue'

const auth = useAuthStore()
const items = ref<Movimiento[]>([])
const loading = ref(false)
const error = ref('')
const f = reactive({ itemId: null as number | null, tipo: null as string | null, desde: '', hasta: '', referencia: '', page: 1, pages: 0, total: 0 })
const TIPOS = ['ENTRADA', 'SALIDA', 'AJUSTE', 'DEVOLUCION', 'VENCIMIENTO', 'TRASLADO']
const headers = [
  { title: 'Fecha', key: 'ocurridoEn', sortable: false },
  { title: 'Tipo', key: 'tipo', sortable: false },
  { title: 'Ítem', key: 'itemNombre', sortable: false },
  { title: 'Lote', key: 'numeroLote', sortable: false },
  { title: 'Cantidad', key: 'delta', sortable: false, align: 'end' as const },
  { title: 'Saldo lote', key: 'saldoLote', sortable: false, align: 'end' as const },
  { title: 'Referencia / motivo', key: 'referencia', sortable: false },
  { title: 'Usuario', key: 'usuario', sortable: false },
]
async function cargar() {
  loading.value = true; error.value = ''
  try {
    const r = await listMovimientos({ itemId: f.itemId, tipo: f.tipo, desde: f.desde, hasta: f.hasta, referencia: f.referencia.trim(), page: f.page - 1, size: 15 })
    items.value = r.items; f.pages = r.totalPages; f.total = r.totalElements
  } catch (e) { error.value = errorText(e, 'No se pudo cargar el kardex') } finally { loading.value = false }
}
function filtrar() { f.page = 1; void cargar() }
const color = (t: string) => (t === 'ENTRADA' || t === 'DEVOLUCION' ? 'success' : t === 'AJUSTE' ? 'info' : 'warning')
defineExpose({ recargar: cargar })
onMounted(cargar)
</script>

<template>
  <div>
    <div class="d-flex flex-wrap ga-2 mb-2 align-center">
      <div class="vet-filtro-ancho flex-grow-1"><ItemSelect v-model="f.itemId" label="Ítem" density="comfortable" hide-details :controla-stock="true" @update:model-value="filtrar" /></div>
      <v-select v-model="f.tipo" :items="TIPOS" label="Tipo" clearable hide-details density="comfortable" class="vet-filtro" @update:model-value="filtrar" />
      <v-text-field v-model="f.desde" type="date" label="Desde" hide-details density="comfortable" class="vet-filtro" @update:model-value="filtrar" />
      <v-text-field v-model="f.hasta" type="date" label="Hasta" hide-details density="comfortable" class="vet-filtro" @update:model-value="filtrar" />
      <v-text-field v-model="f.referencia" label="Referencia (p. ej. FACTURA:POS-)" hide-details density="comfortable" class="vet-filtro" clearable @keyup.enter="filtrar" @click:clear="filtrar" />
    </div>
    <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-2" :text="error" />
    <v-data-table :headers="headers" :items="items" :loading="loading" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin movimientos para estos filtros">
      <template #item.ocurridoEn="{ item }">{{ etiquetaInstante(item.ocurridoEn) }}</template>
      <template #item.tipo="{ item }"><v-chip size="small" variant="tonal" :color="color(item.tipo)" :text="item.tipo" /></template>
      <template #item.itemNombre="{ item }">{{ item.itemNombre }}<div class="text-caption text-medium-emphasis">{{ item.itemCodigo }} · {{ item.bodega }}</div></template>
      <template #item.delta="{ item }"><strong :class="item.delta < 0 ? 'text-error' : 'text-success'">{{ item.delta > 0 ? '+' : '' }}{{ cant(item.delta) }}</strong></template>
      <template #item.saldoLote="{ item }">{{ cant(item.saldoLote) }}</template>
      <template #item.referencia="{ item }">{{ item.referencia ?? '—' }}
        <div class="text-caption text-medium-emphasis">{{ item.motivo }}<template v-if="auth.can.inventarioCostos && item.costoUnitario"> · costo {{ cop(item.costoUnitario) }}</template></div>
      </template>
      <template #item.usuario="{ item }">{{ item.usuario?.nombre ?? '—' }}</template>
    </v-data-table>
    <div class="d-flex align-center justify-space-between mt-2">
      <span class="text-caption text-medium-emphasis">{{ f.total }} movimiento(s) · el kardex es de solo lectura</span>
      <v-pagination v-if="f.pages > 1" v-model="f.page" :length="f.pages" :total-visible="7" @update:model-value="cargar" />
    </div>
  </div>
</template>
