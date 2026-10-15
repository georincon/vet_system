<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { listFacturas, listPresupuestos } from '@/api/facturacion'
import EstadoChip from '@/components/EstadoChip.vue'
import { useAuthStore } from '@/stores/auth'
import { errorText } from '@/utils/errors'
import { cop } from '@/utils/dinero'
import { etiquetaFecha, etiquetaInstante } from '@/utils/fecha'
import type { FacturaResumen, Presupuesto } from '@/types/fase4'
import NuevaFacturaDialog from './NuevaFacturaDialog.vue'
import TutorSelect from './TutorSelect.vue'

const props = defineProps<{ vista?: 'presupuestos' | 'facturas' }>()
const emit = defineEmits<{ verPresupuesto: [id: number | null]; verFactura: [id: number]; abrirCaja: [] }>()
const auth = useAuthStore()

const vista = ref<'presupuestos' | 'facturas'>(props.vista ?? (auth.can.facturacionOperar ? 'facturas' : 'presupuestos'))
const presupuestos = ref<Presupuesto[]>([])
const facturas = ref<FacturaResumen[]>([])
const loading = ref(false)
const error = ref('')
const nueva = ref(false)
const fp = reactive({ estado: null as string | null, tutorId: null as number | null, page: 1, pages: 0, total: 0 })
const ff = reactive({ estado: null as string | null, tipo: null as string | null, q: '', desde: '', hasta: '', conSaldo: false, tutorId: null as number | null, page: 1, pages: 0, total: 0 })
let timer: number | undefined

const hP = [
  { title: 'Código', key: 'codigo', sortable: false }, { title: 'Tutor / paciente', key: 'tutor', sortable: false }, { title: 'Origen', key: 'origen', sortable: false },
  { title: 'Total', key: 'total', sortable: false, align: 'end' as const }, { title: 'Estado', key: 'estado', sortable: false },
]
const hF = [
  { title: 'Número', key: 'numero', sortable: false }, { title: 'Fecha', key: 'fecha', sortable: false }, { title: 'Tutor', key: 'tutor', sortable: false },
  { title: 'Condición', key: 'condicionPago', sortable: false }, { title: 'Total', key: 'total', sortable: false, align: 'end' as const },
  { title: 'Saldo', key: 'saldo', sortable: false, align: 'end' as const }, { title: 'Estado', key: 'estado', sortable: false }, { title: 'DIAN', key: 'estadoDian', sortable: false },
]
const ESTADOS_P = ['BORRADOR', 'ENVIADO', 'ACEPTADO', 'RECHAZADO', 'FACTURADO']
const ESTADOS_F = ['EMITIDA', 'PARCIAL', 'PAGADA', 'ANULADA']
const TIPOS_F = [{ title: 'Factura', value: 'FACTURA' }, { title: 'POS', value: 'POS' }, { title: 'Nota crédito', value: 'NOTA_CREDITO' }]

async function cargar() {
  loading.value = true; error.value = ''
  try {
    if (vista.value === 'presupuestos') {
      const r = await listPresupuestos({ estado: fp.estado, tutorId: fp.tutorId, page: fp.page - 1, size: 15 })
      presupuestos.value = r.items; fp.pages = r.totalPages; fp.total = r.totalElements
    } else {
      const r = await listFacturas({ estado: ff.estado, tipo: ff.tipo, q: ff.q.trim(), desde: ff.desde, hasta: ff.hasta, conSaldo: ff.conSaldo || null, tutorId: ff.tutorId, page: ff.page - 1, size: 15 })
      facturas.value = r.items; ff.pages = r.totalPages; ff.total = r.totalElements
    }
  } catch (e) { error.value = errorText(e, 'No se pudo cargar el listado') } finally { loading.value = false }
}
function filtrar() { fp.page = 1; ff.page = 1; void cargar() }
function buscar() { window.clearTimeout(timer); timer = window.setTimeout(filtrar, 350) }
watch(vista, cargar)
defineExpose({ recargar: cargar })
onMounted(cargar)
</script>

