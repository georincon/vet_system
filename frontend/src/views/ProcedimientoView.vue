<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { cambiarEstadoProcedimiento, getProcedimiento, rutaReporteProcedimientoPdf } from '@/api/procedimientos'
import EnviarTutorButton from '@/components/compartir/EnviarTutorButton.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import MotivoDialog from '@/components/MotivoDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import GaleriaArchivos from '@/components/archivos/GaleriaArchivos.vue'
import ConsentimientoPanel from '@/components/procedimientos/ConsentimientoPanel.vue'
import MonitoreoPanel from '@/components/procedimientos/MonitoreoPanel.vue'
import ProcedimientoDialog from '@/components/procedimientos/ProcedimientoDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { TIPOS_ANESTESIA, type EstadoProcedimiento, type Procedimiento } from '@/types/etapa'
import { documentoDeProcedimiento } from '@/utils/compartir'
import { abrirPdf } from '@/utils/descarga'
import { errorText } from '@/utils/errors'
import { etiquetaFecha, etiquetaInstante } from '@/utils/fecha'

const route = useRoute()
const auth = useAuthStore()
const ui = useUiStore()
const id = computed(() => Number(route.params.id))

const p = ref<Procedimiento | null>(null)
const loading = ref(true)
const errorCarga = ref('')
const editar = ref(false)
const cambiando = ref(false)
const errorEstado = ref('')
const motivoEstado = ref<EstadoProcedimiento | null>(null)
const pdfCargando = ref(false)
const consentRef = ref<HTMLElement | null>(null)

async function cargar() {
  try {
    p.value = await getProcedimiento(id.value)
    errorCarga.value = ''
  } catch (e) {
    errorCarga.value = errorText(e, 'No se pudo cargar el procedimiento')
  } finally {
    loading.value = false
  }
}

const TRANSICIONES: Record<EstadoProcedimiento, EstadoProcedimiento[]> = {
  PROGRAMADA: ['EN_CURSO', 'REALIZADA', 'CANCELADA', 'APLAZADA'],
  APLAZADA: ['PROGRAMADA', 'CANCELADA'],
  EN_CURSO: ['REALIZADA', 'CANCELADA'],
  REALIZADA: [],
  CANCELADA: [],
}
const ACCION: Record<EstadoProcedimiento, { texto: string; icon: string; color: string }> = {
  EN_CURSO: { texto: 'Iniciar', icon: 'mdi-play-circle-outline', color: 'primary' },
  REALIZADA: { texto: 'Marcar realizada', icon: 'mdi-check-circle-outline', color: 'success' },
  CANCELADA: { texto: 'Cancelar', icon: 'mdi-close-circle-outline', color: 'error' },
  APLAZADA: { texto: 'Aplazar', icon: 'mdi-calendar-clock', color: 'warning' },
  PROGRAMADA: { texto: 'Reprogramar', icon: 'mdi-calendar-refresh', color: 'primary' },
}
const acciones = computed(() => (p.value ? TRANSICIONES[p.value.estado] : []))
const puedeEscribir = computed(() => auth.can.procedimientosWrite)

const sinConsentimiento = computed(() =>
  !!p.value && p.value.tipo === 'CIRUGIA' && p.value.consentimiento?.estado !== 'FIRMADO' && !p.value.urgencia
  && !['REALIZADA', 'CANCELADA'].includes(p.value.estado))
const requiereConsentimiento = (e: EstadoProcedimiento) => sinConsentimiento.value && (e === 'EN_CURSO' || e === 'REALIZADA')

