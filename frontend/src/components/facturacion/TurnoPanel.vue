<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { abrirTurno, cerrarTurno, listTurnos, movimientoCaja } from '@/api/facturacion'
import { useCajaStore } from '@/stores/caja'
import { useUiStore } from '@/stores/ui'
import EstadoChip from '@/components/EstadoChip.vue'
import { errorText } from '@/utils/errors'
import { cop, r2 } from '@/utils/dinero'
import { etiquetaInstante } from '@/utils/fecha'
import { METODOS_PAGO, type Turno } from '@/types/fase4'

const caja = useCajaStore()
const ui = useUiStore()
const apertura = ref<number | null>(null)
const error = ref('')
const trabajando = ref(false)
const cierre = reactive({ abierto: false, contado: null as number | null, observaciones: '' })
const mov = reactive({ abierto: false, tipo: 'INGRESO', monto: null as number | null, concepto: '' })
const cerrado = ref<Turno | null>(null)
const historial = ref<Turno[]>([])
const metodo = (m: string) => METODOS_PAGO.find((x) => x.value === m)?.title ?? m

const t = computed(() => caja.turno)
const esperado = computed(() => t.value?.resumen.efectivoEsperado ?? 0)
const dif = computed(() => (cierre.contado === null ? null : r2(Number(cierre.contado) - esperado.value)))

async function abrir() {
  error.value = ''
  if (apertura.value === null || apertura.value < 0) { error.value = 'Indique el monto inicial (puede ser 0)'; return }
  trabajando.value = true
  try {
    caja.set(await abrirTurno(Number(apertura.value)))
    cerrado.value = null
    ui.showSuccess('Turno de caja abierto')
  } catch (e) { error.value = errorText(e, 'No se pudo abrir el turno') } finally { trabajando.value = false }
}
async function registrarMovimiento() {
  error.value = ''
  if (!t.value || !mov.monto || mov.monto <= 0 || !mov.concepto.trim()) { error.value = 'Indique monto y concepto'; return }
  trabajando.value = true
  try {
    caja.set(await movimientoCaja(t.value.id, { tipo: mov.tipo, monto: Number(mov.monto), concepto: mov.concepto.trim() }))
    mov.abierto = false; mov.monto = null; mov.concepto = ''
    ui.showSuccess('Movimiento registrado')
  } catch (e) { error.value = errorText(e, 'No se pudo registrar el movimiento') } finally { trabajando.value = false }
}
async function cerrar() {
  error.value = ''
  if (!t.value || cierre.contado === null || cierre.contado < 0) { error.value = 'Indique el efectivo contado'; return }
  if (dif.value !== 0 && !cierre.observaciones.trim()) { error.value = 'Hay diferencia: las observaciones son obligatorias'; return }
  trabajando.value = true
  try {
    cerrado.value = await cerrarTurno(t.value.id, Number(cierre.contado), cierre.observaciones.trim())
    caja.set(null)
    cierre.abierto = false; cierre.contado = null; cierre.observaciones = ''
    ui.showSuccess('Turno cerrado')
    await cargarHistorial()
  } catch (e) { error.value = errorText(e, 'No se pudo cerrar el turno') } finally { trabajando.value = false }
}
async function cargarHistorial() {
  try { historial.value = (await listTurnos({ estado: 'CERRADO', size: 8 })).items } catch { historial.value = [] }
}
const hHist = [
  { title: 'Abierto', key: 'abiertoEn', sortable: false }, { title: 'Cerrado', key: 'cerradoEn', sortable: false }, { title: 'Usuario', key: 'usuario', sortable: false },
  { title: 'Esperado', key: 'efectivoEsperado', sortable: false, align: 'end' as const }, { title: 'Contado', key: 'efectivoContado', sortable: false, align: 'end' as const },
  { title: 'Diferencia', key: 'diferencia', sortable: false, align: 'end' as const },
]
onMounted(() => { void caja.refrescar(); void cargarHistorial() })
</script>

