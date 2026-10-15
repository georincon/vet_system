<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { cancelarOrden, enviarOrden, listOrdenes } from '@/api/inventario'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { cant, cop } from '@/utils/dinero'
import { etiquetaFecha } from '@/utils/fecha'
import type { Orden } from '@/types/fase4'
import OrdenDialog from './OrdenDialog.vue'
import RecepcionDialog from './RecepcionDialog.vue'

const ui = useUiStore()
const items = ref<Orden[]>([])
const loading = ref(false)
const error = ref('')
const f = reactive({ estado: null as string | null, page: 1, pages: 0, total: 0 })
const expandidas = ref<string[]>([])
const orden = reactive({ dialog: false, editando: null as Orden | null })
const recep = reactive({ dialog: false, orden: null as Orden | null })
const accion = reactive({ orden: null as Orden | null, tipo: '' as 'enviar' | 'cancelar' | '', trabajando: false })
const filas = computed(() => items.value.map((o) => ({ ...o, idTexto: String(o.id) })))
const ESTADOS = ['BORRADOR', 'ENVIADA', 'PARCIAL', 'RECIBIDA', 'CANCELADA']
const headers = [
  { title: 'Orden', key: 'codigo', sortable: false }, { title: 'Proveedor', key: 'proveedor', sortable: false },
  { title: 'Fecha', key: 'fecha', sortable: false }, { title: 'Total', key: 'total', sortable: false, align: 'end' as const },
  { title: 'Estado', key: 'estado', sortable: false }, { title: '', key: 'acciones', sortable: false, align: 'end' as const },
]
const detalleHeaders = [
  { title: 'Ítem', key: 'nombre', sortable: false }, { title: 'Pedido', key: 'cantidad', sortable: false, align: 'end' as const },
  { title: 'Recibido', key: 'cantidadRecibida', sortable: false, align: 'end' as const }, { title: 'Pendiente', key: 'pendiente', sortable: false, align: 'end' as const },
  { title: 'Costo unit.', key: 'costoUnitario', sortable: false, align: 'end' as const },
]
async function cargar() {
  loading.value = true; error.value = ''
  try {
    const r = await listOrdenes({ estado: f.estado, page: f.page - 1, size: 15 })
    items.value = r.items; f.pages = r.totalPages; f.total = r.totalElements
  } catch (e) { error.value = errorText(e, 'No se pudieron cargar las órdenes') } finally { loading.value = false }
}
function filtrar() { f.page = 1; void cargar() }
function editar(o: Orden | null) { orden.editando = o; orden.dialog = true }
function recibir(o: Orden) { recep.orden = o; recep.dialog = true }
async function ejecutar() {
  if (!accion.orden || !accion.tipo) return
  accion.trabajando = true
  try {
    if (accion.tipo === 'enviar') await enviarOrden(accion.orden.id); else await cancelarOrden(accion.orden.id)
    ui.showSuccess(accion.tipo === 'enviar' ? 'Orden enviada al proveedor' : 'Orden cancelada')
    await cargar()
  } catch (e) { ui.showError(errorText(e, 'No se pudo completar la acción')) }
  accion.trabajando = false; accion.orden = null; accion.tipo = ''
}
defineExpose({ nueva: () => editar(null) })
onMounted(cargar)
</script>

<template>
  <div>
    <div class="d-flex flex-wrap ga-2 mb-2 align-center">
      <v-select v-model="f.estado" :items="ESTADOS" label="Estado" clearable hide-details density="comfortable" class="vet-filtro" @update:model-value="filtrar" />
      <v-spacer />
      <v-btn color="primary" variant="flat" prepend-icon="mdi-plus" @click="editar(null)">Nueva orden</v-btn>
    </div>
    <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-2" :text="error" />
    <v-data-table :headers="headers" :items="filas" :loading="loading" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin órdenes de compra"
      item-value="idTexto" v-model:expanded="expandidas" show-expand>
      <template #item.fecha="{ item }">{{ etiquetaFecha(item.fecha) }}</template>
      <template #item.total="{ item }"><span class="vet-monto">{{ cop(item.total) }}</span></template>
      <template #item.estado="{ item }"><EstadoChip :estado="item.estado" /></template>
      <template #item.acciones="{ item }">
        <div class="d-flex justify-end ga-1 flex-wrap">
          <v-btn v-if="item.estado === 'BORRADOR'" size="small" variant="text" prepend-icon="mdi-pencil-outline" @click="editar(item)">Editar</v-btn>
          <v-btn v-if="item.estado === 'BORRADOR'" size="small" variant="tonal" color="primary" prepend-icon="mdi-send-outline" @click="accion.orden = item; accion.tipo = 'enviar'">Enviar</v-btn>
          <v-btn v-if="item.estado === 'ENVIADA' || item.estado === 'PARCIAL'" size="small" variant="flat" color="primary" prepend-icon="mdi-package-down" @click="recibir(item)">Recibir</v-btn>
          <v-btn v-if="item.estado === 'BORRADOR' || item.estado === 'ENVIADA'" size="small" variant="text" color="error" @click="accion.orden = item; accion.tipo = 'cancelar'">Cancelar</v-btn>
        </div>
      </template>
      <template #expanded-row="{ item }">
        <tr><td :colspan="headers.length + 1" class="pa-3">
          <v-data-table :headers="detalleHeaders" :items="item.items" :items-per-page="-1" hide-default-footer density="compact">
            <template #item.nombre="{ item: i }">{{ i.nombre }}<div class="text-caption text-medium-emphasis">{{ i.codigo }}</div></template>
            <template #item.cantidad="{ item: i }">{{ cant(i.cantidad) }}</template>
            <template #item.cantidadRecibida="{ item: i }">{{ cant(i.cantidadRecibida) }}</template>
            <template #item.pendiente="{ item: i }">{{ cant(i.pendiente) }}</template>
            <template #item.costoUnitario="{ item: i }">{{ cop(i.costoUnitario) }}</template>
          </v-data-table>
          <div class="text-caption text-medium-emphasis mt-2">Subtotal {{ cop(item.subtotal) }} · IVA {{ cop(item.impuestos) }} · Total {{ cop(item.total) }}<template v-if="item.bodegaDestino"> · destino {{ item.bodegaDestino }}</template></div>
        </td></tr>
      </template>
    </v-data-table>
    <div class="d-flex align-center justify-space-between mt-2">
      <span class="text-caption text-medium-emphasis">{{ f.total }} orden(es)</span>
      <v-pagination v-if="f.pages > 1" v-model="f.page" :length="f.pages" :total-visible="7" @update:model-value="cargar" />
    </div>
    <OrdenDialog v-model="orden.dialog" :orden="orden.editando" @guardado="cargar" />
    <RecepcionDialog v-model="recep.dialog" :orden="recep.orden" @recibida="cargar" />
    <ConfirmDialog :model-value="!!accion.orden" :title="accion.tipo === 'enviar' ? 'Enviar orden' : 'Cancelar orden'" :color="accion.tipo === 'enviar' ? 'primary' : 'error'"
      :confirm-text="accion.tipo === 'enviar' ? 'Enviar' : 'Cancelar orden'" cancel-text="Volver" :loading="accion.trabajando"
      :message="accion.tipo === 'enviar' ? `La orden ${accion.orden?.codigo} dejará de ser editable y podrá recibirse.` : `Se cancelará la orden ${accion.orden?.codigo}.`"
      @update:model-value="accion.orden = null" @confirm="ejecutar" />
  </div>
</template>
