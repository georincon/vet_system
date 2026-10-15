<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useCajaStore } from '@/stores/caja'
import { cop } from '@/utils/dinero'
import { hoy, sumarDias } from '@/utils/fecha'
import type { PagoInput } from '@/types/fase4'
import PagosEditor from './PagosEditor.vue'
import SinTurnoAviso from './SinTurnoAviso.vue'

export interface FacturarDatos { condicionPago: 'CONTADO' | 'CREDITO'; fechaVencimiento?: string; notas?: string; pagos: PagoInput[] }
const props = defineProps<{ modelValue: boolean; total: number; tutorEsConsumidorFinal?: boolean; enviando?: boolean; error?: string }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; confirmar: [d: FacturarDatos]; abrirCaja: [] }>()
const caja = useCajaStore()

const form = reactive({ condicionPago: 'CONTADO' as 'CONTADO' | 'CREDITO', fechaVencimiento: '', notas: '', cobrarAhora: true })
const pagos = ref<PagoInput[]>([])
const pagosRef = ref<InstanceType<typeof PagosEditor> | null>(null)
const local = ref('')

watch(() => props.modelValue, (o) => {
  if (!o) return
  Object.assign(form, { condicionPago: 'CONTADO', fechaVencimiento: sumarDias(hoy(), 30), notas: '', cobrarAhora: true })
  pagos.value = [{ metodo: 'EFECTIVO', monto: props.total, referencia: '' }]
  local.value = ''
  void caja.refrescar()
})
const credito = computed(() => form.condicionPago === 'CREDITO')
const conPagos = computed(() => form.cobrarAhora && pagos.value.some((p) => p.monto > 0))
const necesitaTurno = computed(() => conPagos.value)

function confirmar() {
  local.value = ''
  if (necesitaTurno.value && !caja.abierto) { local.value = 'Para registrar pagos abra primero su turno de caja (o desmarque "Registrar pago ahora").'; return }
  if (conPagos.value && !pagosRef.value?.cuadra) { local.value = credito.value ? 'Los pagos exceden el total' : 'En contado los pagos deben sumar el total'; return }
  if (credito.value && props.tutorEsConsumidorFinal) { local.value = 'Consumidor final no compra a crédito: asigne un tutor'; return }
  emit('confirmar', {
    condicionPago: form.condicionPago, fechaVencimiento: credito.value ? form.fechaVencimiento || undefined : undefined,
    notas: form.notas.trim() || undefined,
    pagos: conPagos.value ? pagos.value.filter((p) => p.monto > 0).map((p) => ({ metodo: p.metodo, monto: Number(p.monto), referencia: p.referencia?.trim() || undefined })) : [],
  })
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="760" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">Facturar · {{ cop(total) }}</v-card-title>
      <v-card-text>
        <v-alert v-if="error || local" type="error" variant="tonal" density="compact" class="mb-3" :text="local || error" />
        <v-btn-toggle v-model="form.condicionPago" mandatory density="comfortable" color="primary" variant="outlined" divided class="mb-3">
          <v-btn value="CONTADO">Contado</v-btn>
          <v-btn value="CREDITO">Crédito</v-btn>
        </v-btn-toggle>
        <v-text-field v-if="credito" v-model="form.fechaVencimiento" type="date" label="Vence el" :min="hoy()" class="vet-filtro" />
        <v-switch v-model="form.cobrarAhora" color="primary" density="compact" hide-details :label="credito ? 'Registrar un abono ahora' : 'Registrar pago ahora (si no, queda emitida para pagar luego)'" />
        <template v-if="form.cobrarAhora">
          <SinTurnoAviso v-if="caja.cargado && !caja.abierto" class="mt-3" @abrir="emit('abrirCaja'); emit('update:modelValue', false)" />
          <div class="mt-3"><PagosEditor ref="pagosRef" v-model="pagos" :total="total" :parcial="credito" /></div>
        </template>
        <v-text-field v-model="form.notas" label="Notas" density="comfortable" hide-details class="mt-3" />
      </v-card-text>
      <v-card-actions class="px-4 pb-4"><v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn color="primary" variant="flat" :loading="enviando" @click="confirmar">Emitir factura</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
