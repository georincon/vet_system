<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { listOrdenes, registrarMuestra } from '@/api/laboratorio'
import PacienteSelect from '@/components/agenda/PacienteSelect.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import PageHeader from '@/components/PageHeader.vue'
import ImportarDialog from '@/components/laboratorio/ImportarDialog.vue'
import NuevaOrdenDialog from '@/components/laboratorio/NuevaOrdenDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'
import { PRIORIDAD_META } from '@/utils/lab'
import { ESTADOS_ORDEN, PRIORIDADES, TIPOS_EXAMEN, type OrdenDetalle, type OrdenResumen, type TipoExamen } from '@/types/etapa'

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()

const items = ref<OrdenResumen[]>([])
const total = ref(0)
const loading = ref(false)
const filtro = reactive({
  estado: [] as string[], prioridad: null as string | null, tipo: null as TipoExamen | null,
  pacienteId: null as number | null, q: '', desde: '', hasta: '',
})
const page = ref(1)
const size = ref(20)
const nueva = ref(false)
const importar = ref(false)
const muestraId = ref<number | null>(null)

async function cargar() {
  loading.value = true
  try {
    const r = await listOrdenes({
      estado: filtro.estado.length ? filtro.estado.join(',') : undefined, prioridad: filtro.prioridad, tipo: filtro.tipo,
      pacienteId: filtro.pacienteId, q: filtro.q || undefined, desde: filtro.desde || undefined, hasta: filtro.hasta || undefined,
      page: page.value - 1, size: size.value,
    })
    items.value = r.items
    total.value = r.totalElements
  } catch (e) { ui.showError(errorText(e, 'No se pudo cargar la bandeja')) } finally { loading.value = false }
}

let t: number | undefined
watch(() => filtro.q, () => { window.clearTimeout(t); t = window.setTimeout(() => { page.value = 1; void cargar() }, 300) })
watch(() => [filtro.estado, filtro.prioridad, filtro.tipo, filtro.pacienteId, filtro.desde, filtro.hasta, size.value], () => { page.value = 1; void cargar() })
watch(page, cargar)
onMounted(cargar)

const headers = [
  { title: 'Orden', key: 'codigo' }, { title: 'Paciente', key: 'pacienteNombre', sortable: false }, { title: 'Exámenes', key: 'examenes', sortable: false },
  { title: 'Prioridad', key: 'prioridad', sortable: false }, { title: 'Estado', key: 'estado', sortable: false },
  { title: 'Avance', key: 'avance', sortable: false }, { title: 'Solicitada', key: 'solicitadaEn', sortable: false },
  { title: '', key: 'acciones', sortable: false, align: 'end' as const },
]

async function tomarMuestra(o: OrdenResumen) {
  muestraId.value = o.id
  try {
    await registrarMuestra(o.id)
    ui.showSuccess(`Muestra registrada en ${o.codigo}`)
    await cargar()
  } catch (e) { ui.showError(errorText(e, 'No se pudo registrar la muestra')) } finally { muestraId.value = null }
}
function creada(o: OrdenDetalle) { void router.push({ name: 'orden-lab', params: { id: o.id } }) }
const pendientes = computed(() => items.value.filter((o) => o.estado === 'SOLICITADA').length)
function abrir(_: Event, r: { item: OrdenResumen }) { void router.push({ name: 'orden-lab', params: { id: r.item.id } }) }
</script>

