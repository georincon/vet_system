<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { listEspecies } from '@/api/catalogos'
import { listPendientes } from '@/api/inmunizacion'
import { getPaciente } from '@/api/pacientes'
import PageHeader from '@/components/PageHeader.vue'
import AplicarInmunizacionDialog from '@/components/vacunacion/AplicarInmunizacionDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaFecha } from '@/utils/fecha'
import type { Especie, Paciente } from '@/types'
import type { Pendiente } from '@/types/fase3'

const auth = useAuthStore()
const ui = useUiStore()

const items = ref<Pendiente[]>([])
const especies = ref<Especie[]>([])
const loading = ref(false)
const error = ref('')
const f = reactive({
  desde: '', hasta: '', dias: 30,
  especieId: null as number | null,
  tipo: null as 'VACUNA' | 'ANTIPARASITARIO' | null,
  estado: null as 'VENCIDA' | 'POR_VENCER' | null,
  page: 1, size: 20, total: 0, pages: 0,
})

const TIPOS = [{ title: 'Todos', value: null }, { title: 'Vacunas', value: 'VACUNA' }, { title: 'Desparasitantes', value: 'ANTIPARASITARIO' }]
const ESTADOS = [{ title: 'Vencidas y por vencer', value: null }, { title: 'Solo vencidas', value: 'VENCIDA' }, { title: 'Solo por vencer', value: 'POR_VENCER' }]

const verPaciente = computed(() => auth.can.clinicaRead)
const puedeAplicar = computed(() => auth.can.inmunizacionAplicar)
const headers = computed(() => [
  { title: 'Urgencia', key: 'estado', sortable: false },
  { title: 'Paciente', key: 'paciente', sortable: false },
  { title: 'Producto', key: 'producto', sortable: false },
  { title: 'Próxima fecha', key: 'proximaFecha', sortable: false },
  { title: 'Tutor / contacto', key: 'tutor', sortable: false },
  { title: 'Acciones', key: 'acciones', sortable: false, align: 'end' as const },
])

async function cargar() {
  loading.value = true
  error.value = ''
  try {
    const r = await listPendientes({
      desde: f.desde, hasta: f.hasta, dias: f.dias, especieId: f.especieId, tipo: f.tipo, estado: f.estado,
      page: f.page - 1, size: f.size,
    })
    items.value = r.items
    f.total = r.totalElements
    f.pages = r.totalPages
  } catch (e) {
    error.value = errorText(e, 'No se pudieron cargar los pendientes')
  } finally { loading.value = false }
}
function buscar() { f.page = 1; void cargar() }
function limpiar() {
  Object.assign(f, { desde: '', hasta: '', dias: 30, especieId: null, tipo: null, estado: null })
  buscar()
}

function urgencia(p: Pendiente): { color: string; text: string; icon: string } {
  if (p.estado === 'VENCIDA' || p.diasRestantes < 0) return { color: 'error', text: `Vencida hace ${Math.abs(p.diasRestantes)} d`, icon: 'mdi-alert-circle-outline' }
  if (p.diasRestantes === 0) return { color: 'warning', text: 'Vence hoy', icon: 'mdi-clock-alert-outline' }
  return { color: p.diasRestantes <= 7 ? 'warning' : 'info', text: `En ${p.diasRestantes} d`, icon: 'mdi-calendar-clock' }
}

// Aplicar desde la fila
const dlg = ref(false)
const dlgPaciente = ref<Paciente | null>(null)
const dlgInicial = ref<{ tipo: 'VACUNA' | 'ANTIPARASITARIO'; productoId: number } | null>(null)
async function aplicar(p: Pendiente) {
  try {
    dlgPaciente.value = await getPaciente(p.pacienteId)
    dlgInicial.value = { tipo: p.tipo, productoId: p.productoId }
    dlg.value = true
  } catch (e) { ui.showError(errorText(e, 'No se pudo abrir el paciente')) }
}

onMounted(async () => {
  void cargar()
  try { especies.value = await listEspecies() } catch { /* filtro opcional */ }
})
</script>

