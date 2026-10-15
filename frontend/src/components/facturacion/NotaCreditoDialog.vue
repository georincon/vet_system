<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { notaCredito, type NotaCreditoResp } from '@/api/facturacion'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useCajaStore } from '@/stores/caja'
import { errorText } from '@/utils/errors'
import { cant, cop } from '@/utils/dinero'
import { METODOS_PAGO, type Factura } from '@/types/fase4'

const props = defineProps<{ modelValue: boolean; factura: Factura | null }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; emitida: [r: NotaCreditoResp] }>()
const caja = useCajaStore()

interface Fila { facturaItemId: number; descripcion: string; maximo: number; cantidad: number; controlaStock: boolean }
const filas = ref<Fila[]>([])
const total = ref(true)
const form = reactive({ motivo: '', reintegrarStock: true, metodo: 'EFECTIVO' })
const error = ref('')
const confirmar = ref(false)
const enviando = ref(false)

const hayPagado = computed(() => (props.factura?.pagado ?? 0) > 0)
watch(() => props.modelValue, (o) => {
  if (!o || !props.factura) return
  error.value = ''; total.value = true
  Object.assign(form, { motivo: '', reintegrarStock: true, metodo: 'EFECTIVO' })
  filas.value = props.factura.items.map((i) => {
    const max = Number(i.cantidad) - Number(i.cantidadAcreditada ?? 0)
    return { facturaItemId: i.id as number, descripcion: i.descripcion ?? '', maximo: max, cantidad: max, controlaStock: !!i.itemId }
  }).filter((f) => f.maximo > 0)
})

function revisar() {
  error.value = ''
  if (!form.motivo.trim()) { error.value = 'El motivo es obligatorio'; return }
  if (!total.value) {
    const sel = filas.value.filter((f) => f.cantidad > 0)
    if (!sel.length) { error.value = 'Indique la cantidad a acreditar de al menos un renglón'; return }
    if (sel.some((f) => f.cantidad > f.maximo)) { error.value = 'No puede acreditar más de lo facturado'; return }
  }
  if (hayPagado.value && form.metodo === 'EFECTIVO' && !caja.abierto) { error.value = 'La devolución en efectivo exige un turno de caja abierto. Ábralo o elija otro método.'; return }
  confirmar.value = true
}
async function emitir() {
  if (!props.factura) return
  enviando.value = true
  try {
    const r = await notaCredito(props.factura.id, {
      motivo: form.motivo.trim(), reintegrarStock: form.reintegrarStock,
      items: total.value ? undefined : filas.value.filter((f) => f.cantidad > 0).map((f) => ({ facturaItemId: f.facturaItemId, cantidad: Number(f.cantidad) })),
      devolucion: hayPagado.value ? { metodo: form.metodo } : undefined,
    })
    confirmar.value = false
    emit('update:modelValue', false); emit('emitida', r)
  } catch (e) { confirmar.value = false; error.value = errorText(e, 'No se pudo emitir la nota crédito') } finally { enviando.value = false }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="720" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card v-if="factura">
      <v-card-title class="pt-4">Nota crédito · {{ factura.numero }}</v-card-title>
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-btn-toggle v-model="total" mandatory density="comfortable" color="primary" variant="outlined" divided class="mb-3">
          <v-btn :value="true">Anulación total</v-btn>
          <v-btn :value="false">Parcial por renglón</v-btn>
        </v-btn-toggle>
        <div v-if="!total" class="mb-3">
          <v-row v-for="f in filas" :key="f.facturaItemId" dense align="center">
            <v-col cols="8">{{ f.descripcion }} <span class="text-caption text-medium-emphasis">(máx. {{ cant(f.maximo) }})</span></v-col>
            <v-col cols="4"><v-text-field v-model.number="f.cantidad" type="number" min="0" :max="f.maximo" step="any" density="compact" hide-details label="Cantidad" /></v-col>
          </v-row>
        </div>
        <v-textarea v-model="form.motivo" label="Motivo (obligatorio)" rows="2" auto-grow />
        <v-switch v-model="form.reintegrarStock" color="primary" density="compact" hide-details label="Reintegrar al stock lo que descontó esta factura" />
        <div v-if="hayPagado" class="mt-3">
          <v-select v-model="form.metodo" :items="METODOS_PAGO" label="Método de devolución del dinero ya pagado" />
          <p class="text-caption text-medium-emphasis">La nota reduce primero el saldo; lo pagado que sobre se devuelve. En efectivo se registra un egreso en su turno abierto.</p>
        </div>
        <v-alert type="warning" variant="tonal" density="compact" class="mt-3" :text="`La nota crédito no puede deshacerse. Total de la factura: ${cop(factura.total)}.`" />
      </v-card-text>
      <v-card-actions class="px-4 pb-4"><v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn color="error" variant="flat" @click="revisar">Emitir nota crédito</v-btn>
      </v-card-actions>
    </v-card>
    <ConfirmDialog v-model="confirmar" title="Confirmar nota crédito" confirm-text="Sí, emitir" :loading="enviando"
      :message="`Se emitirá una nota crédito ${total ? 'total (la factura quedará anulada)' : 'parcial'}${form.reintegrarStock ? ' y se reintegrará el stock' : ' sin reintegrar stock'}. ¿Continuar?`" @confirm="emitir" />
  </v-dialog>
</template>
