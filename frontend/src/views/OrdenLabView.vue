<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { cambiarEstadoOrden, getExamen, getOrden, registrarMuestra, rutaInformePdf } from '@/api/laboratorio'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import MotivoDialog from '@/components/MotivoDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import GaleriaArchivos from '@/components/archivos/GaleriaArchivos.vue'
import EnviarTutorButton from '@/components/compartir/EnviarTutorButton.vue'
import InformeImagenForm from '@/components/informes/InformeImagenForm.vue'
import InformePatologiaForm from '@/components/informes/InformePatologiaForm.vue'
import ImportarDialog from '@/components/laboratorio/ImportarDialog.vue'
import ResultadosTable from '@/components/laboratorio/ResultadosTable.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { abrirPdf } from '@/utils/descarga'
import { documentosDeOrden } from '@/utils/compartir'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'
import { PRIORIDAD_META } from '@/utils/lab'
import type { ExamenCatalogo, OrdenDetalle, ParametroExamen } from '@/types/etapa'

const route = useRoute()
const auth = useAuthStore()
const ui = useUiStore()
const id = computed(() => Number(route.params.id))

const orden = ref<OrdenDetalle | null>(null)
const parametros = ref<Record<number, ParametroExamen[]>>({})
const loading = ref(true)
const errorCarga = ref('')
const muestraDialog = ref(false)
const observaciones = ref('')
const trabajando = ref(false)
const importar = ref(false)
const motivoCancelar = ref(false)
const confirmarProceso = ref(false)
const generandoPdf = ref(false)

async function cargar() {
  try {
    orden.value = await getOrden(id.value)
    await cargarParametros(orden.value)
    errorCarga.value = ''
  } catch (e) { errorCarga.value = errorText(e, 'No se pudo cargar la orden') } finally { loading.value = false }
}

async function cargarParametros(o: OrdenDetalle) {
  const faltan = [...new Set(o.items.filter((i) => i.tipo === 'LAB' && !parametros.value[i.examenId]).map((i) => i.examenId))]
  const det = await Promise.all(faltan.map((x) => getExamen(x)))
  for (const e of det as ExamenCatalogo[]) parametros.value[e.id] = e.parametros ?? []
}

const abierta = computed(() => !!orden.value && !['CANCELADA', 'COMPLETADA'].includes(orden.value.estado))
const cancelada = computed(() => orden.value?.estado === 'CANCELADA')
const docs = computed(() => (orden.value ? documentosDeOrden(orden.value) : []))

async function tomarMuestra() {
  trabajando.value = true
  try {
    orden.value = await registrarMuestra(id.value, observaciones.value)
    muestraDialog.value = false
    ui.showSuccess('Muestra registrada')
  } catch (e) { ui.showError(errorText(e, 'No se pudo registrar la muestra')) } finally { trabajando.value = false }
}
async function cambiarEstado(estado: 'EN_PROCESO' | 'CANCELADA', motivo?: string) {
  trabajando.value = true
  try {
    orden.value = await cambiarEstadoOrden(id.value, estado, motivo)
    motivoCancelar.value = false; confirmarProceso.value = false
    ui.showSuccess(estado === 'CANCELADA' ? 'Orden cancelada' : 'Orden en proceso')
  } catch (e) { ui.showError(errorText(e, 'No se pudo cambiar el estado')) } finally { trabajando.value = false }
}
async function descargarInforme() {
  generandoPdf.value = true
  try { await abrirPdf(rutaInformePdf(id.value), `informe-${orden.value?.codigo ?? id.value}.pdf`) }
  catch (e) { ui.showError(errorText(e, 'No se pudo generar el informe PDF')) } finally { generandoPdf.value = false }
}

onMounted(cargar)
watch(id, () => { loading.value = true; orden.value = null; void cargar() })
const puedeCapturar = auth.can.laboratorioCapturar
const puedeValidar = auth.can.laboratorioValidar
</script>