<template>
  <div>
    <PageHeader title="Bandeja de laboratorio" subtitle="Órdenes de laboratorio, imágenes y patología">
      <v-btn variant="flat" prepend-icon="mdi-book-open-variant" :to="{ name: 'laboratorio-catalogo' }">Catálogo</v-btn>
      <v-btn v-if="auth.can.laboratorioCapturar" variant="flat" prepend-icon="mdi-upload" @click="importar = true">Importar resultados</v-btn>
      <v-btn v-if="auth.can.laboratorioOrdenar" variant="flat" prepend-icon="mdi-plus" @click="nueva = true">Nueva orden</v-btn>
    </PageHeader>

    <v-card border elevation="0" class="mb-3">
      <v-card-text class="d-flex flex-wrap ga-3 align-center">
        <v-select v-model="filtro.estado" :items="ESTADOS_ORDEN" item-title="title" item-value="value" label="Estado" multiple chips closable-chips clearable class="vet-filtro vet-filtro-ancho" />
        <v-select v-model="filtro.prioridad" :items="PRIORIDADES" item-title="title" item-value="value" label="Prioridad" clearable class="vet-filtro" />
        <v-select v-model="filtro.tipo" :items="TIPOS_EXAMEN" item-title="title" item-value="value" label="Tipo" clearable class="vet-filtro" />
        <div class="vet-filtro vet-filtro-ancho"><PacienteSelect v-model="filtro.pacienteId" /></div>
        <v-text-field v-model="filtro.q" label="Buscar (código, paciente)" prepend-inner-icon="mdi-magnify" clearable class="vet-filtro vet-filtro-ancho" />
        <v-text-field v-model="filtro.desde" label="Desde" type="date" class="vet-filtro" />
        <v-text-field v-model="filtro.hasta" label="Hasta" type="date" class="vet-filtro" />
      </v-card-text>
    </v-card>

    <p class="text-caption text-medium-emphasis mb-1" aria-live="polite">{{ total }} orden(es) · {{ pendientes }} sin muestra en esta página</p>
    <v-data-table-server v-model:items-per-page="size" :headers="headers" :items="items" :items-length="total" :loading="loading"
      :page="page" density="comfortable" hover item-value="id" :items-per-page-options="[10, 20, 50]" @update:page="page = $event" @click:row="abrir">
      <template #item.codigo="{ item }">
        <router-link :to="{ name: 'orden-lab', params: { id: item.id } }" @click.stop>{{ item.codigo }}</router-link>
        <v-chip v-if="item.hayCriticos" size="x-small" color="error" variant="flat" class="ml-1" prepend-icon="mdi-alert-octagon" text="Crítico" />
      </template>
      <template #item.pacienteNombre="{ item }">{{ item.pacienteNombre }}<div class="text-caption text-medium-emphasis">{{ item.pacienteCodigo }} · {{ item.especie }}</div></template>
      <template #item.examenes="{ item }"><div class="d-flex flex-wrap ga-1"><v-chip v-for="e in item.examenes" :key="e" size="x-small" variant="outlined" :text="e" /></div></template>
      <template #item.prioridad="{ item }">
        <v-chip size="small" variant="tonal" :color="PRIORIDAD_META[item.prioridad]?.color" :prepend-icon="PRIORIDAD_META[item.prioridad]?.icon" :text="PRIORIDAD_META[item.prioridad]?.texto ?? item.prioridad" />
      </template>
      <template #item.estado="{ item }"><EstadoChip :estado="item.estado" /></template>
      <template #item.avance="{ item }">{{ item.itemsValidados }}/{{ item.itemsTotal }}</template>
      <template #item.solicitadaEn="{ item }">{{ etiquetaInstante(item.solicitadaEn) }}</template>
      <template #item.acciones="{ item }">
        <v-btn v-if="item.estado === 'SOLICITADA' && auth.can.laboratorioCapturar" size="small" variant="tonal" prepend-icon="mdi-test-tube"
          :loading="muestraId === item.id" @click.stop="tomarMuestra(item)">Tomar muestra</v-btn>
      </template>
      <template #no-data><div class="py-6 text-medium-emphasis">No hay órdenes con estos filtros.</div></template>
    </v-data-table-server>

    <NuevaOrdenDialog v-model="nueva" @creada="creada" />
    <ImportarDialog v-model="importar" @importado="cargar" />
  </div>
</template>