async function irConsentimiento() {
  consentRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function cambiar(e: EstadoProcedimiento, motivo?: string) {
  cambiando.value = true
  errorEstado.value = ''
  try {
    p.value = await cambiarEstadoProcedimiento(id.value, e, motivo)
    motivoEstado.value = null
    ui.showSuccess(`Estado actualizado: ${ACCION[e].texto.toLowerCase()}`)
  } catch (err) {
    motivoEstado.value = null
    errorEstado.value = errorText(err, 'No se pudo cambiar el estado')
  } finally {
    cambiando.value = false
  }
}
function pedir(e: EstadoProcedimiento) {
  errorEstado.value = ''
  if (e === 'CANCELADA' || e === 'APLAZADA') motivoEstado.value = e
  else void cambiar(e)
}

async function pdf() {
  if (!p.value) return
  pdfCargando.value = true
  try { await abrirPdf(rutaReporteProcedimientoPdf(p.value.id), `${p.value.codigo}.pdf`) }
  catch (e) { ui.showError(errorText(e, 'No se pudo descargar el reporte')) }
  finally { pdfCargando.value = false }
}

const anestesiaTexto = computed(() => TIPOS_ANESTESIA.find((t) => t.value === p.value?.anestesiaTipo)?.title ?? '—')

watch(id, () => { loading.value = true; void cargar() })
onMounted(cargar)
</script>

<template>
  <div>
    <PageHeader :title="p ? `${p.tipo === 'CIRUGIA' ? 'Cirugía' : 'Procedimiento'} ${p.codigo}` : 'Procedimiento'" :subtitle="p?.procedimiento ?? ''">
      <v-btn v-if="p" variant="flat" prepend-icon="mdi-arrow-left" :to="{ name: 'historia', params: { id: p.pacienteId }, query: { tab: 'procedimientos' } }">Historia</v-btn>
      <v-btn v-if="p && puedeEscribir && p.estado !== 'CANCELADA'" variant="flat" prepend-icon="mdi-pencil" @click="editar = true">Editar</v-btn>
      <v-btn v-if="p && puedeEscribir" variant="flat" prepend-icon="mdi-file-pdf-box" :loading="pdfCargando" @click="pdf">Descargar reporte PDF</v-btn>
      <EnviarTutorButton v-if="p" :paciente-id="p.pacienteId" :documentos="[documentoDeProcedimiento(p)]" />
    </PageHeader>

    <v-progress-linear v-if="loading" indeterminate color="primary" />
    <v-alert v-else-if="errorCarga" type="error" variant="tonal" :text="errorCarga" />

    <template v-else-if="p">
      <div class="vet-info-bar mb-4">
        <div class="d-flex flex-wrap align-center ga-3">
          <strong class="text-h6"><router-link :to="{ name: 'historia', params: { id: p.pacienteId } }">{{ p.pacienteNombre }}</router-link></strong>
          <span class="text-medium-emphasis">{{ p.pacienteCodigo }}</span>
          <v-chip size="small" variant="tonal" :color="p.tipo === 'CIRUGIA' ? 'secondary' : 'primary'" :text="p.tipo === 'CIRUGIA' ? 'Cirugía' : 'Procedimiento'" />
          <EstadoChip :estado="p.estado" />
          <v-chip v-if="p.urgencia" size="small" color="error" variant="flat" prepend-icon="mdi-flash-alert" text="Urgencia" />
          <v-chip v-if="p.asa" size="small" variant="tonal" :text="`ASA ${p.asa}`" />
        </div>
        <div class="mt-2 text-body-2">
          <strong>Cirujano:</strong> {{ p.cirujano?.nombre ?? '—' }}
          <template v-if="p.ayudante"> · <strong>Ayudante:</strong> {{ p.ayudante.nombre }}</template>
          <template v-if="p.anestesiologo"> · <strong>Anestesiólogo:</strong> {{ p.anestesiologo.nombre }}</template>
        </div>
        <div class="text-body-2">
          <strong>Programada:</strong> {{ etiquetaInstante(p.programadaPara) }} · <strong>Inicio:</strong> {{ etiquetaInstante(p.inicioEn) }}
          · <strong>Fin:</strong> {{ etiquetaInstante(p.finEn) }}
          <template v-if="p.consultaId"> · <router-link :to="{ name: 'consulta', params: { id: p.consultaId } }">Consulta #{{ p.consultaId }}</router-link></template>
        </div>
        <div v-if="p.motivoCancelacion" class="text-body-2"><strong>Motivo:</strong> {{ p.motivoCancelacion }}</div>
      </div>

      <v-alert v-if="sinConsentimiento" type="error" variant="tonal" icon="mdi-file-sign" class="mb-4" prominent>
        <strong>No se puede iniciar la cirugía sin consentimiento informado firmado</strong>
        <div>Registre el consentimiento del tutor (o marque la cirugía como urgencia) para poder iniciarla o marcarla como realizada.
          <span v-if="p.consentimiento?.estado === 'RECHAZADO'">El tutor rechazó el consentimiento anterior.</span></div>
        <v-btn v-if="puedeEscribir" size="small" variant="flat" color="error" class="mt-2" prepend-icon="mdi-draw-pen" @click="irConsentimiento">Registrar consentimiento</v-btn>
      </v-alert>
      <v-alert v-if="errorEstado" type="error" variant="tonal" class="mb-4" closable :text="errorEstado" @click:close="errorEstado = ''" />

      <div v-if="puedeEscribir && acciones.length" class="d-flex flex-wrap ga-2 mb-4">
        <v-tooltip v-for="e in acciones" :key="e" :disabled="!requiereConsentimiento(e)" text="Requiere consentimiento FIRMADO (salvo urgencia)" location="top">
          <template #activator="{ props: tp }">
            <span v-bind="tp">
              <v-btn :color="ACCION[e].color" variant="tonal" :prepend-icon="ACCION[e].icon" :loading="cambiando"
                :disabled="requiereConsentimiento(e)" @click="pedir(e)">{{ ACCION[e].texto }}</v-btn>
            </span>
          </template>
        </v-tooltip>
      </div>

      <v-card border elevation="0" class="mb-4">
        <v-card-text>
          <h2 class="vet-section-title">Valoración y anestesia</h2>
          <v-row dense class="vet-proc-texto">
            <v-col cols="6" md="3"><strong>Peso</strong><div>{{ p.pesoKg != null ? p.pesoKg + ' kg' : '—' }}</div></v-col>
            <v-col cols="6" md="3"><strong>Ayuno</strong><div>{{ p.ayunoHoras != null ? p.ayunoHoras + ' h' : '—' }}</div></v-col>
            <v-col cols="12" md="6"><strong>Diagnóstico preoperatorio</strong><div>{{ p.diagnosticoPreoperatorio || '—' }}</div></v-col>
            <v-col cols="6" md="3"><strong>Anestesia</strong><div>{{ anestesiaTexto }}</div></v-col>
            <v-col cols="6" md="3"><strong>Premedicación</strong><div>{{ p.premedicacion || '—' }}</div></v-col>
            <v-col cols="6" md="3"><strong>Inducción</strong><div>{{ p.induccion || '—' }}</div></v-col>
            <v-col cols="6" md="3"><strong>Mantenimiento</strong><div>{{ p.mantenimiento || '—' }}</div></v-col>
            <v-col cols="12" md="6"><strong>Analgesia</strong><div>{{ p.analgesia || '—' }}</div></v-col>
          </v-row>

          <h2 class="vet-section-title mt-6">Técnica, hallazgos y complicaciones</h2>
          <v-row dense class="vet-proc-texto">
            <v-col cols="12"><strong>Técnica</strong><div>{{ p.descripcionTecnica || '—' }}</div></v-col>
            <v-col cols="12" md="6"><strong>Hallazgos</strong><div>{{ p.hallazgos || '—' }}</div></v-col>
            <v-col cols="12" md="6"><strong>Complicaciones</strong><div>{{ p.complicaciones || '—' }}</div></v-col>
            <v-col cols="12" md="6"><strong>Material de sutura</strong><div>{{ p.materialSutura || '—' }}</div></v-col>
            <v-col cols="12" md="6"><strong>Muestras para histopatología</strong><div>{{ p.muestrasHistopatologia ? 'Sí' : 'No' }}</div></v-col>
          </v-row>

          <h2 class="vet-section-title mt-6">Insumos</h2>
          <v-table v-if="p.insumos.length" density="compact">
            <thead><tr><th>Descripción</th><th>Cantidad</th><th>Lote</th><th>Notas</th></tr></thead>
            <tbody><tr v-for="(i, k) in p.insumos" :key="i.id ?? k"><td>{{ i.descripcion }}</td><td>{{ i.cantidad }}</td><td>{{ i.lote || '—' }}</td><td>{{ i.notas || '' }}</td></tr></tbody>
          </v-table>
          <div v-else class="text-medium-emphasis text-body-2">Sin insumos registrados.</div>

          <h2 class="vet-section-title mt-6">Resultado y cuidados postoperatorios</h2>
          <v-row dense class="vet-proc-texto">
            <v-col cols="12" md="6"><strong>Diagnóstico postoperatorio</strong><div>{{ p.diagnosticoPostoperatorio || '—' }}</div></v-col>
            <v-col cols="12" md="6"><strong>Resultado</strong><div>{{ p.resultado || '—' }}</div></v-col>
            <v-col cols="12"><strong>Indicaciones postoperatorias</strong><div>{{ p.indicacionesPostoperatorias || '—' }}</div></v-col>
            <v-col cols="12" md="8"><strong>Controles</strong><div>{{ p.controles || '—' }}</div></v-col>
            <v-col cols="12" md="4"><strong>Próximo control</strong><div>{{ etiquetaFecha(p.proximoControl) }}</div></v-col>
          </v-row>
        </v-card-text>
      </v-card>

      <v-card border elevation="0" class="mb-4">
        <v-card-text><MonitoreoPanel :procedimiento="p" :puede-registrar="auth.can.monitoreoAnestesico" @cambio="cargar" /></v-card-text>
      </v-card>

      <v-card border elevation="0" class="mb-4">
        <v-card-text><div ref="consentRef"><ConsentimientoPanel :procedimiento="p" :puede-registrar="puedeEscribir" @cambio="cargar" /></div></v-card-text>
      </v-card>

      <v-card border elevation="0" class="mb-4">
        <v-card-text>
          <GaleriaArchivos titulo="Imágenes y archivos" :archivos="p.archivos" :paciente-id="p.pacienteId" :vinculo="{ cirugiaId: p.id }"
            tipo-subida="IMAGEN" :puede-subir="auth.can.archivosSubir" :puede-eliminar="auth.can.archivosEliminar" @cambio="cargar" />
        </v-card-text>
      </v-card>
    </template>

    <ProcedimientoDialog v-model="editar" :procedimiento="p" @guardado="(x) => { p = x; ui.showSuccess('Cambios guardados') }" />
    <MotivoDialog :model-value="!!motivoEstado" :title="motivoEstado === 'CANCELADA' ? 'Cancelar procedimiento' : 'Aplazar procedimiento'"
      :confirm-text="motivoEstado === 'CANCELADA' ? 'Cancelar procedimiento' : 'Aplazar'" :loading="cambiando"
      @update:model-value="!$event && (motivoEstado = null)" @confirm="(m) => motivoEstado && cambiar(motivoEstado, m)" />
  </div>
</template>