<template>
  <div>
    <PageHeader :title="orden ? `Orden ${orden.codigo}` : 'Orden de examen'" :subtitle="orden ? `${orden.pacienteNombre} · ${etiquetaInstante(orden.solicitadaEn)}` : ''">
      <v-btn variant="flat" prepend-icon="mdi-arrow-left" :to="{ name: 'laboratorio' }">Bandeja</v-btn>
      <template v-if="orden">
        <v-btn v-if="auth.can.clinicaRead" variant="flat" prepend-icon="mdi-timeline-text-outline" :to="{ name: 'historia', params: { id: orden.pacienteId } }">Historia</v-btn>
        <v-btn variant="flat" prepend-icon="mdi-file-pdf-box" :loading="generandoPdf" @click="descargarInforme">Informe PDF</v-btn>
        <EnviarTutorButton :paciente-id="orden.pacienteId" :documentos="docs" />
      </template>
    </PageHeader>

    <v-progress-linear v-if="loading" indeterminate color="primary" />
    <v-alert v-else-if="errorCarga" type="error" variant="tonal" :text="errorCarga" />

    <template v-else-if="orden">
      <div class="vet-info-bar mb-4">
        <div class="d-flex flex-wrap align-center ga-3">
          <router-link v-if="auth.can.clinicaRead" class="text-h6" :to="{ name: 'historia', params: { id: orden.pacienteId } }">{{ orden.pacienteNombre }}</router-link>
          <strong v-else class="text-h6">{{ orden.pacienteNombre }}</strong>
          <span>{{ orden.pacienteCodigo }}<template v-if="orden.especie"> · {{ orden.especie }}</template></span>
          <EstadoChip :estado="orden.estado" />
          <v-chip size="small" variant="tonal" :color="PRIORIDAD_META[orden.prioridad]?.color" :prepend-icon="PRIORIDAD_META[orden.prioridad]?.icon" :text="PRIORIDAD_META[orden.prioridad]?.texto" />
          <v-chip v-if="orden.hayCriticos" size="small" color="error" variant="flat" prepend-icon="mdi-alert-octagon" text="Valores críticos" />
          <v-chip v-if="orden.consultaId" size="small" variant="tonal" prepend-icon="mdi-stethoscope" :to="{ name: 'consulta', params: { id: orden.consultaId } }" text="Consulta vinculada" />
        </div>
        <div v-if="orden.indicacionClinica" class="mt-2"><strong>Indicación clínica:</strong> {{ orden.indicacionClinica }}</div>
        <div v-if="orden.laboratorioExterno" class="mt-1"><strong>Laboratorio externo:</strong> {{ orden.laboratorioExterno }}</div>
        <div class="mt-1 text-body-2">
          <strong>Solicitada por:</strong> {{ orden.solicitadoPor?.nombre ?? '—' }}.
          <template v-if="orden.muestraTomadaEn"> <strong>Muestra:</strong> {{ etiquetaInstante(orden.muestraTomadaEn) }} por {{ orden.muestraTomadaPor?.nombre ?? '—' }}<template v-if="orden.muestraObservaciones"> ({{ orden.muestraObservaciones }})</template>.</template>
          <template v-else> Sin muestra registrada.</template>
        </div>
        <div v-if="cancelada" class="mt-2 text-error"><v-icon icon="mdi-cancel" size="small" /> Cancelada: {{ orden.motivoCancelacion }}</div>
        <div v-if="abierta" class="d-flex flex-wrap ga-2 mt-3">
          <v-btn v-if="orden.estado === 'SOLICITADA' && puedeCapturar" size="small" color="primary" variant="flat" prepend-icon="mdi-test-tube" @click="observaciones = ''; muestraDialog = true">Tomar muestra</v-btn>
          <v-btn v-if="puedeCapturar" size="small" variant="tonal" prepend-icon="mdi-upload" @click="importar = true">Importar resultados</v-btn>
          <v-btn v-if="puedeValidar && orden.estado === 'MUESTRA_TOMADA'" size="small" variant="tonal" prepend-icon="mdi-progress-clock" @click="confirmarProceso = true">Marcar en proceso</v-btn>
          <v-btn v-if="puedeValidar" size="small" variant="tonal" color="error" prepend-icon="mdi-cancel" @click="motivoCancelar = true">Cancelar orden</v-btn>
        </div>
      </div>

      <v-card v-for="it in orden.items" :key="it.id" border elevation="0" class="mb-4">
        <v-card-text>
          <div class="d-flex flex-wrap align-center ga-2 mb-2">
            <h2 class="vet-section-title flex-grow-1 mb-0">{{ it.examenNombre }} <span class="text-caption">({{ it.examenCodigo }})</span></h2>
            <EstadoChip :estado="it.estado" />
            <v-chip v-if="it.corregido" size="small" variant="tonal" color="warning" prepend-icon="mdi-history" :text="`Corregido (v${it.version})`" />
          </div>
          <div v-if="it.estado === 'CANCELADO'" class="text-medium-emphasis">Examen cancelado{{ it.motivoCancelacion ? ': ' + it.motivoCancelacion : '' }}.</div>
          <template v-else>
            <ResultadosTable v-if="it.tipo === 'LAB'" :orden="orden" :item="it" :parametros="parametros[it.examenId] ?? []"
              :puede-capturar="puedeCapturar" :puede-validar="puedeValidar" @cambio="cargar" />
            <InformeImagenForm v-else-if="it.tipo === 'IMAGEN' || it.tipo === 'FUNCIONAL'" :orden-id="orden.id" :item="it"
              :puede-editar="puedeValidar && !cancelada" :puede-validar="puedeValidar && !cancelada" @cambio="cargar" />
            <InformePatologiaForm v-else :orden-id="orden.id" :item="it" :puede-editar="puedeValidar && !cancelada" :puede-validar="puedeValidar && !cancelada" @cambio="cargar" />

            <div class="mt-4">
              <GaleriaArchivos :archivos="it.archivos" :paciente-id="orden.pacienteId"
                :vinculo="{ ordenExamenId: orden.id, ordenItemId: it.id, consultaId: orden.consultaId }"
                :tipo-subida="it.tipo === 'IMAGEN' ? 'ECOGRAFIA' : it.tipo === 'LAB' ? 'PDF_LABORATORIO' : 'OTRO'"
                :titulo="it.tipo === 'LAB' ? 'PDF externo del laboratorio' : 'Archivos adjuntos'"
                :puede-subir="auth.can.archivosSubir && !cancelada" :puede-eliminar="auth.can.archivosEliminar" @cambio="cargar" />
            </div>
          </template>
        </v-card-text>
      </v-card>

      <v-card border elevation="0" class="mb-4">
        <v-card-text>
          <h2 class="vet-section-title">Bitácora</h2>
          <v-timeline density="compact" side="end">
            <v-timeline-item v-for="e in orden.eventos" :key="e.id" size="x-small" dot-color="primary">
              <div class="d-flex flex-wrap ga-2 align-center">
                <v-chip size="x-small" variant="tonal" :text="e.tipo" />
                <span class="text-caption text-medium-emphasis">{{ etiquetaInstante(e.creadoEn) }} · {{ e.usuario?.nombre ?? e.origen ?? 'Sistema' }}</span>
              </div>
              <div class="text-body-2">{{ e.detalle ?? (e.estadoNuevo ? `${e.estadoAnterior ?? ''} → ${e.estadoNuevo}` : '') }}</div>
            </v-timeline-item>
          </v-timeline>
        </v-card-text>
      </v-card>
    </template>

    <v-dialog v-model="muestraDialog" max-width="460">
      <v-card>
        <v-card-title class="pt-4">Registrar toma de muestra</v-card-title>
        <v-card-text><v-textarea v-model="observaciones" label="Observaciones (opcional)" rows="2" auto-grow maxlength="255" /></v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer /><v-btn variant="text" @click="muestraDialog = false">Cancelar</v-btn>
          <v-btn color="primary" variant="flat" :loading="trabajando" @click="tomarMuestra">Registrar</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
    <MotivoDialog v-model="motivoCancelar" title="Cancelar orden" confirm-text="Cancelar orden" label="Motivo de la cancelación" :loading="trabajando" @confirm="(m) => cambiarEstado('CANCELADA', m)" />
    <ConfirmDialog v-model="confirmarProceso" title="Marcar en proceso" color="primary" confirm-text="Marcar" :loading="trabajando"
      message="La orden pasará a EN_PROCESO (también ocurre al capturar el primer resultado)." @confirm="cambiarEstado('EN_PROCESO')" />
    <ImportarDialog v-model="importar" :orden-id="orden?.id" :orden-codigo="orden?.codigo" @importado="cargar" />
  </div>
</template>
