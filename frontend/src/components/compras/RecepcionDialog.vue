<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { listBodegas, recibirOrden } from '@/api/inventario'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { cant, cop } from '@/utils/dinero'
import { hoy } from '@/utils/fecha'
import type { Bodega, Orden } from '@/types/fase4'

const props = defineProps<{ modelValue: boolean; orden: Orden | null }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; recibida: [] }>()
const ui = useUiStore()

interface Fila { ordenCompraItemId: number; itemId: number; nombre: string; pendiente: number; cantidad: number; numeroLote: string; fechaVencimiento: string; costoUnitario: number }
const filas = ref<Fila[]>([])
const bodegas = ref<Bodega[]>([])
const form = reactive({ bodegaId: null as number | null, numeroFacturaProveedor: '', notas: '' })
const guardando = ref(false)
const error = ref('')

watch(() => props.modelValue, async (o) => {
  if (!o || !props.orden) return
  error.value = ''
  const ord = props.orden
  Object.assign(form, { bodegaId: ord.bodegaDestinoId ?? null, numeroFacturaProveedor: '', notas: '' })
  filas.value = ord.items.filter((i) => i.pendiente > 0).map((i) => ({
    ordenCompraItemId: i.id, itemId: i.itemId, nombre: i.nombre, pendiente: i.pendiente, cantidad: i.pendiente,
    numeroLote: '', fechaVencimiento: '', costoUnitario: i.costoUnitario,
  }))
  try {
    bodegas.value = await listBodegas()
    if (!form.bodegaId) form.bodegaId = bodegas.value[0]?.id ?? null
  } catch (e) { error.value = errorText(e, 'No se pudieron cargar las bodegas') }
})

async function guardar() {
  error.value = ''
  const aRecibir = filas.value.filter((f) => f.cantidad > 0)
  if (!aRecibir.length) { error.value = 'Indique la cantidad recibida de al menos un renglón'; return }
  const mal = aRecibir.find((f) => !f.numeroLote.trim() || f.cantidad > f.pendiente || f.costoUnitario < 0)
  if (mal) {
    error.value = !mal.numeroLote.trim() ? `Falta el número de lote de ${mal.nombre}` : mal.cantidad > mal.pendiente ? `${mal.nombre}: no puede recibir más de lo pendiente (${cant(mal.pendiente)})` : `Costo inválido en ${mal.nombre}`
    return
  }
  guardando.value = true
  try {
    await recibirOrden(props.orden!.id, {
      bodegaId: form.bodegaId, numeroFacturaProveedor: form.numeroFacturaProveedor.trim() || undefined, fecha: hoy(), notas: form.notas.trim() || undefined,
      items: aRecibir.map((f) => ({
        ordenCompraItemId: f.ordenCompraItemId, itemId: f.itemId, cantidad: Number(f.cantidad), numeroLote: f.numeroLote.trim(),
        fechaVencimiento: f.fechaVencimiento || null, costoUnitario: Number(f.costoUnitario),
      })),
    })
    ui.showSuccess('Mercancía recibida: lotes creados y kardex actualizado')
    emit('update:modelValue', false); emit('recibida')
  } catch (e) { error.value = errorText(e, 'No se pudo registrar la recepción') } finally { guardando.value = false }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="1000" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card v-if="orden">
      <v-card-title class="pt-4">Recibir mercancía · {{ orden.codigo }}</v-card-title>
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <p class="text-body-2 text-medium-emphasis mb-3">Puede recibir total o parcialmente: deje en 0 los renglones que no llegaron. Cada renglón crea un lote con su vencimiento y costo.</p>
        <v-row dense>
          <v-col cols="12" sm="6"><v-select v-model="form.bodegaId" :items="bodegas" item-title="nombre" item-value="id" label="Bodega de ingreso" /></v-col>
          <v-col cols="12" sm="6"><v-text-field v-model="form.numeroFacturaProveedor" label="Nº factura del proveedor" /></v-col>
        </v-row>
        <div v-for="f in filas" :key="f.ordenCompraItemId" class="vet-info-bar mt-3">
          <div class="font-weight-bold mb-2">{{ f.nombre }} <span class="text-caption text-medium-emphasis">· pendiente {{ cant(f.pendiente) }}</span></div>
          <v-row dense>
            <v-col cols="6" md="2"><v-text-field v-model.number="f.cantidad" type="number" min="0" :max="f.pendiente" step="any" label="Recibe" density="comfortable" hide-details /></v-col>
            <v-col cols="6" md="3"><v-text-field v-model="f.numeroLote" label="Nº de lote" density="comfortable" hide-details /></v-col>
            <v-col cols="6" md="3"><v-text-field v-model="f.fechaVencimiento" type="date" label="Vencimiento" :min="hoy()" density="comfortable" hide-details /></v-col>
            <v-col cols="6" md="4"><v-text-field v-model.number="f.costoUnitario" type="number" min="0" label="Costo unitario" prefix="$" density="comfortable" hide-details :hint="cop(f.costoUnitario)" /></v-col>
          </v-row>
        </div>
        <v-textarea v-model="form.notas" label="Notas" rows="2" auto-grow class="mt-3" />
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn color="primary" variant="flat" :loading="guardando" @click="guardar">Registrar recepción</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
