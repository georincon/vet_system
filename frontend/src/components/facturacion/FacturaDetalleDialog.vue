<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getFactura, pagarFactura, reintentarDian, type NotaCreditoResp } from '@/api/facturacion'
import EstadoChip from '@/components/EstadoChip.vue'
import { useAuthStore } from '@/stores/auth'
import { useCajaStore } from '@/stores/caja'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { cant, cop } from '@/utils/dinero'
import { etiquetaFecha, etiquetaInstante } from '@/utils/fecha'
import { METODOS_PAGO, type Factura, type PagoInput } from '@/types/fase4'
import NotaCreditoDialog from './NotaCreditoDialog.vue'
import PagosEditor from './PagosEditor.vue'
import SinTurnoAviso from './SinTurnoAviso.vue'

const props = defineProps<{ modelValue: boolean; facturaId: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; cambio: []; abrirCaja: [] }>()
const auth = useAuthStore()
const caja = useCajaStore()
const ui = useUiStore()

const f = ref<Factura | null>(null)
const loading = ref(false)
const error = ref('')
const pagando = ref(false)
const pagos = ref<PagoInput[]>([])
const guardando = ref(false)
const nota = ref(false)
const resultado = ref<NotaCreditoResp | null>(null)
const metodo = (m: string) => METODOS_PAGO.find((x) => x.value === m)?.title ?? m

const puedePagar = computed(() => !!f.value && auth.can.facturacionOperar && f.value.saldo > 0 && f.value.estado !== 'ANULADA' && f.value.tipoDocumento !== 'NOTA_CREDITO')
const puedeNota = computed(() => !!f.value && auth.can.notasCredito && f.value.estado !== 'ANULADA' && f.value.tipoDocumento !== 'NOTA_CREDITO')