<template>
  <div>
    <PageHeader title="Pendientes de vacunar" subtitle="Vacunas y desparasitaciones vencidas o próximas a vencer">
      <v-btn v-if="auth.can.comunicacionesRead" variant="flat" prepend-icon="mdi-email-fast-outline" :to="{ name: 'comunicaciones' }">Recordatorios</v-btn>
      <v-btn variant="flat" prepend-icon="mdi-refresh" :loading="loading" @click="cargar">Actualizar</v-btn>
    </PageHeader>

    <v-card border elevation="0" class="mb-4">
      <v-card-text>
        <v-row dense>
          <v-col cols="6" md="2"><v-text-field v-model="f.desde" type="date" label="Desde" density="comfortable" hide-details clearable /></v-col>
          <v-col cols="6" md="2"><v-text-field v-model="f.hasta" type="date" label="Hasta" density="comfortable" hide-details clearable /></v-col>
          <v-col cols="6" md="2"><v-text-field v-model.number="f.dias" type="number" min="1" label="Por vencer en (días)" density="comfortable" hide-details :disabled="!!f.hasta" /></v-col>
          <v-col cols="6" md="2"><v-select v-model="f.especieId" :items="especies" item-title="nombre" item-value="id" label="Especie" density="comfortable" hide-details clearable /></v-col>
          <v-col cols="6" md="2"><v-select v-model="f.tipo" :items="TIPOS" label="Tipo" density="comfortable" hide-details /></v-col>
          <v-col cols="6" md="2"><v-select v-model="f.estado" :items="ESTADOS" label="Estado" density="comfortable" hide-details /></v-col>
        </v-row>
        <div class="d-flex ga-2 mt-3">
          <v-btn color="primary" variant="flat" prepend-icon="mdi-magnify" @click="buscar">Buscar</v-btn>
          <v-btn variant="text" @click="limpiar">Limpiar</v-btn>
          <v-spacer />
          <span class="text-body-2 text-medium-emphasis align-self-center">{{ f.total }} pendiente(s)</span>
        </div>
      </v-card-text>
    </v-card>

    <v-alert v-if="error" type="error" variant="tonal" class="mb-3" :text="error" />

    <v-card border elevation="0">
      <v-data-table :headers="headers" :items="items" :loading="loading" :items-per-page="-1" hide-default-footer density="comfortable"
        no-data-text="No hay pendientes con estos filtros">
        <template #item.estado="{ item }">
          <v-chip size="small" variant="tonal" :color="urgencia(item).color" :prepend-icon="urgencia(item).icon" :text="urgencia(item).text" />
        </template>
        <template #item.paciente="{ item }">
          <div class="font-weight-medium">{{ item.paciente }}</div>
          <div class="text-caption text-medium-emphasis">{{ item.especie }}</div>
        </template>
        <template #item.producto="{ item }">
          {{ item.producto }}
          <v-chip size="x-small" class="ml-1" variant="tonal" :color="item.tipo === 'VACUNA' ? 'info' : 'secondary'" :text="item.tipo === 'VACUNA' ? 'Vacuna' : 'Antiparasitario'" />
          <div class="text-caption text-medium-emphasis">Última: {{ etiquetaFecha(item.ultimaAplicacion) }}</div>
        </template>
        <template #item.proximaFecha="{ item }">{{ etiquetaFecha(item.proximaFecha) }}</template>
        <template #item.tutor="{ item }">
          <template v-if="item.tutor">
            <div class="font-weight-medium">{{ item.tutor.nombre }}</div>
            <div class="text-caption"><v-icon icon="mdi-phone-outline" size="x-small" /> {{ item.tutor.telefono || 'sin teléfono' }}</div>
            <div class="text-caption"><v-icon icon="mdi-email-outline" size="x-small" /> {{ item.tutor.email || 'sin correo' }}</div>
            <v-chip v-if="!item.tutor.aceptaEmail" size="x-small" color="warning" variant="tonal" prepend-icon="mdi-email-off-outline" class="mt-1"
              text="Sin consentimiento de correo: no se enviará recordatorio" />
          </template>
          <span v-else class="text-medium-emphasis">Sin tutor principal</span>
        </template>
        <template #item.acciones="{ item }">
          <v-btn v-if="verPaciente" size="small" variant="text" icon="mdi-timeline-text-outline" title="Ver paciente"
            :to="{ name: 'historia', params: { id: item.pacienteId }, query: { tab: 'vacunacion' } }" />
          <v-btn v-if="puedeAplicar" size="small" variant="tonal" color="primary" prepend-icon="mdi-needle" @click="aplicar(item)">Aplicar</v-btn>
          <v-btn v-if="item.tutor && auth.can.comunicacionesRead" size="small" variant="text" icon="mdi-bell-outline" title="Ver recordatorios"
            :to="{ name: 'comunicaciones' }" />
        </template>
      </v-data-table>
      <div v-if="f.pages > 1" class="pa-3 d-flex justify-center">
        <v-pagination v-model="f.page" :length="f.pages" :total-visible="7" @update:model-value="cargar" />
      </div>
    </v-card>

    <AplicarInmunizacionDialog v-if="dlgPaciente" v-model="dlg" :paciente-id="dlgPaciente.id" :paciente-nombre="dlgPaciente.nombre"
      :especie-id="dlgPaciente.especie.id" :fecha-nacimiento="dlgPaciente.fechaNacimiento" :inicial="dlgInicial" @aplicada="cargar" />
  </div>
</template>
