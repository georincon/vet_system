<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { estadoLote, listLotes } from '@/api/inventario'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { cant, cop } from '@/utils/dinero'
import { etiquetaFecha } from '@/utils/fecha'
import type { Bodega, Lote } from '@/types/fase4'
import MovimientoDialog, { type ModoMov } from './MovimientoDialog.vue'
import LoteDialog from './LoteDialog.vue'
import ItemSelect from './ItemSelect.vue'

defineProps<{ bodegas: Bodega[] }>()
const auth = useAuthStore()
const ui = useUiStore()
const lotes = ref<Lote[]>([])
const loading = ref(false)
const error = ref('')
const f = reactive({ itemId: null as number | null, bodegaId: null as number | null, conStock: true as boolean | null, soloVigentes: false as boolean | null })
const mov = reactive({ abierto: false, lote: null as Lote | null, modo: 'ajuste' as ModoMov })
const nuevoLote = ref(false)
const confirmCuarentena = ref<Lote | null>(null)

const headers = computed(() => [
  { title: 'Ítem', key: 'itemNombre', sortable: false },
  { title: 'Lote', key: 'numeroLote', sortable: false },
  { title: 'Bodega', key: 'bodega', sortable: false },
  { title: 'Vencimiento', key: 'fechaVencimiento', sortable: false },
  { title: 'Vigencia', key: 'vigencia', sortable: false },
  { title: 'Stock', key: 'cantidadActual', sortable: false, align: 'end' as const },
  ...(auth.can.inventarioCostos ? [{ title: 'Costo unit.', key: 'costoUnitario', sortable: false, align: 'end' as const }] : []),
  { title: 'Estado', key: 'estado', sortable: false },
  ...(auth.can.inventarioUso ? [{ title: '', key: 'acciones', sortable: false, align: 'end' as const }] : []),
])

/** Clasifica el lote: vencido / por vencer (30 d) / vigente. */
function vigencia(l: Lote): { text: string; color: string } {
  if (l.vencido) return { text: 'Vencido', color: 'error' }
  if (l.diasParaVencer !== null && l.diasParaVencer !== undefined && l.diasParaVencer <= 30) return { text: `Por vencer (${l.diasParaVencer} d)`, color: 'warning' }
  if (!l.fechaVencimiento) return { text: 'Sin vencimiento', color: 'grey' }
  return { text: 'Vigente', color: 'success' }
}

async function cargar() {
  loading.value = true; error.value = ''
  try { lotes.value = await listLotes({ itemId: f.itemId, bodegaId: f.bodegaId, conStock: f.conStock, soloVigentes: f.soloVigentes }) }
  catch (e) { error.value = errorText(e, 'No se pudieron cargar los lotes') } finally { loading.value = false }
}
function abrir(l: Lote, modo: ModoMov) { mov.lote = l; mov.modo = modo; mov.abierto = true }
async function cambiarCuarentena() {
  const l = confirmCuarentena.value
  if (!l) return
  try {
    await estadoLote(l.id, l.estado === 'CUARENTENA' ? 'DISPONIBLE' : 'CUARENTENA')
    ui.showSuccess('Estado del lote actualizado')
    await cargar()
  } catch (e) { ui.showError(errorText(e, 'No se pudo cambiar el estado')) }
  confirmCuarentena.value = null
}
defineExpose({ recargar: cargar })
onMounted(cargar)
</script>

<template>
  <div>
    <div class="d-flex flex-wrap ga-2 mb-2 align-center">
      <div class="vet-filtro-ancho flex-grow-1"><ItemSelect v-model="f.itemId" label="Ítem" density="comfortable" hide-details :controla-stock="true" @update:model-value="cargar" /></div>
      <v-select v-model="f.bodegaId" :items="bodegas" item-title="nombre" item-value="id" label="Bodega" clearable hide-details density="comfortable" class="vet-filtro" @update:model-value="cargar" />
      <v-switch v-model="f.conStock" :true-value="true" :false-value="null" label="Con stock" color="primary" density="compact" hide-details @update:model-value="cargar" />
      <v-switch v-model="f.soloVigentes" :true-value="true" :false-value="null" label="Solo vigentes" color="primary" density="compact" hide-details @update:model-value="cargar" />
      <v-btn v-if="auth.can.inventarioWrite" color="primary" variant="flat" prepend-icon="mdi-plus" @click="nuevoLote = true">Cargar lote</v-btn>
    </div>
    <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-2" :text="error" />
    <v-data-table :headers="headers" :items="lotes" :loading="loading" :items-per-page="50" density="comfortable" no-data-text="Sin lotes para estos filtros">
      <template #item.itemNombre="{ item }">{{ item.itemNombre }}<div class="text-caption text-medium-emphasis">{{ item.itemCodigo }}</div></template>
      <template #item.fechaVencimiento="{ item }">{{ etiquetaFecha(item.fechaVencimiento) }}</template>
      <template #item.vigencia="{ item }"><v-chip size="small" variant="tonal" :color="vigencia(item).color" :text="vigencia(item).text" /></template>
      <template #item.cantidadActual="{ item }">{{ cant(item.cantidadActual) }}<div class="text-caption text-medium-emphasis">de {{ cant(item.cantidadInicial) }}</div></template>
      <template #item.costoUnitario="{ item }">{{ cop(item.costoUnitario) }}</template>
      <template #item.estado="{ item }"><v-chip size="small" variant="tonal" :color="item.estado === 'DISPONIBLE' ? 'success' : item.estado === 'CUARENTENA' ? 'warning' : 'grey'" :text="item.estado" /></template>
      <template #item.acciones="{ item }">
        <v-menu>
          <template #activator="{ props: p }"><v-btn v-bind="p" icon="mdi-dots-vertical" size="small" variant="text" aria-label="Acciones del lote" /></template>
          <v-list density="compact">
            <template v-if="auth.can.inventarioWrite">
              <v-list-item prepend-icon="mdi-scale-balance" title="Ajustar existencias" @click="abrir(item, 'ajuste')" />
              <v-list-item prepend-icon="mdi-package-down" title="Salida con motivo" @click="abrir(item, 'salida')" />
              <v-list-item v-if="item.estado !== 'RETIRADO' && item.estado !== 'VENCIDO'" prepend-icon="mdi-shield-alert-outline"
                :title="item.estado === 'CUARENTENA' ? 'Liberar de cuarentena' : 'Poner en cuarentena'" @click="confirmCuarentena = item" />
              <v-list-item v-if="item.estado !== 'RETIRADO' && item.estado !== 'VENCIDO'" prepend-icon="mdi-delete-outline" title="Retirar lote" base-color="error" @click="abrir(item, 'retiro')" />
            </template>
            <v-list-item v-else prepend-icon="mdi-package-down" title="Registrar uso" @click="abrir(item, 'uso')" />
          </v-list>
        </v-menu>
      </template>
    </v-data-table>
    <MovimientoDialog v-model="mov.abierto" :lote="mov.lote" :modo="mov.modo" @hecho="cargar" />
    <LoteDialog v-model="nuevoLote" :bodegas="bodegas" @guardado="cargar" />
    <ConfirmDialog :model-value="!!confirmCuarentena" title="Cambiar estado del lote" color="primary" confirm-text="Confirmar"
      :message="confirmCuarentena?.estado === 'CUARENTENA' ? 'El lote volverá a estar disponible para ventas y usos.' : 'En cuarentena el lote no se usará ni se venderá (FEFO lo omite).'"
      @update:model-value="confirmCuarentena = null" @confirm="cambiarCuarentena" />
  </div>
</template>