async function cargar() {
  if (!props.facturaId) return
  loading.value = true; error.value = ''
  try { f.value = await getFactura(props.facturaId) } catch (e) { error.value = errorText(e, 'No se pudo cargar la factura') } finally { loading.value = false }
}
watch(() => [props.modelValue, props.facturaId] as const, ([o]) => {
  if (!o) return
  f.value = null; pagando.value = false; pagos.value = []; resultado.value = null
  void cargar()
  void caja.refrescar()
})
function iniciarPago() {
  pagando.value = true
  pagos.value = [{ metodo: 'EFECTIVO', monto: f.value?.saldo ?? 0, referencia: '' }]
}
async function registrarPagos() {
  if (!f.value) return
  const validos = pagos.value.filter((p) => p.monto > 0)
  if (!validos.length) { error.value = 'Indique al menos un pago'; return }
  guardando.value = true; error.value = ''
  try {
    for (const p of validos) f.value = await pagarFactura(f.value.id, { metodo: p.metodo, monto: Number(p.monto), referencia: p.referencia?.trim() || undefined })
    ui.showSuccess('Pago registrado')
    pagando.value = false
    emit('cambio'); void caja.refrescar()
  } catch (e) { error.value = errorText(e, 'No se pudo registrar el pago'); await cargar(); emit('cambio') } finally { guardando.value = false }
}
async function dian() {
  if (!f.value) return
  try { f.value = await reintentarDian(f.value.id); ui.showSuccess('Reintento enviado al puerto DIAN') } catch (e) { error.value = errorText(e, 'No se pudo reintentar') }
}
async function notaEmitida(r: NotaCreditoResp) {
  resultado.value = r; f.value = r.factura
  emit('cambio'); void caja.refrescar()
}
const hItems = [
  { title: 'Descripción', key: 'descripcion', sortable: false }, { title: 'Cant.', key: 'cantidad', sortable: false, align: 'end' as const },
  { title: 'Precio', key: 'precioUnitario', sortable: false, align: 'end' as const }, { title: 'Desc.', key: 'descuento', sortable: false, align: 'end' as const },
  { title: 'IVA', key: 'impuesto', sortable: false, align: 'end' as const }, { title: 'Total', key: 'total', sortable: false, align: 'end' as const },
]
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="960" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4 d-flex flex-wrap align-center ga-2">
        <span>{{ f ? f.numero : 'Factura' }}</span>
        <EstadoChip v-if="f" :estado="f.estado" />
        <v-spacer />
        <v-btn icon="mdi-close" variant="text" size="small" aria-label="Cerrar" @click="emit('update:modelValue', false)" />
      </v-card-title>
      <v-card-text>
        <v-progress-linear v-if="loading" indeterminate class="mb-3" />
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-alert v-if="resultado" type="success" variant="tonal" density="compact" class="mb-3" closable @click:close="resultado = null"
          :text="`Nota crédito ${resultado.nota.numero} emitida. A devolver: ${cop(resultado.montoADevolver)}${resultado.devolucionRegistradaEnCaja ? ' (egreso registrado en caja)' : ''}. Stock reintegrado: ${cant(resultado.stockReintegrado)}.`" />
        <template v-if="f">
          <div class="vet-info-bar mb-4">
            <v-row dense>
              <v-col cols="12" sm="6"><strong>{{ f.tutor.nombre }}</strong><div class="text-caption">{{ f.tutor.documento }}</div><div v-if="f.paciente" class="text-caption">Paciente: {{ f.paciente }}</div></v-col>
              <v-col cols="6" sm="3"><div class="text-caption">Fecha</div>{{ etiquetaInstante(f.fecha) }}</v-col>
              <v-col cols="6" sm="3"><div class="text-caption">Condición</div>{{ f.condicionPago === 'CREDITO' ? 'Crédito' : 'Contado' }}<div v-if="f.fechaVencimiento" class="text-caption">vence {{ etiquetaFecha(f.fechaVencimiento) }}</div></v-col>
            </v-row>
            <div class="mt-2 d-flex flex-wrap align-center ga-2">
              <span class="text-caption">DIAN:</span><EstadoChip :estado="f.estadoDian" />
              <span class="text-caption text-medium-emphasis">{{ f.dianMensaje }}</span>
              <v-btn v-if="auth.isAdmin && f.estadoDian === 'PENDIENTE'" size="x-small" variant="text" @click="dian">Reintentar</v-btn>
            </div>
            <div v-if="f.numeroOrigen" class="text-caption mt-1">Nota sobre {{ f.numeroOrigen }}</div>
            <div v-if="f.motivoAnulacion" class="text-caption mt-1">Motivo: {{ f.motivoAnulacion }}</div>
          </div>

          <v-data-table :headers="hItems" :items="f.items" :items-per-page="-1" hide-default-footer density="comfortable">
            <template #item.cantidad="{ item }">{{ cant(item.cantidad) }}<div v-if="item.cantidadAcreditada" class="text-caption text-error">acreditada {{ cant(item.cantidadAcreditada) }}</div></template>
            <template #item.precioUnitario="{ item }">{{ cop(item.precioUnitario) }}</template>
            <template #item.descuento="{ item }">{{ cop(item.descuento) }}</template>
            <template #item.impuesto="{ item }">{{ cop(item.impuesto) }} <span class="text-caption">({{ item.impuestoPct }}%)</span></template>
            <template #item.total="{ item }"><strong>{{ cop(item.total) }}</strong></template>
          </v-data-table>

          <div class="d-flex flex-column align-end mt-3">
            <div class="vet-monto text-body-2">Subtotal {{ cop(f.subtotal) }} · descuento {{ cop(f.descuento) }} · IVA {{ cop(f.impuestos) }}</div>
            <div class="vet-monto text-h6">Total {{ cop(f.total) }}</div>
            <div class="vet-monto">Pagado {{ cop(f.pagado) }} · <strong :class="f.saldo > 0 ? 'text-error' : 'text-success'">Saldo {{ cop(f.saldo) }}</strong></div>
          </div>

          <h3 class="vet-section-title mt-4">Pagos y abonos</h3>
          <v-table density="comfortable">
            <thead><tr><th>Fecha</th><th>Método</th><th>Referencia</th><th>Registró</th><th class="text-end">Monto</th></tr></thead>
            <tbody>
              <tr v-if="!f.pagos.length"><td colspan="5" class="text-center text-medium-emphasis py-3">Sin pagos registrados</td></tr>
              <tr v-for="p in f.pagos" :key="p.id"><td>{{ etiquetaInstante(p.fecha) }}</td><td>{{ metodo(p.metodo) }}</td><td>{{ p.referencia ?? '—' }}</td><td>{{ p.usuario?.nombre ?? '—' }}</td><td class="text-end vet-monto">{{ cop(p.monto) }}</td></tr>
            </tbody>
          </v-table>

          <template v-if="f.notasCredito.length">
            <h3 class="vet-section-title mt-4">Notas crédito</h3>
            <v-table density="comfortable">
              <thead><tr><th>Número</th><th>Fecha</th><th class="text-end">Valor</th></tr></thead>
              <tbody><tr v-for="n in f.notasCredito" :key="n.id"><td>{{ n.numero }}</td><td>{{ etiquetaInstante(n.fecha) }}</td><td class="text-end vet-monto">{{ cop(n.total) }}</td></tr></tbody>
            </v-table>
          </template>

          <div v-if="pagando" class="mt-4">
            <SinTurnoAviso v-if="!caja.abierto" @abrir="emit('abrirCaja'); emit('update:modelValue', false)" />
            <PagosEditor v-model="pagos" :total="f.saldo" parcial titulo="Registrar pago" />
            <div class="d-flex justify-end ga-2 mt-3">
              <v-btn variant="text" @click="pagando = false">Cancelar</v-btn>
              <v-btn color="primary" variant="flat" :loading="guardando" :disabled="!caja.abierto" @click="registrarPagos">Registrar pago</v-btn>
            </div>
          </div>
        </template>
      </v-card-text>
      <v-card-actions v-if="f" class="px-4 pb-4 flex-wrap">
        <v-spacer />
        <v-btn v-if="puedeNota" color="error" variant="tonal" prepend-icon="mdi-file-undo-outline" @click="nota = true">Nota crédito</v-btn>
        <v-btn v-if="puedePagar && !pagando" color="primary" variant="flat" prepend-icon="mdi-cash-plus" @click="iniciarPago">Registrar pago</v-btn>
      </v-card-actions>
    </v-card>
    <NotaCreditoDialog v-model="nota" :factura="f" @emitida="notaEmitida" />
  </v-dialog>
</template>