<template>
  <div>
    <v-alert v-if="error && !cierre.abierto && !mov.abierto" type="error" variant="tonal" density="compact" class="mb-3" :text="error" closable @click:close="error = ''" />

    <v-alert v-if="cerrado" type="success" variant="tonal" class="mb-4" closable title="Turno cerrado" @click:close="cerrado = null">
      <div class="vet-monto">Esperado {{ cop(cerrado.efectivoEsperado) }} · contado {{ cop(cerrado.efectivoContado) }} ·
        <strong :class="(cerrado.diferencia ?? 0) === 0 ? '' : 'text-error'">diferencia {{ cop(cerrado.diferencia) }}</strong></div>
      <div v-if="cerrado.observaciones" class="text-caption">{{ cerrado.observaciones }}</div>
    </v-alert>

    <template v-if="!t">
      <div class="vet-info-bar mb-4">
        <h3 class="vet-section-title">Abrir turno de caja</h3>
        <div class="d-flex flex-wrap ga-2 align-center">
          <v-text-field v-model.number="apertura" type="number" min="0" label="Monto inicial en efectivo" prefix="$" density="comfortable" hide-details class="vet-filtro" @keyup.enter="abrir" />
          <v-btn color="primary" variant="flat" prepend-icon="mdi-cash-register" :loading="trabajando" @click="abrir">Abrir caja</v-btn>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="d-flex flex-wrap align-center ga-2 mb-3">
        <EstadoChip estado="ABIERTO" />
        <span>{{ t.caja }} · abierto {{ etiquetaInstante(t.abiertoEn) }} por {{ t.usuario }}</span>
        <v-spacer />
        <v-btn variant="tonal" prepend-icon="mdi-cash-plus" @click="error = ''; mov.abierto = true">Ingreso / egreso</v-btn>
        <v-btn color="primary" variant="flat" prepend-icon="mdi-lock-check-outline" @click="error = ''; cierre.abierto = true">Cerrar caja</v-btn>
      </div>
      <v-row>
        <v-col cols="6" md="3"><div class="vet-info-bar"><div class="text-caption">Apertura</div><div class="text-h6 vet-monto">{{ cop(t.resumen.montoApertura) }}</div></div></v-col>
        <v-col cols="6" md="3"><div class="vet-info-bar"><div class="text-caption">Cobrado en efectivo</div><div class="text-h6 vet-monto">{{ cop(t.resumen.ingresosEfectivo) }}</div></div></v-col>
        <v-col cols="6" md="3"><div class="vet-info-bar"><div class="text-caption">Total cobrado</div><div class="text-h6 vet-monto">{{ cop(t.resumen.totalCobrado) }}</div></div></v-col>
        <v-col cols="6" md="3"><div class="vet-info-bar"><div class="text-caption">Efectivo esperado</div><div class="text-h6 vet-monto">{{ cop(t.resumen.efectivoEsperado) }}</div></div></v-col>
      </v-row>
      <h3 class="vet-section-title mt-4">Cobros por método</h3>
      <div v-if="t.resumen.ingresosPorMetodo.length" class="d-flex flex-wrap ga-2 mb-2">
        <v-chip v-for="m in t.resumen.ingresosPorMetodo" :key="m.metodo" variant="tonal" color="primary">{{ metodo(m.metodo) }}: {{ cop(m.monto) }} ({{ m.pagos }})</v-chip>
      </div>
      <p v-else class="text-medium-emphasis">Aún no hay cobros en este turno.</p>
      <p class="text-caption text-medium-emphasis">Ingresos manuales {{ cop(t.resumen.ingresosManuales) }} · egresos {{ cop(t.resumen.egresos) }} · retiros {{ cop(t.resumen.retiros) }}</p>
      <h3 class="vet-section-title mt-4">Movimientos del turno</h3>
      <v-table density="comfortable">
        <thead><tr><th>Hora</th><th>Tipo</th><th>Concepto</th><th class="text-end">Monto</th></tr></thead>
        <tbody>
          <tr v-if="!t.movimientos.length"><td colspan="4" class="text-center text-medium-emphasis py-3">Sin movimientos</td></tr>
          <tr v-for="m in t.movimientos" :key="m.id"><td>{{ etiquetaInstante(m.ocurridoEn) }}</td><td>{{ m.tipo }}</td><td>{{ m.concepto ?? '—' }}</td><td class="text-end vet-monto">{{ cop(m.monto) }}</td></tr>
        </tbody>
      </v-table>
    </template>

    <h3 class="vet-section-title mt-6">Últimos turnos cerrados</h3>
    <v-data-table :headers="hHist" :items="historial" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin turnos cerrados">
      <template #item.abiertoEn="{ item }">{{ etiquetaInstante(item.abiertoEn) }}</template>
      <template #item.cerradoEn="{ item }">{{ etiquetaInstante(item.cerradoEn) }}</template>
      <template #item.efectivoEsperado="{ item }">{{ cop(item.efectivoEsperado) }}</template>
      <template #item.efectivoContado="{ item }">{{ cop(item.efectivoContado) }}</template>
      <template #item.diferencia="{ item }"><v-chip size="small" variant="tonal" :color="(item.diferencia ?? 0) === 0 ? 'success' : 'error'" :text="cop(item.diferencia)" /></template>
    </v-data-table>

    <v-dialog v-model="cierre.abierto" max-width="520">
      <v-card>
        <v-card-title class="pt-4">Cerrar caja</v-card-title>
        <v-card-text>
          <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
          <div class="vet-info-bar mb-3">Efectivo esperado: <strong class="vet-monto">{{ cop(esperado) }}</strong></div>
          <v-text-field v-model.number="cierre.contado" type="number" min="0" label="Efectivo contado" prefix="$" autofocus />
          <v-alert v-if="dif !== null" :type="dif === 0 ? 'success' : 'warning'" variant="tonal" density="compact" class="mb-3"
            :text="dif === 0 ? 'La caja cuadra.' : dif > 0 ? `Sobrante de ${cop(dif)}` : `Faltante de ${cop(-dif)}`" />
          <v-textarea v-model="cierre.observaciones" :label="dif ? 'Observaciones (obligatorias por la diferencia)' : 'Observaciones'" rows="2" auto-grow />
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer />
          <v-btn variant="text" @click="cierre.abierto = false">Cancelar</v-btn>
          <v-btn color="primary" variant="flat" :loading="trabajando" @click="cerrar">Cerrar turno</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="mov.abierto" max-width="480">
      <v-card>
        <v-card-title class="pt-4">Movimiento de caja</v-card-title>
        <v-card-text>
          <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
          <v-select v-model="mov.tipo" :items="[{ title: 'Ingreso', value: 'INGRESO' }, { title: 'Egreso', value: 'EGRESO' }, { title: 'Retiro', value: 'RETIRO' }]" label="Tipo" />
          <v-text-field v-model.number="mov.monto" type="number" min="0" label="Monto" prefix="$" />
          <v-text-field v-model="mov.concepto" label="Concepto" />
          <p class="text-caption text-medium-emphasis">Egresos y retiros no pueden exceder el efectivo en caja.</p>
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer />
          <v-btn variant="text" @click="mov.abierto = false">Cancelar</v-btn>
          <v-btn color="primary" variant="flat" :loading="trabajando" @click="registrarMovimiento">Registrar</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
