<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createAlergia, createPeso, deleteAlergia, getHistoria, getPaciente, listAlergias, listPesos } from '@/api/pacientes'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import PageHeader from '@/components/PageHeader.vue'
import VacunacionPanel from '@/components/vacunacion/VacunacionPanel.vue'
import ArchivosPanel from '@/components/archivos/ArchivosPanel.vue'
import EnviarTutorButton from '@/components/compartir/EnviarTutorButton.vue'
import HistorialEnviosPanel from '@/components/compartir/HistorialEnviosPanel.vue'
import CobrosPanel from '@/components/historia/CobrosPanel.vue'
import FormulasPanel from '@/components/historia/FormulasPanel.vue'
import OrdenesPacientePanel from '@/components/laboratorio/OrdenesPacientePanel.vue'
import ProcedimientosPanel from '@/components/procedimientos/ProcedimientosPanel.vue'
import { listControles } from '@/api/formula'
import { listConsultas } from '@/api/consultas'
import { listLaboratorioPaciente } from '@/api/laboratorio'
import { usePuedeCompartir } from '@/utils/compartir'
import type { ControlSeguimiento, OrdenResumen } from '@/types/etapa'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaFecha, hoy } from '@/utils/fecha'
import type { Alergia, HistoriaItem, Paciente, PesoRegistro, SeveridadAlergia, TipoAlergia, TipoHistoria } from '@/types'

const route = useRoute()
const router = useRouter()
type Tab = 'historia' | 'consultas' | 'laboratorio' | 'imagenes' | 'patologia' | 'procedimientos' | 'archivos' | 'vacunacion' | 'formulas' | 'envios' | 'cobros'
const TABS_VALIDAS: Tab[] = ['historia', 'consultas', 'laboratorio', 'imagenes', 'patologia', 'procedimientos', 'archivos', 'vacunacion', 'formulas', 'envios', 'cobros']
const tab = ref<Tab>(TABS_VALIDAS.includes(route.query.tab as Tab) ? (route.query.tab as Tab) : 'historia')
watch(tab, (t) => { void router.replace({ query: t === 'historia' ? {} : { tab: t } }) })
const auth = useAuthStore()
const { puede: puedeCompartir } = usePuedeCompartir()
const ui = useUiStore()
const id = computed(() => Number(route.params.id))

const paciente = ref<Paciente | null>(null)
const alergias = ref<Alergia[]>([])
const pesos = ref<PesoRegistro[]>([])
const items = ref<HistoriaItem[]>([])
const tipo = ref<TipoHistoria | null>(null)
const page = ref(1)
const totalPages = ref(0)
const total = ref(0)
const loading = ref(false)

const META: Record<TipoHistoria, { icon: string; color: string; label: string }> = {
  CONSULTA: { icon: 'mdi-stethoscope', color: 'primary', label: 'Consulta' },
  VACUNA: { icon: 'mdi-needle', color: 'info', label: 'Vacuna' },
  DESPARASITACION: { icon: 'mdi-bug-outline', color: 'secondary', label: 'Desparasitación' },
  PESO: { icon: 'mdi-scale-bathroom', color: 'secondary', label: 'Peso' },
  ALERGIA: { icon: 'mdi-alert-circle-outline', color: 'error', label: 'Alergia' },
  LABORATORIO: { icon: 'mdi-flask-outline', color: 'secondary', label: 'Laboratorio' },
  IMAGEN: { icon: 'mdi-image-search-outline', color: 'info', label: 'Imagen' },
  PATOLOGIA: { icon: 'mdi-microscope', color: 'secondary', label: 'Patología' },
  PROCEDIMIENTO: { icon: 'mdi-bandage', color: 'primary', label: 'Procedimiento' },
  CIRUGIA: { icon: 'mdi-content-cut', color: 'error', label: 'Cirugía' },
  ARCHIVO: { icon: 'mdi-paperclip', color: 'grey', label: 'Archivo' },
}
const TIPOS = [{ title: 'Todo', value: null }, ...(Object.keys(META) as TipoHistoria[]).map((k) => ({ title: META[k].label + (k === 'DESPARASITACION' ? 'es' : 's'), value: k }))]

