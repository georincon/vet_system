<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { abonar, carteraTutor, listCartera, reporteCartera } from '@/api/facturacion'
import { useCajaStore } from '@/stores/caja'
import { useUiStore } from '@/stores/ui'
import EstadoChip from '@/components/EstadoChip.vue'
import { errorText } from '@/utils/errors'
import { cop } from '@/utils/dinero'
import { etiquetaFecha } from '@/utils/fecha'
import type { CarteraDetalle, CarteraTutor, PagoInput, ReporteCartera } from '@/types/fase4'
import PagosEditor from './PagosEditor.vue'
import SinTurnoAviso from './SinTurnoAviso.vue'

const emit = defineEmits<{ verFactura: [id: number]; abrirCaja: [] }>()
const caja = useCajaStore()
const ui = useUiStore()
const items = ref<CarteraTutor[]>([])
const rep = ref<ReporteCartera | null>(null)
const loading = ref(false)
const error = ref('')
const f = reactive({ soloVencidas: false, page: 1, pages: 0, total: 0 })
const det = reactive({ abierto: false, data: null as CarteraDetalle | null, tutorId: 0, pagos: [] as PagoInput[], error: '', trabajando: false })

const headers = [
  { title: 'Tutor', key: 'tutor', sortable: false }, { title: 'Facturas', key: 'facturas', sortable: false, align: 'end' as const },
  { title: 'Saldo', key: 'saldo', sortable: false, align: 'end' as const }, { title: 'Vencido', key: 'saldoVencido', sortable: false, align: 'end' as const },
  { title: 'Mora máx.', key: 'diasMoraMax', sortable: false, align: 'end' as const }, { title: '', key: 'acciones', sortable: false, align: 'end' as const },
]
const EDADES: [keyof ReporteCartera['edades'], string][] = [['vigente', 'Vigente'], ['dias1a30', '1-30 d'], ['dias31a60', '31-60 d'], ['dias61a90', '61-90 d'], ['masDe90', '> 90 d']]

async function cargar() {
  loading.value = true; error.value = ''
  try {
    const [r, rc] = await Promise.all([listCartera(f.soloVencidas, f.page - 1, 15), reporteCartera().catch(() => null)])
    items.value = r.items; f.pages = r.totalPages; f.total = r.totalElements; rep.value = rc
  } catch (e) { error.value = errorText(e, 'No se pudo cargar la cartera') } finally { loading.value = false }
}
async function abrir(t: CarteraTutor) {
  det.tutorId = t.tutorId; det.error = ''; det.pagos = []; det.data = null; det.abierto = true
  void caja.refrescar()
  try { det.data = await carteraTutor(t.tutorId); det.pagos = [{ metodo: 'EFECTIVO', monto: det.data.saldo, referencia: '' }] } catch (e) { det.error = errorText(e, 'No se pudo cargar el detalle') }
}
async function registrar() {
  det.error = ''
  const validos = det.pagos.filter((p) => p.monto > 0)
  if (!validos.length) { det.error = 'Indique al menos un pago'; return }
  if (!caja.abierto) { det.error = 'Abra su turno de caja para registrar abonos'; return }
  det.trabajando = true
  try {
    const r = await abonar(det.tutorId, validos.map((p) => ({ metodo: p.metodo, monto: Number(p.monto), referencia: p.referencia?.trim() || undefined })))
    ui.showSuccess(`Abono de ${cop(r.total)} aplicado. Saldo del tutor: ${cop(r.saldoTutor)}`)
    det.abierto = false
    void caja.refrescar()
    await cargar()
  } catch (e) { det.error = errorText(e, 'No se pudo registrar el abono') } finally { det.trabajando = false }
}
defineExpose({ recargar: cargar })
onMounted(cargar)
</script>

