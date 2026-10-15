<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ajustarLote, registrarSalida, retirarLote } from '@/api/inventario'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { cant } from '@/utils/dinero'
import { MOTIVOS_SALIDA, type Lote } from '@/types/fase4'

export type ModoMov = 'ajuste' | 'salida' | 'retiro' | 'uso'
const props = defineProps<{ modelValue: boolean; lote: Lote | null; modo: ModoMov }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; hecho: [] }>()
const ui = useUiStore()

const form = reactive({ cantidad: 1 as number | null, cantidadReal: null as number | null, motivo: '', motivoSalida: 'MERMA', observaciones: '' })
const guardando = ref(false)
const error = ref('')
const titulo = computed(() => ({ ajuste: 'Ajuste de existencias', salida: 'Salida con motivo', retiro: 'Retirar lote', uso: 'Registrar uso' })[props.modo])
const lote = computed(() => props.lote)

watch(() => props.modelValue, (o) => {
  if (!o) return
  error.value = ''
  Object.assign(form, { cantidad: 1, cantidadReal: props.lote?.cantidadActual ?? null, motivo: '', motivoSalida: 'MERMA', observaciones: '' })
})

async function guardar() {
  const l = props.lote
  if (!l) return
  error.value = ''
  if (props.modo !== 'uso' && !form.motivo.trim() && props.modo !== 'salida') { error.value = 'El motivo es obligatorio'; return }
  guardando.value = true
  try {
    if (props.modo === 'ajuste') {
      if (form.cantidadReal === null || form.cantidadReal < 0) { error.value = 'Indique la cantidad real contada'; return }
      const r = await ajustarLote({ loteId: l.id, cantidadReal: Number(form.cantidadReal), motivo: form.motivo.trim() })
      ui.showSuccess(`Lote ${r.numeroLote}: ${cant(r.anterior)} → ${cant(r.actual)}`)
    } else if (props.modo === 'retiro') {
      await retirarLote(l.id, form.motivo.trim())
      ui.showSuccess(`Lote ${l.numeroLote} retirado de existencias`)
    } else {
      if (!form.cantidad || form.cantidad <= 0) { error.value = 'Cantidad inválida'; return }
      const r = await registrarSalida({
        itemId: l.itemId, loteId: l.id, bodegaId: l.bodegaId, cantidad: Number(form.cantidad),
        ...(props.modo === 'salida' ? { motivo: form.motivoSalida } : {}), observaciones: form.observaciones.trim() || undefined,
      }, props.modo === 'uso')
      ui.showSuccess(`Salida registrada. Stock restante: ${cant(r.stockRestante)}`)
    }
    emit('update:modelValue', false)
    emit('hecho')
  } catch (e) { error.value = errorText(e, 'No se pudo registrar el movimiento') } finally { guardando.value = false }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="520" @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">{{ titulo }}</v-card-title>
      <v-card-text>
        <div v-if="lote" class="vet-info-bar mb-3">
          <strong>{{ lote.itemNombre }}</strong> · lote {{ lote.numeroLote }} · {{ lote.bodega }}<br />
          Existencias actuales: <strong>{{ cant(lote.cantidadActual) }}</strong>
        </div>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-text-field v-if="modo === 'ajuste'" v-model.number="form.cantidadReal" type="number" min="0" step="any" label="Cantidad real contada" />
        <v-text-field v-if="modo === 'salida' || modo === 'uso'" v-model.number="form.cantidad" type="number" min="0" step="any" label="Cantidad" />
        <v-select v-if="modo === 'salida'" v-model="form.motivoSalida" :items="MOTIVOS_SALIDA" label="Motivo de la salida" />
        <v-textarea v-if="modo === 'ajuste' || modo === 'retiro'" v-model="form.motivo" label="Motivo (obligatorio)" rows="2" auto-grow />
        <v-textarea v-if="modo === 'salida' || modo === 'uso'" v-model="form.observaciones" :label="modo === 'salida' ? 'Detalle (opcional)' : 'Observaciones'" rows="2" auto-grow />
        <v-alert v-if="modo === 'retiro'" type="warning" variant="tonal" density="compact" text="El lote sale de las existencias (vencimiento/baja) y no puede reactivarse." />
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn :color="modo === 'retiro' ? 'error' : 'primary'" variant="flat" :loading="guardando" @click="guardar">Confirmar</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