<template>
  <div>
    <div class="d-flex flex-wrap ga-2 mb-3 align-center">
      <v-btn-toggle v-if="auth.can.facturacionOperar" v-model="vista" mandatory density="comfortable" color="primary" variant="outlined" divided>
        <v-btn value="facturas" prepend-icon="mdi-receipt-text-outline">Facturas</v-btn>
        <v-btn value="presupuestos" prepend-icon="mdi-file-document-outline">Presupuestos</v-btn>
      </v-btn-toggle>
      <v-spacer />
      <v-btn v-if="auth.can.facturacionOperar && vista === 'presupuestos'" variant="tonal" prepend-icon="mdi-plus" @click="emit('verPresupuesto', null)">Nuevo presupuesto</v-btn>
      <v-btn v-if="auth.can.facturacionOperar && vista === 'facturas'" color="primary" variant="flat" prepend-icon="mdi-plus" @click="nueva = true">Nueva factura</v-btn>
    </div>
    <v-alert v-if="!auth.can.facturacionOperar" type="info" variant="tonal" density="compact" class="mb-3" text="Solo ve los presupuestos de sus propias consultas (lectura)." />
    <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-2" :text="error" />

    <template v-if="vista === 'presupuestos'">
      <div class="d-flex flex-wrap ga-2 mb-2">
        <v-select v-model="fp.estado" :items="ESTADOS_P" label="Estado" clearable hide-details density="comfortable" class="vet-filtro" @update:model-value="filtrar" />
        <div v-if="auth.can.facturacionOperar" class="vet-filtro-ancho"><TutorSelect v-model="fp.tutorId" density="comfortable" hide-details @update:model-value="filtrar" /></div>
      </div>
      <v-data-table :headers="hP" :items="presupuestos" :loading="loading" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin presupuestos" hover
        @click:row="(_: Event, r: { item: Presupuesto }) => emit('verPresupuesto', r.item.id)">
        <template #item.tutor="{ item }">{{ item.tutor.nombre }}<div class="text-caption text-medium-emphasis">{{ item.paciente ?? 'Sin paciente' }}</div></template>
        <template #item.origen="{ item }">{{ item.origen === 'CONSULTA' ? 'Consulta' : 'Manual' }}<div v-if="item.consultaId" class="text-caption text-medium-emphasis">consulta #{{ item.consultaId }}</div></template>
        <template #item.total="{ item }"><span class="vet-monto">{{ cop(item.total) }}</span></template>
        <template #item.estado="{ item }"><EstadoChip :estado="item.estado" /></template>
      </v-data-table>
      <div class="d-flex align-center justify-space-between mt-2">
        <span class="text-caption text-medium-emphasis">{{ fp.total }} presupuesto(s)</span>
        <v-pagination v-if="fp.pages > 1" v-model="fp.page" :length="fp.pages" :total-visible="7" @update:model-value="cargar" />
      </div>
    </template>

    <template v-else>
      <div class="d-flex flex-wrap ga-2 mb-2 align-center">
        <v-text-field v-model="ff.q" label="Número de factura" prepend-inner-icon="mdi-magnify" clearable hide-details density="comfortable" class="vet-filtro" @update:model-value="buscar" />
        <v-select v-model="ff.estado" :items="ESTADOS_F" label="Estado" clearable hide-details density="comfortable" class="vet-filtro" @update:model-value="filtrar" />
        <v-select v-model="ff.tipo" :items="TIPOS_F" label="Tipo" clearable hide-details density="comfortable" class="vet-filtro" @update:model-value="filtrar" />
        <v-text-field v-model="ff.desde" type="date" label="Desde" hide-details density="comfortable" class="vet-filtro" @update:model-value="filtrar" />
        <v-text-field v-model="ff.hasta" type="date" label="Hasta" hide-details density="comfortable" class="vet-filtro" @update:model-value="filtrar" />
        <v-switch v-model="ff.conSaldo" label="Con saldo" color="primary" density="compact" hide-details @update:model-value="filtrar" />
      </div>
      <v-data-table :headers="hF" :items="facturas" :loading="loading" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin facturas para estos filtros" hover
        @click:row="(_: Event, r: { item: FacturaResumen }) => emit('verFactura', r.item.id)">
        <template #item.fecha="{ item }">{{ etiquetaInstante(item.fecha) }}</template>
        <template #item.tutor="{ item }">{{ item.tutor.nombre }}</template>
        <template #item.condicionPago="{ item }">{{ item.condicionPago === 'CREDITO' ? 'Crédito' : 'Contado' }}<div v-if="item.fechaVencimiento" class="text-caption text-medium-emphasis">vence {{ etiquetaFecha(item.fechaVencimiento) }}</div></template>
        <template #item.total="{ item }"><span class="vet-monto">{{ cop(item.total) }}</span></template>
        <template #item.saldo="{ item }"><span class="vet-monto" :class="item.saldo > 0 ? 'text-error' : ''">{{ cop(item.saldo) }}</span></template>
        <template #item.estado="{ item }"><EstadoChip :estado="item.estado" /></template>
        <template #item.estadoDian="{ item }"><EstadoChip :estado="item.estadoDian" /></template>
      </v-data-table>
      <div class="d-flex align-center justify-space-between mt-2">
        <span class="text-caption text-medium-emphasis">{{ ff.total }} documento(s)</span>
        <v-pagination v-if="ff.pages > 1" v-model="ff.page" :length="ff.pages" :total-visible="7" @update:model-value="cargar" />
      </div>
    </template>
    <NuevaFacturaDialog v-model="nueva" @cambio="cargar" @creada="emit('verFactura', $event)" @abrir-caja="emit('abrirCaja')" />
  </div>
</template>