/** Pestañas que son una vista filtrada de la línea de tiempo del backend. */
const TIPO_POR_TAB: Partial<Record<Tab, string>> = { consultas: 'CONSULTA' }
const tabLinea = computed(() => tab.value === 'historia' || tab.value === 'consultas')
function rutaEvento(it: HistoriaItem) {
  if (it.tipo === 'CONSULTA') return { name: 'consulta', params: { id: it.referenciaId } }
  if (it.tipo === 'LABORATORIO' || it.tipo === 'IMAGEN' || it.tipo === 'PATOLOGIA') return { name: 'orden-lab', params: { id: it.referenciaId } }
  if (it.tipo === 'PROCEDIMIENTO' || it.tipo === 'CIRUGIA') return { name: 'procedimiento', params: { id: it.referenciaId } }
  if (it.tipo === 'ARCHIVO') return { name: 'historia', params: { id: id.value }, query: { tab: 'archivos' } }
  return null
}

// Resumen superior: últimos críticos y próximos controles
const criticos = ref<OrdenResumen[]>([])
const controles = ref<ControlSeguimiento[]>([])
async function loadResumenClinico() {
  criticos.value = []; controles.value = []
  try {
    const r = await listLaboratorioPaciente(id.value, null, 0, 20)
    criticos.value = r.items.filter((o) => o.hayCriticos).slice(0, 3)
  } catch { /* sin acceso */ }
  try {
    const cs = (await listConsultas(undefined, id.value, 0, 5)).items
    const todos = (await Promise.all(cs.map((c) => listControles(c.id).catch(() => [] as ControlSeguimiento[])))).flat()
    controles.value = todos.filter((c) => (c.estado === 'PENDIENTE' || c.estado === 'AGENDADO') && c.fechaSugerida >= hoy())
      .sort((a, b) => a.fechaSugerida.localeCompare(b.fechaSugerida)).slice(0, 3)
  } catch { /* sin acceso */ }
}

const activas = computed(() => alergias.value.filter((a) => a.activo))
const ultimoPeso = computed(() => pesos.value[0] ?? null)
const puedePeso = computed(() => auth.can.signos)
const puedeAlergia = computed(() => auth.can.clinicaWrite)

async function loadResumen() {
  try {
    const [p, a, w] = await Promise.all([getPaciente(id.value), listAlergias(id.value), listPesos(id.value)])
    paciente.value = p; alergias.value = a; pesos.value = w
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo cargar el paciente'))
  }
}

async function loadHistoria() {
  loading.value = true
  try {
    const r = await getHistoria(id.value, (tab.value === 'historia' ? tipo.value : TIPO_POR_TAB[tab.value]) ?? undefined, page.value - 1, 10)
    items.value = r.items
    totalPages.value = r.totalPages
    total.value = r.totalElements
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo cargar la historia'))
  } finally {
    loading.value = false
  }
}

// Registrar peso
const pesoDialog = ref(false)
const pesoForm = reactive({ pesoKg: null as number | null, fecha: hoy() })
const guardando = ref(false)
const errorForm = ref('')
async function guardarPeso() {
  if (!pesoForm.pesoKg || pesoForm.pesoKg <= 0) { errorForm.value = 'Ingrese un peso válido'; return }
  guardando.value = true
  try {
    await createPeso(id.value, Number(pesoForm.pesoKg), pesoForm.fecha)
    pesoDialog.value = false
    ui.showSuccess('Peso registrado')
    await Promise.all([loadResumen(), loadHistoria()])
  } catch (e) { errorForm.value = errorText(e, 'No se pudo registrar el peso') } finally { guardando.value = false }
}

