<script setup lang="ts">
import { computed } from 'vue'
import ItemSelect from '@/components/inventario/ItemSelect.vue'
import { useAuthStore } from '@/stores/auth'
import { calcLinea, cant, cop } from '@/utils/dinero'
import type { Item } from '@/types/fase4'

export interface LineaUI {
  key: number
  itemId: number | null
  descripcion: string
  cantidad: number
  precioUnitario: number
  impuestoPct: number
  descuento: number
  /** Stock vigente conocido (solo informativo). */
  stock?: number | null
  controlaStock?: boolean
  /** Conservados al editar un presupuesto generado desde la consulta. */
  extra?: { consultaId?: number | null; pacienteId?: number | null; veterinarioId?: number | null }
  /** Valores del catálogo para detectar cambios de precio/IVA. */
  catPrecio?: number
  catIva?: number
}

const props = defineProps<{ modelValue: LineaUI[]; readonly?: boolean; faltantes?: number[] }>()
const emit = defineEmits<{ 'update:modelValue': [v: LineaUI[]] }>()
const auth = useAuthStore()
const esAdmin = computed(() => auth.isAdmin)
let seq = 1000

const calc = (l: LineaUI) => calcLinea(Number(l.cantidad) || 0, Number(l.precioUnitario) || 0, Number(l.descuento) || 0, Number(l.impuestoPct) || 0)
const totales = computed(() => {
  let bruto = 0, descuento = 0, impuestos = 0, total = 0
  for (const l of props.modelValue) {
    const c = calc(l)
    bruto += c.bruto; descuento += Number(l.descuento) || 0; impuestos += c.impuesto; total += c.total
  }
  return { bruto, descuento, impuestos, total }
})

function agregarItem(i: Item | null) {
  if (!i) return
  const existente = props.modelValue.find((l) => l.itemId === i.id)
  if (existente) { set(existente, { cantidad: Number(existente.cantidad) + 1 }); return }
  emit('update:modelValue', [...props.modelValue, {
    key: ++seq, itemId: i.id, descripcion: i.nombre, cantidad: 1, precioUnitario: i.precioVenta, impuestoPct: i.impuestoPct, descuento: 0,
    stock: i.stockActual ?? null, controlaStock: i.controlaStock, catPrecio: i.precioVenta, catIva: i.impuestoPct,
  }])
}
function agregarLibre() {
  emit('update:modelValue', [...props.modelValue, { key: ++seq, itemId: null, descripcion: '', cantidad: 1, precioUnitario: 0, impuestoPct: 0, descuento: 0 }])
}
function set(l: LineaUI, parche: Partial<LineaUI>) { emit('update:modelValue', props.modelValue.map((x) => (x.key === l.key ? { ...x, ...parche } : x))) }
function quitar(l: LineaUI) { emit('update:modelValue', props.modelValue.filter((x) => x.key !== l.key)) }
const sinStock = (l: LineaUI) => !!l.controlaStock && l.stock !== null && l.stock !== undefined && Number(l.cantidad) > l.stock
defineExpose({ totales })
</script>

<template>
  <div>
    <div v-if="!readonly" class="d-flex flex-wrap ga-2 mb-3 align-center">
      <div class="flex-grow-1 vet-filtro-ancho">
        <ItemSelect :model-value="null" label="Buscar ítem o servicio (código o nombre)" density="comfortable" hide-details @seleccion="agregarItem" />
      </div>
      <v-btn v-if="esAdmin" variant="tonal" prepend-icon="mdi-text-box-plus-outline" @click="agregarLibre">Renglón libre</v-btn>
    </div>
    <v-table density="comfortable" class="vet-tabla-lineas">
      <thead>
        <tr><th>Descripción</th><th class="text-end">Cant.</th><th class="text-end">Precio</th><th class="text-end">Desc. ($)</th><th class="text-end">IVA</th><th class="text-end">Total</th><th v-if="!readonly"></th></tr>
      </thead>
      <tbody>
        <tr v-if="!modelValue.length"><td :colspan="readonly ? 6 : 7" class="text-center text-medium-emphasis py-4">Sin renglones. Busque un ítem para agregarlo.</td></tr>
        <tr v-for="l in modelValue" :key="l.key" :class="{ 'vet-linea-falta': !!l.itemId && faltantes?.includes(l.itemId) }">
          <td>
            <v-text-field v-if="!l.itemId && !readonly" :model-value="l.descripcion" label="Descripción" density="compact" hide-details @update:model-value="set(l, { descripcion: $event })" />
            <template v-else>{{ l.descripcion }}</template>
            <div v-if="sinStock(l)" class="text-caption text-warning">Stock vigente {{ cant(l.stock) }} (menor a lo solicitado)</div>
            <div v-else-if="l.controlaStock && l.stock !== null && l.stock !== undefined" class="text-caption text-medium-emphasis">Stock {{ cant(l.stock) }}</div>
            <div v-if="!!l.itemId && faltantes?.includes(l.itemId)" class="text-caption text-error">Sin stock suficiente</div>
          </td>
          <td class="text-end vet-celda-num">
            <v-text-field v-if="!readonly" :model-value="l.cantidad" type="number" min="0" step="any" density="compact" hide-details aria-label="Cantidad" @update:model-value="set(l, { cantidad: Number($event) })" />
            <template v-else>{{ cant(l.cantidad) }}</template>
          </td>
          <td class="text-end vet-celda-precio">
            <v-text-field v-if="!readonly && (esAdmin || !l.itemId)" :model-value="l.precioUnitario" type="number" min="0" density="compact" hide-details aria-label="Precio" @update:model-value="set(l, { precioUnitario: Number($event) })" />
            <span v-else class="vet-monto">{{ cop(l.precioUnitario) }}</span>
          </td>
          <td class="text-end vet-celda-precio">
            <v-text-field v-if="!readonly" :model-value="l.descuento" type="number" min="0" density="compact" hide-details aria-label="Descuento" @update:model-value="set(l, { descuento: Number($event) })" />
            <span v-else class="vet-monto">{{ cop(l.descuento) }}</span>
          </td>
          <td class="text-end vet-celda-num">
            <v-select v-if="!readonly && (esAdmin || !l.itemId)" :model-value="l.impuestoPct" :items="[0, 5, 19]" density="compact" hide-details aria-label="IVA" @update:model-value="set(l, { impuestoPct: $event })" />
            <template v-else>{{ l.impuestoPct }}%</template>
          </td>
          <td class="text-end vet-monto"><strong>{{ cop(calc(l).total) }}</strong></td>
          <td v-if="!readonly" class="text-end"><v-btn icon="mdi-close" size="small" variant="text" aria-label="Quitar renglón" @click="quitar(l)" /></td>
        </tr>
      </tbody>
    </v-table>
    <div class="d-flex flex-column align-end mt-3">
      <div class="text-body-2 vet-monto">Subtotal {{ cop(totales.bruto) }} · Descuento {{ cop(totales.descuento) }} · IVA {{ cop(totales.impuestos) }}</div>
      <div class="vet-total-grande vet-monto">Total {{ cop(totales.total) }}</div>
    </div>
  </div>
</template>
