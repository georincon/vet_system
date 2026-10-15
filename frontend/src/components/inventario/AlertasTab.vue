<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getAlertas } from '@/api/inventario'
import { errorText } from '@/utils/errors'
import { cant } from '@/utils/dinero'
import { etiquetaFecha } from '@/utils/fecha'
import type { Alertas, AlertaLote } from '@/types/fase4'

const data = ref<Alertas | null>(null)
const dias = ref(30)
const loading = ref(false)
const error = ref('')
async function cargar() {
  loading.value = true; error.value = ''
  try { data.value = await getAlertas(dias.value || 30) } catch (e) { error.value = errorText(e, 'No se pudieron cargar las alertas') } finally { loading.value = false }
}
const hStock = [
  { title: 'Ítem', key: 'nombre', sortable: false }, { title: 'Stock vigente', key: 'stock', sortable: false, align: 'end' as const },
  { title: 'Mínimo', key: 'minimo', sortable: false, align: 'end' as const }, { title: 'Faltante', key: 'faltante', sortable: false, align: 'end' as const },
]
const hLote = [
  { title: 'Ítem', key: 'itemNombre', sortable: false }, { title: 'Lote', key: 'numeroLote', sortable: false }, { title: 'Bodega', key: 'bodega', sortable: false },
  { title: 'Vencimiento', key: 'fechaVencimiento', sortable: false }, { title: 'Cantidad', key: 'cantidad', sortable: false, align: 'end' as const },
]
const dv = (l: AlertaLote) => (l.diasParaVencer === null || l.diasParaVencer === undefined ? '' : l.diasParaVencer < 0 ? `vencido hace ${-l.diasParaVencer} d` : `en ${l.diasParaVencer} d`)
defineExpose({ recargar: cargar })
onMounted(cargar)
</script>

<template>
  <div>
    <div class="d-flex ga-2 mb-3 align-center">
      <v-text-field v-model.number="dias" type="number" min="1" max="365" label="Ventana de vencimiento (días)" hide-details density="comfortable" class="vet-filtro" @keyup.enter="cargar" />
      <v-btn variant="tonal" prepend-icon="mdi-refresh" :loading="loading" @click="cargar">Actualizar</v-btn>
    </div>
    <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-2" :text="error" />
    <template v-if="data">
      <v-row class="mb-2">
        <v-col cols="12" sm="4"><div class="vet-info-bar"><div class="text-h5 font-weight-bold">{{ data.resumen.bajoMinimo }}</div>Ítems bajo el mínimo</div></v-col>
        <v-col cols="12" sm="4"><div class="vet-info-bar"><div class="text-h5 font-weight-bold">{{ data.resumen.porVencer }}</div>Lotes por vencer ({{ data.dias }} d)</div></v-col>
        <v-col cols="12" sm="4"><div class="vet-info-bar"><div class="text-h5 font-weight-bold">{{ data.resumen.vencidos }}</div>Lotes vencidos con existencias</div></v-col>
      </v-row>
      <h3 class="vet-section-title mt-4">Bajo el mínimo</h3>
      <v-data-table :headers="hStock" :items="data.bajoMinimo" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Ningún ítem bajo el mínimo">
        <template #item.nombre="{ item }">{{ item.nombre }}<div class="text-caption text-medium-emphasis">{{ item.codigo }}</div></template>
        <template #item.stock="{ item }">{{ cant(item.stock) }}</template>
        <template #item.minimo="{ item }">{{ cant(item.minimo) }}</template>
        <template #item.faltante="{ item }"><v-chip size="small" color="error" variant="tonal" :text="cant(item.faltante)" /></template>
      </v-data-table>
      <h3 class="vet-section-title mt-6">Lotes por vencer</h3>
      <v-data-table :headers="hLote" :items="data.porVencer" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin lotes por vencer">
        <template #item.fechaVencimiento="{ item }">{{ etiquetaFecha(item.fechaVencimiento) }} <v-chip size="x-small" color="warning" variant="tonal" :text="dv(item)" /></template>
        <template #item.cantidad="{ item }">{{ cant(item.cantidad) }}</template>
      </v-data-table>
      <h3 class="vet-section-title mt-6">Lotes vencidos con existencias</h3>
      <v-data-table :headers="hLote" :items="data.vencidos" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin lotes vencidos">
        <template #item.fechaVencimiento="{ item }">{{ etiquetaFecha(item.fechaVencimiento) }} <v-chip size="x-small" color="error" variant="tonal" :text="dv(item)" /></template>
        <template #item.cantidad="{ item }">{{ cant(item.cantidad) }}</template>
      </v-data-table>
    </template>
  </div>
</template>