// Alergias
const alergiaDialog = ref(false)
const alergiaForm = reactive({ descripcion: '', severidad: 'MEDIA' as SeveridadAlergia, tipo: 'MEDICAMENTO' as TipoAlergia, reaccion: '' })
const quitar = ref<Alergia | null>(null)
async function guardarAlergia() {
  if (!alergiaForm.descripcion.trim()) { errorForm.value = 'Ingrese la descripción'; return }
  guardando.value = true
  try {
    await createAlergia(id.value, { ...alergiaForm, descripcion: alergiaForm.descripcion.trim(), reaccion: alergiaForm.reaccion.trim() || undefined })
    alergiaDialog.value = false
    ui.showSuccess('Alergia registrada')
    await Promise.all([loadResumen(), loadHistoria()])
  } catch (e) { errorForm.value = errorText(e, 'No se pudo registrar la alergia') } finally { guardando.value = false }
}
async function eliminarAlergia() {
  if (!quitar.value) return
  try {
    await deleteAlergia(id.value, quitar.value.id)
    quitar.value = null
    await Promise.all([loadResumen(), loadHistoria()])
  } catch (e) { ui.showError(errorText(e, 'No se pudo eliminar la alergia')) }
}

watch([tipo, tab], () => { if (tabLinea.value) { page.value = 1; void loadHistoria() } })
watch(id, () => { void loadResumen(); void loadHistoria(); void loadResumenClinico() })
onMounted(() => { void loadResumen(); void loadHistoria(); void loadResumenClinico() })
</script>

