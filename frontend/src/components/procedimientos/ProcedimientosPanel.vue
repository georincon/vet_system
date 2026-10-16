<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { listProcedimientos, listProcedimientosPaciente } from '@/api/procedimientos'
import EstadoChip from '@/components/EstadoChip.vue'
import ProcedimientoDialog from '@/components/procedimientos/ProcedimientoDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import type { PacienteResumen } from '@/types'
import type { Procedimiento, ProcedimientoResumen, TipoProcedimiento } from '@/types/etapa'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'

const props = defineProps<{ pacienteId: number; consultaId?: number | null; paciente?: PacienteResumen | null }>()
const emit = defineEmits<{ cambio: [] }>()
const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()

const items = ref<ProcedimientoResumen[]>([])
const loading = ref(false)
const dialog = ref(false)
const tipoNuevo = ref<TipoProcedimiento>('PROCEDIMIENTO')

async function cargar() {
  loading.value = true
  try {
    items.value = props.consultaId
      ? (await listProcedimientos({ consultaId: props.consultaId, size: 50 })).items
      : await listProcedimientosPaciente(props.pacienteId)
  } catch (e) {
    ui.showError(errorText(e, 'No se pudieron cargar los procedimientos'))
  } finally {
    loading.value = false
  }
}

function nuevo(t: TipoProcedimiento) { tipoNuevo.value = t; dialog.value = true }
function guardado(p: Procedimiento) {
  ui.showSuccess(`${p.tipo === 'CIRUGIA' ? 'Cirugía' : 'Procedimiento'} ${p.codigo} registrado`)
  emit('cambio')
  void router.push({ name: 'procedimiento', params: { id: p.id } })
}

function consentimiento(p: ProcedimientoResumen): { texto: string; color: string; icon: string } | null {
  if (p.consentimientoEstado === 'FIRMADO') return { texto: 'Consentimiento firmado', color: 'success', icon: 'mdi-draw-pen' }
  if (p.consentimientoEstado === 'RECHAZADO') return { texto: 'Consentimiento rechazado', color: 'error', icon: 'mdi-close-circle-outline' }
  if (p.tipo === 'CIRUGIA') return { texto: 'Sin consentimiento', color: p.urgencia ? 'warning' : 'error', icon: 'mdi-alert-outline' }
  return null
}

watch(() => [props.pacienteId, props.consultaId], cargar)
onMounted(cargar)
defineExpose({ cargar })
</script>

<template>
  <div>
    <div class="d-flex flex-wrap align-center ga-2 mb-2">
      <h2 class="vet-section-title flex-grow-1 mb-0">Procedimientos y cirugías <span class="text-caption text-medium-emphasis">({{ items.length }})</span></h2>
      <template v-if="auth.can.procedimientosWrite">
        <v-btn size="small" variant="tonal" color="primary" prepend-icon="mdi-bandage" @click="nuevo('PROCEDIMIENTO')">Nuevo procedimiento</v-btn>
        <v-btn size="small" variant="tonal" color="primary" prepend-icon="mdi-content-cut" @click="nuevo('CIRUGIA')">Nueva cirugía</v-btn>
      </template>
    </div>
    <v-progress-linear v-if="loading" indeterminate color="primary" />
    <div class="vet-tabla-resultados">
      <v-table v-if="items.length" density="compact">
        <thead><tr><th>Código</th><th>Procedimiento</th><th>Tipo</th><th>Estado</th><th>Consentimiento</th><th>Fecha</th><th>Cirujano</th></tr></thead>
        <tbody>
          <tr v-for="p in items" :key="p.id">
            <td><router-link :to="{ name: 'procedimiento', params: { id: p.id } }">{{ p.codigo }}</router-link></td>
            <td>{{ p.procedimiento }}</td>
            <td>
              <v-chip size="small" variant="tonal" :color="p.tipo === 'CIRUGIA' ? 'secondary' : 'primary'"
                :text="p.tipo === 'CIRUGIA' ? 'Cirugía' : 'Procedimiento'" />
              <v-chip v-if="p.urgencia" size="small" color="error" variant="tonal" prepend-icon="mdi-flash-alert" text="Urgencia" class="ml-1" />
            </td>
            <td><EstadoChip :estado="p.estado" /></td>
            <td>
              <v-chip v-if="consentimiento(p)" size="small" variant="tonal" :color="consentimiento(p)!.color"
                :prepend-icon="consentimiento(p)!.icon" :text="consentimiento(p)!.texto" />
              <span v-else class="text-medium-emphasis">—</span>
            </td>
            <td>{{ etiquetaInstante(p.inicioEn ?? p.programadaPara) }}</td>
            <td>{{ p.cirujano?.nombre ?? '—' }}</td>
          </tr>
        </tbody>
      </v-table>
    </div>
    <div v-if="!items.length && !loading" class="text-medium-emphasis text-body-2">Sin procedimientos ni cirugías.</div>

    <ProcedimientoDialog v-model="dialog" :paciente-id="pacienteId" :paciente-inicial="paciente" :consulta-id="consultaId"
      :tipo="tipoNuevo" @guardado="guardado" />
  </div>
</template>