<template>
  <div>
    <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-2" :text="error" />
    <template v-if="rep">
      <h3 class="vet-section-title">Antigüedad de la cartera · total {{ cop(rep.total) }} ({{ rep.facturas }} facturas)</h3>
      <v-row class="mb-2">
        <v-col v-for="[k, label] in EDADES" :key="k" cols="6" sm="4" md="2"><div class="vet-info-bar"><div class="text-caption">{{ label }}</div><div class="text-subtitle-1 font-weight-bold vet-monto">{{ cop(rep.edades[k]) }}</div></div></v-col>
      </v-row>
    </template>
    <div class="d-flex ga-2 mb-2 align-center">
      <h3 class="vet-section-title flex-grow-1 mb-0">Cartera por tutor</h3>
      <v-switch v-model="f.soloVencidas" label="Solo vencidas" color="primary" density="compact" hide-details @update:model-value="f.page = 1; cargar()" />
    </div>
    <v-data-table :headers="headers" :items="items" :loading="loading" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin cartera pendiente">
      <template #item.tutor="{ item }">{{ item.tutor }}<div class="text-caption text-medium-emphasis">{{ item.documento }}</div></template>
      <template #item.saldo="{ item }"><strong class="vet-monto">{{ cop(item.saldo) }}</strong></template>
      <template #item.saldoVencido="{ item }"><span class="vet-monto" :class="item.saldoVencido > 0 ? 'text-error' : ''">{{ cop(item.saldoVencido) }}</span></template>
      <template #item.diasMoraMax="{ item }">{{ item.diasMoraMax ? item.diasMoraMax + ' d' : '—' }}</template>
      <template #item.acciones="{ item }"><v-btn size="small" variant="tonal" color="primary" prepend-icon="mdi-cash-plus" @click="abrir(item)">Abonar</v-btn></template>
    </v-data-table>
    <div class="d-flex align-center justify-space-between mt-2">
      <span class="text-caption text-medium-emphasis">{{ f.total }} tutor(es) con saldo</span>
      <v-pagination v-if="f.pages > 1" v-model="f.page" :length="f.pages" :total-visible="7" @update:model-value="cargar" />
    </div>

    <v-dialog v-model="det.abierto" max-width="860" scrollable>
      <v-card>
        <v-card-title class="pt-4">Cartera · {{ det.data?.tutor.nombre ?? [det.data?.tutor.nombres, det.data?.tutor.apellidos].filter(Boolean).join(' ') }}</v-card-title>
        <v-card-text>
          <v-alert v-if="det.error" type="error" variant="tonal" density="compact" class="mb-3" :text="det.error" />
          <SinTurnoAviso v-if="caja.cargado && !caja.abierto" texto="Los abonos son pagos y exigen un turno de caja abierto." @abrir="det.abierto = false; emit('abrirCaja')" />
          <template v-if="det.data">
            <div class="vet-info-bar mb-3 vet-monto">Saldo {{ cop(det.data.saldo) }} · vencido {{ cop(det.data.saldoVencido) }}</div>
            <v-table density="comfortable">
              <thead><tr><th>Factura</th><th>Estado</th><th>Vence</th><th>Mora</th><th class="text-end">Saldo</th></tr></thead>
              <tbody>
                <tr v-for="x in det.data.facturas" :key="x.factura.id">
                  <td><a href="#" @click.prevent="emit('verFactura', x.factura.id)">{{ x.factura.numero }}</a></td>
                  <td><EstadoChip :estado="x.factura.estado" /></td>
                  <td>{{ etiquetaFecha(x.vencimiento) }}</td>
                  <td>{{ x.diasMora ? x.diasMora + ' d' : '—' }}</td>
                  <td class="text-end vet-monto">{{ cop(x.factura.saldo) }}</td>
                </tr>
              </tbody>
            </v-table>
            <p class="text-caption text-medium-emphasis mt-2">El abono se aplica a las facturas más antiguas primero.</p>
            <div class="mt-3"><PagosEditor v-model="det.pagos" :total="det.data.saldo" parcial titulo="Abono" /></div>
          </template>
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer />
          <v-btn variant="text" @click="det.abierto = false">Cerrar</v-btn>
          <v-btn color="primary" variant="flat" :loading="det.trabajando" :disabled="!det.data || !caja.abierto" @click="registrar">Registrar abono</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