<template>
  <div>
    <PageHeader :title="paciente ? `Historia de ${paciente.nombre}` : 'Historia del paciente'"
      :subtitle="paciente ? `${paciente.codigo} · ${paciente.especie.nombre}${paciente.raza ? ' · ' + paciente.raza.nombre : ''}` : ''">
      <v-btn variant="flat" prepend-icon="mdi-arrow-left" :to="{ name: 'pacientes' }">Pacientes</v-btn>
    </PageHeader>

    <v-row v-if="paciente">
      <v-col cols="12" md="4">
        <v-card border elevation="0" class="h-100">
          <v-card-text>
            <h2 class="vet-section-title">Resumen</h2>
            <div class="mb-2"><strong>Sexo:</strong> {{ paciente.sexo === 'M' ? 'Macho' : 'Hembra' }}
              <template v-if="paciente.fechaNacimiento"> · Nac. {{ etiquetaFecha(paciente.fechaNacimiento) }}</template></div>
            <div class="mb-2 d-flex align-center ga-2">
              <strong>Último peso:</strong>
              <span>{{ ultimoPeso ? `${ultimoPeso.pesoKg} kg (${etiquetaFecha(ultimoPeso.fecha)})` : paciente.pesoActual != null ? paciente.pesoActual + ' kg' : '—' }}</span>
              <v-btn v-if="puedePeso" size="x-small" variant="tonal" icon="mdi-plus" title="Registrar peso"
                @click="pesoForm.pesoKg = null; pesoForm.fecha = hoy(); errorForm = ''; pesoDialog = true" />
            </div>
            <div class="d-flex align-center mb-1">
              <strong class="flex-grow-1">Alergias</strong>
              <v-btn v-if="puedeAlergia" size="x-small" variant="tonal" icon="mdi-plus" title="Agregar alergia"
                @click="Object.assign(alergiaForm, { descripcion: '', reaccion: '' }); errorForm = ''; alergiaDialog = true" />
            </div>
            <div class="d-flex flex-wrap ga-1">
              <v-chip v-for="a in activas" :key="a.id" size="small" variant="flat" prepend-icon="mdi-alert"
                :color="a.severidad === 'ALTA' ? 'error' : a.severidad === 'MEDIA' ? 'warning' : 'grey'"
                :closable="puedeAlergia" @click:close="quitar = a"
                :text="`${a.descripcion}${a.severidad ? ' (' + a.severidad.toLowerCase() + ')' : ''}`" />
              <span v-if="!activas.length" class="text-medium-emphasis">Sin alergias registradas</span>
            </div>
            <div v-if="paciente.alertas" class="mt-3"><v-icon icon="mdi-bell-alert-outline" size="small" /> {{ paciente.alertas }}</div>
            <div class="mt-3">
              <strong>Últimos resultados críticos</strong>
              <div v-for="o in criticos" :key="o.id" class="text-body-2">
                <v-icon icon="mdi-alert-octagon" color="error" size="small" />
                <router-link :to="{ name: 'orden-lab', params: { id: o.id } }">{{ o.codigo }}</router-link> · {{ o.examenes.join(', ') }}
              </div>
              <div v-if="!criticos.length" class="text-medium-emphasis text-body-2">Ninguno reciente</div>
            </div>
            <div class="mt-3">
              <strong>Próximos controles</strong>
              <div v-for="c in controles" :key="c.id" class="text-body-2">
                <v-icon icon="mdi-calendar-clock-outline" size="small" /> {{ etiquetaFecha(c.fechaSugerida) }} · {{ c.motivo }}
              </div>
              <div v-if="!controles.length" class="text-medium-emphasis text-body-2">Ninguno programado</div>
            </div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" md="8">
        <v-card border elevation="0" class="h-100">
          <v-tabs v-model="tab" color="primary" density="comfortable" show-arrows>
            <v-tab value="historia" prepend-icon="mdi-timeline-text-outline">Todo</v-tab>
            <v-tab value="consultas" prepend-icon="mdi-stethoscope">Consultas</v-tab>
            <v-tab value="laboratorio" prepend-icon="mdi-flask-outline">Laboratorio</v-tab>
            <v-tab value="imagenes" prepend-icon="mdi-image-search-outline">Imágenes</v-tab>
            <v-tab value="patologia" prepend-icon="mdi-microscope">Patología</v-tab>
            <v-tab value="procedimientos" prepend-icon="mdi-content-cut">Procedimientos</v-tab>
            <v-tab value="archivos" prepend-icon="mdi-paperclip">Archivos</v-tab>
            <v-tab value="vacunacion" prepend-icon="mdi-needle">Vacunación</v-tab>
            <v-tab value="formulas" prepend-icon="mdi-prescription">Fórmulas</v-tab>
            <v-tab v-if="puedeCompartir" value="envios" prepend-icon="mdi-send-outline">Envíos</v-tab>
            <v-tab v-if="auth.can.cobrosHistoria" value="cobros" prepend-icon="mdi-cash-multiple">Cobros</v-tab>
          </v-tabs>
          <v-divider />
          <v-card-text v-if="tab === 'vacunacion'">
            <VacunacionPanel :paciente="paciente" @cambio="loadHistoria" />
          </v-card-text>
          <v-card-text v-if="tab === 'laboratorio'"><OrdenesPacientePanel :paciente-id="id" :tipos="['LAB']" titulo="Órdenes de laboratorio" @cambio="loadResumenClinico" /></v-card-text>
          <v-card-text v-if="tab === 'imagenes'"><OrdenesPacientePanel :paciente-id="id" :tipos="['IMAGEN', 'FUNCIONAL']" titulo="Órdenes de imagen y estudios funcionales" /></v-card-text>
          <v-card-text v-if="tab === 'patologia'"><OrdenesPacientePanel :paciente-id="id" :tipos="['PATOLOGIA']" titulo="Patología y citología" /></v-card-text>
          <v-card-text v-if="tab === 'procedimientos'"><ProcedimientosPanel :paciente-id="id" /></v-card-text>
          <v-card-text v-if="tab === 'archivos'"><ArchivosPanel :paciente-id="id" @cambio="loadHistoria" /></v-card-text>
          <v-card-text v-if="tab === 'formulas'"><FormulasPanel :paciente-id="id" /></v-card-text>
          <v-card-text v-if="tab === 'envios'">
            <div class="d-flex flex-wrap align-center ga-2 mb-2">
              <h2 class="vet-section-title flex-grow-1 mb-0">Historial de envíos al tutor</h2>
              <EnviarTutorButton :paciente-id="id" label="Enviar carné de vacunación" :documentos="[{ tipo: 'CARNET', id: id, titulo: 'Carné de vacunación', enviable: true }]" />
            </div>
            <HistorialEnviosPanel :paciente-id="id" />
          </v-card-text>
          <v-card-text v-if="tab === 'cobros'"><CobrosPanel :paciente-id="id" :tutores="paciente.tutores ?? []" /></v-card-text>
          <v-card-text v-show="tabLinea">
            <div class="d-flex align-center flex-wrap ga-2 mb-2">
              <h2 class="vet-section-title flex-grow-1 mb-0">{{ tab === 'consultas' ? 'Consultas' : 'Línea de tiempo' }} <span class="text-caption text-medium-emphasis">({{ total }})</span></h2>
              <v-select v-if="tab === 'historia'" v-model="tipo" :items="TIPOS" label="Tipo de evento" density="compact" hide-details style="max-width: 240px" />
            </div>
            <v-progress-linear v-if="loading" indeterminate color="primary" />
            <v-timeline v-if="items.length" density="compact" side="end" class="mt-2">
              <v-timeline-item v-for="(it, i) in items" :key="it.tipo + it.referenciaId + i" size="small"
                :dot-color="META[it.tipo]?.color" :icon="META[it.tipo]?.icon">
                <div class="d-flex flex-wrap align-center ga-2">
                  <v-chip size="x-small" variant="tonal" :color="META[it.tipo]?.color" :text="META[it.tipo]?.label ?? it.tipo" />
                  <span class="text-caption text-medium-emphasis">{{ etiquetaFecha(it.fecha) }}</span>
                  <EstadoChip v-if="it.estado && it.tipo === 'CONSULTA'" :estado="it.estado" size="x-small" />
                </div>
                <div class="font-weight-bold">
                  <router-link v-if="rutaEvento(it)" :to="rutaEvento(it)!">{{ it.titulo }}</router-link>
                  <template v-else>{{ it.titulo }}</template>
                </div>
                <div v-if="it.detalle" class="text-body-2 text-medium-emphasis">{{ it.detalle }}</div>
              </v-timeline-item>
            </v-timeline>
            <div v-else-if="!loading" class="text-medium-emphasis py-6 text-center">Sin eventos en la historia.</div>
            <div v-if="totalPages > 1" class="d-flex justify-center mt-3">
              <v-pagination v-model="page" :length="totalPages" :total-visible="6" @update:model-value="loadHistoria" />
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-dialog v-model="pesoDialog" max-width="400">
      <v-card>
        <v-card-title class="pt-4">Registrar peso</v-card-title>
        <v-card-text>
          <v-alert v-if="errorForm" type="error" variant="tonal" density="compact" class="mb-3" :text="errorForm" />
          <v-text-field v-model.number="pesoForm.pesoKg" label="Peso (kg)" type="number" step="0.01" class="mb-3" />
          <v-text-field v-model="pesoForm.fecha" label="Fecha" type="date" :max="hoy()" />
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer /><v-btn variant="text" @click="pesoDialog = false">Cancelar</v-btn>
          <v-btn color="primary" variant="flat" :loading="guardando" @click="guardarPeso">Guardar</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
    <v-dialog v-model="alergiaDialog" max-width="480">
      <v-card>
        <v-card-title class="pt-4">Agregar alergia</v-card-title>
        <v-card-text>
          <v-alert v-if="errorForm" type="error" variant="tonal" density="compact" class="mb-3" :text="errorForm" />
          <v-text-field v-model="alergiaForm.descripcion" label="Descripción (p. ej. Penicilina)" class="mb-3" />
          <v-row dense>
            <v-col cols="6"><v-select v-model="alergiaForm.severidad" label="Severidad" :items="['BAJA', 'MEDIA', 'ALTA']" /></v-col>
            <v-col cols="6"><v-select v-model="alergiaForm.tipo" label="Tipo" :items="['MEDICAMENTO', 'ALIMENTO', 'AMBIENTAL', 'VACUNA', 'OTRA']" /></v-col>
          </v-row>
          <v-text-field v-model="alergiaForm.reaccion" label="Reacción" class="mt-3" />
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer /><v-btn variant="text" @click="alergiaDialog = false">Cancelar</v-btn>
          <v-btn color="primary" variant="flat" :loading="guardando" @click="guardarAlergia">Guardar</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
    <ConfirmDialog :model-value="!!quitar" title="Quitar alergia" :message="`¿Dar de baja la alergia a ${quitar?.descripcion}?`" confirm-text="Quitar"
      @update:model-value="!$event && (quitar = null)" @confirm="eliminarAlergia" />
  </div>
</template>
