<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { listBodegas, listProveedores, saveOrden } from '@/api/inventario'
import ItemSelect from '@/components/inventario/ItemSelect.vue'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { cop, r2 } from '@/utils/dinero'
import type { Bodega, Item, Orden, Proveedor } from '@/types/fase4'

const props = defineProps<{ modelValue: boolean; orden?: Orden | null }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; guardado: [o: Orden] }>()
const ui = useUiStore()

interface Fila { key: number; itemId: number | null; nombre: string; cantidad: number; costoUnitario: number; impuestoPct: number }
let seq = 0
const form = reactive({ proveedorId: null as number | null, bodegaDestinoId: null as number | null, fechaEsperada: '', notas: '' })
const filas = ref<Fila[]>([])
const proveedores = ref<Proveedor[]>([])
const bodegas = ref<Bodega[]>([])
const guardando = ref(false)
const error = ref('')

const total = computed(() => r2(filas.value.reduce((s, f) => s + f.cantidad * f.costoUnitario * (1 + f.impuestoPct / 100), 0)))
const nueva = (): Fila => ({ key: ++seq, itemId: null, nombre: '', cantidad: 1, costoUnitario: 0, impuestoPct: 0 })

watch(() => props.modelValue, async (o) => {
  if (!o) return
  error.value = ''
  const x = props.orden
  Object.assign(form, { proveedorId: x?.proveedorId ?? null, bodegaDestinoId: x?.bodegaDestinoId ?? null, fechaEsperada: x?.fechaEsperada ?? '', notas: x?.notas ?? '' })
  filas.value = x ? x.items.map((i) => ({ key: ++seq, itemId: i.itemId, nombre: i.nombre, cantidad: i.cantidad, costoUnitario: i.costoUnitario, impuestoPct: i.impuestoPct })) : [nueva()]
  try {
    const [p, b] = await Promise.all([listProveedores(undefined, 0, 100, true), listBodegas()])
    proveedores.value = p.items; bodegas.value = b
  } catch (e) { error.value = errorText(e, 'No se pudieron cargar proveedores y bodegas') }
})
function elegir(f: Fila, i: Item | null) {
  if (!i) return
  f.nombre = i.nombre
  f.impuestoPct = i.impuestoPct
  if (i.costo) f.costoUnitario = i.costo
}
async function guardar() {
  error.value = ''
  if (!form.proveedorId) { error.value = 'Seleccione un proveedor'; return }
  const validas = filas.value.filter((f) => f.itemId)
  if (!validas.length) { error.value = 'Agregue al menos un ítem'; return }
  if (validas.some((f) => !(f.cantidad > 0) || f.costoUnitario < 0)) { error.value = 'Revise cantidades y costos'; return }
  guardando.value = true
  try {
    const nuevo = !props.orden
    const g = await saveOrden(props.orden?.id ?? null, {
      proveedorId: form.proveedorId, bodegaDestinoId: form.bodegaDestinoId, fechaEsperada: form.fechaEsperada || null,
      notas: nuevo ? form.notas.trim() || null : form.notas.trim(),
      items: validas.map((f) => ({ itemId: f.itemId as number, cantidad: Number(f.cantidad), costoUnitario: Number(f.costoUnitario), impuestoPct: Number(f.impuestoPct) })),
    })
    ui.showSuccess(nuevo ? `Orden ${g.codigo} creada en borrador` : 'Orden actualizada')
    emit('update:modelValue', false); emit('guardado', g)
  } catch (e) { error.value = errorText(e, 'No se pudo guardar la orden') } finally { guardando.value = false }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="1000" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">{{ orden ? `Editar ${orden.codigo}` : 'Nueva orden de compra' }}</v-card-title>
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-row dense>
          <v-col cols="12" md="5"><v-select v-model="form.proveedorId" :items="proveedores" item-title="razonSocial" item-value="id" label="Proveedor" no-data-text="Cree primero un proveedor" /></v-col>
          <v-col cols="12" sm="6" md="4"><v-select v-model="form.bodegaDestinoId" :items="bodegas" item-title="nombre" item-value="id" label="Bodega destino" clearable /></v-col>
          <v-col cols="12" sm="6" md="3"><v-text-field v-model="form.fechaEsperada" type="date" label="Fecha esperada" /></v-col>
        </v-row>
        <h3 class="vet-section-title mt-2">Ítems</h3>
        <v-row v-for="f in filas" :key="f.key" dense align="center">
          <v-col cols="12" md="5"><ItemSelect v-model="f.itemId" label="Ítem" :controla-stock="true" density="comfortable" hide-details @seleccion="elegir(f, $event)" /></v-col>
          <v-col cols="4" md="2"><v-text-field v-model.number="f.cantidad" type="number" min="0" step="any" label="Cantidad" density="comfortable" hide-details /></v-col>
          <v-col cols="4" md="2"><v-text-field v-model.number="f.costoUnitario" type="number" min="0" label="Costo unit." prefix="$" density="comfortable" hide-details /></v-col>
          <v-col cols="3" md="2"><v-select v-model="f.impuestoPct" :items="[0, 5, 19]" label="IVA %" density="comfortable" hide-details /></v-col>
          <v-col cols="1" class="text-end"><v-btn icon="mdi-close" size="small" variant="text" aria-label="Quitar renglón" :disabled="filas.length === 1" @click="filas = filas.filter((x) => x !== f)" /></v-col>
        </v-row>
        <v-btn variant="tonal" prepend-icon="mdi-plus" class="mt-3" @click="filas.push(nueva())">Agregar ítem</v-btn>
        <v-textarea v-model="form.notas" label="Notas" rows="2" auto-grow class="mt-3" />
        <div class="text-end vet-total-grande vet-monto">Total estimado: {{ cop(total) }}</div>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn color="primary" variant="flat" :loading="guardando" @click="guardar">Guardar borrador</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
