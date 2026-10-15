<script setup lang="ts">
import { computed } from 'vue'
import { cop, r2 } from '@/utils/dinero'
import { METODOS_PAGO, type MetodoPago, type PagoInput } from '@/types/fase4'

const props = defineProps<{
  modelValue: PagoInput[]
  total: number
  /** Permite pagar menos del total (crédito, abonos). */
  parcial?: boolean
  titulo?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [v: PagoInput[]] }>()

const sumado = computed(() => r2(props.modelValue.reduce((s, p) => s + (Number(p.monto) || 0), 0)))
const diferencia = computed(() => r2(props.total - sumado.value))
const cuadra = computed(() => (props.parcial ? sumado.value <= props.total : diferencia.value === 0))

function set(i: number, parche: Partial<PagoInput>) {
  emit('update:modelValue', props.modelValue.map((p, k) => (k === i ? { ...p, ...parche } : p)))
}
function agregar(metodo: MetodoPago = 'EFECTIVO') {
  const resto = Math.max(0, diferencia.value)
  emit('update:modelValue', [...props.modelValue, { metodo, monto: resto, referencia: '' }])
}
function quitar(i: number) { emit('update:modelValue', props.modelValue.filter((_, k) => k !== i)) }
function completar(i: number) {
  const otros = r2(props.modelValue.reduce((s, p, k) => (k === i ? s : s + (Number(p.monto) || 0)), 0))
  set(i, { monto: Math.max(0, r2(props.total - otros)) })
}
defineExpose({ cuadra, sumado })
</script>

<template>
  <div>
    <h3 class="vet-section-title">{{ titulo ?? 'Pagos' }}</h3>
    <div v-for="(p, i) in modelValue" :key="i" class="d-flex flex-wrap ga-2 mb-2 align-center">
      <v-select :model-value="p.metodo" :items="METODOS_PAGO" label="Método" density="comfortable" hide-details class="vet-filtro" @update:model-value="set(i, { metodo: $event })" />
      <v-text-field :model-value="p.monto" type="number" min="0" label="Monto" prefix="$" density="comfortable" hide-details class="vet-filtro"
        @update:model-value="set(i, { monto: Number($event) })" />
      <v-text-field :model-value="p.referencia" label="Referencia" density="comfortable" hide-details class="vet-filtro" @update:model-value="set(i, { referencia: $event })" />
      <v-btn size="small" variant="text" title="Completar con el resto" @click="completar(i)">Resto</v-btn>
      <v-btn icon="mdi-close" size="small" variant="text" aria-label="Quitar pago" @click="quitar(i)" />
    </div>
    <div class="d-flex flex-wrap ga-2 align-center">
      <v-btn size="small" variant="tonal" prepend-icon="mdi-plus" @click="agregar()">Agregar pago</v-btn>
      <v-spacer />
      <span class="vet-monto">Total {{ cop(total) }} · pagado {{ cop(sumado) }}</span>
      <v-chip v-if="diferencia > 0 && !parcial" size="small" color="warning" variant="tonal" :text="`Falta ${cop(diferencia)}`" />
      <v-chip v-else-if="diferencia > 0 && parcial" size="small" color="info" variant="tonal" :text="`Saldo ${cop(diferencia)}`" />
      <v-chip v-else-if="diferencia < 0" size="small" color="error" variant="tonal" :text="`Excede en ${cop(-diferencia)}`" />
      <v-chip v-else size="small" color="success" variant="tonal" text="Cuadra" prepend-icon="mdi-check" />
    </div>
  </div>
</template>
