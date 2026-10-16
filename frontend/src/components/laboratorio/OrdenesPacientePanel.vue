<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { listLaboratorioPaciente, listOrdenes } from '@/api/laboratorio'
import EstadoChip from '@/components/EstadoChip.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'
import { PRIORIDAD_META } from '@/utils/lab'
import NuevaOrdenDialog from './NuevaOrdenDialog.vue'
import TendenciaDialog from './TendenciaDialog.vue'
import type { PacienteResumen } from '@/types'
import type { OrdenDetalle, OrdenResumen, TipoExamen } from '@/types/etapa'

const props = defineProps<{
  pacienteId: number
  consultaId?: number | null
  tipos?: TipoExamen[]
  titulo?: string
  paciente?: PacienteResumen | null
  sinNuevaOrden?: boolean
}>()
const emit = defineEmits<{ cambio: [] }>()
const auth = useAuthStore()
const ui = useUiStore()

const ordenes = ref<OrdenResumen[]>([])
const loading = ref(false)
const nueva = ref(false)
const tendencia = ref(false)

const conTendencia = computed(() => !props.tipos?.length || props.tipos.includes('LAB'))

async function cargar() {
  loading.value = true
  try {
    const tipos = props.tipos?.length ? props.tipos : [null]
    const paginas = await Promise.all(tipos.map((t) => props.consultaId
      ? listOrdenes({ consultaId: props.consultaId, tipo: t, size: 50 })
      : listLaboratorioPaciente(props.pacienteId, t, 0, 50)))
    const mapa = new Map<number, OrdenResumen>()
    for (const p of paginas) for (const o of p.items) mapa.set(o.id, o)
    ordenes.value = [...mapa.values()].sort((a, b) => b.solicitadaEn.localeCompare(a.solicitadaEn))
  } catch (e) { ui.showError(errorText(e, 'No se pudieron cargar las órdenes')) } finally { loading.value = false }
}

function creada(_o: OrdenDetalle) { void cargar(); emit('cambio') }
watch(() => [props.pacienteId, props.consultaId], () => void cargar())
onMounted(cargar)
defineExpose({ recargar: cargar })
</script>

<template>
  <div>
    <div class="d-flex align-center flex-wrap ga-2 mb-2">
      <h2 class="vet-section-title flex-grow-1 mb-0">{{ titulo ?? 'Órdenes de exámenes' }} <span class="text-caption text-medium-emphasis">({{ ordenes.length }})</span></h2>
      <v-btn v-if="conTendencia && auth.can.laboratorioRead" size="small" variant="tonal" prepend-icon="mdi-chart-line" @click="tendencia = true">Tendencias</v-btn>
      <v-btn v-if="!sinNuevaOrden && auth.can.laboratorioOrdenar" size="small" color="primary" variant="tonal" prepend-icon="mdi-plus" @click="nueva = true">Nueva orden</v-btn>
    </div>
    <v-progress-linear v-if="loading" indeterminate color="primary" />
    <v-card v-for="o in ordenes" :key="o.id" border elevation="0" class="mb-2 vet-evento-tarjeta" :to="{ name: 'orden-lab', params: { id: o.id } }">
      <v-card-text class="d-flex flex-wrap align-center ga-2">
        <strong>{{ o.codigo }}</strong>
        <EstadoChip :estado="o.estado" />
        <v-chip size="small" variant="tonal" :color="PRIORIDAD_META[o.prioridad]?.color" :prepend-icon="PRIORIDAD_META[o.prioridad]?.icon" :text="PRIORIDAD_META[o.prioridad]?.texto ?? o.prioridad" />
        <v-chip v-if="o.hayCriticos" size="small" color="error" variant="flat" prepend-icon="mdi-alert-octagon" text="Críticos" />
        <span class="text-caption text-medium-emphasis">{{ etiquetaInstante(o.solicitadaEn) }}</span>
        <v-spacer />
        <span class="text-caption">{{ o.itemsValidados }}/{{ o.itemsTotal }} validados</span>
        <div class="w-100 d-flex flex-wrap ga-1">
          <v-chip v-for="e in o.examenes" :key="e" size="x-small" variant="outlined" :text="e" />
        </div>
      </v-card-text>
    </v-card>
    <div v-if="!ordenes.length && !loading" class="text-medium-emphasis text-body-2">Sin órdenes.</div>

    <NuevaOrdenDialog v-model="nueva" :paciente-id="pacienteId" :paciente-inicial="paciente" :consulta-id="consultaId" :tipos="tipos" @creada="creada" />
    <TendenciaDialog v-model="tendencia" :paciente-id="pacienteId" />
  </div>
</template>
