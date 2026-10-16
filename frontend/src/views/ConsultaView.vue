<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { cerrarConsulta, getConsulta, listPlantillas, updateConsulta } from '@/api/consultas'
import { getPaciente, listAlergias } from '@/api/pacientes'
import { listPresupuestos } from '@/api/facturacion'
import { getTutor } from '@/api/tutores'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import PageHeader from '@/components/PageHeader.vue'
import EnviarTutorButton from '@/components/compartir/EnviarTutorButton.vue'
import OrdenesPacientePanel from '@/components/laboratorio/OrdenesPacientePanel.vue'
import PlanTratamientoPanel from '@/components/plan/PlanTratamientoPanel.vue'
import ProcedimientosPanel from '@/components/procedimientos/ProcedimientosPanel.vue'
import { rutaFormulaConsultaPdf, rutaResumenConsultaPdf } from '@/api/formula'
import { abrirPdf } from '@/utils/descarga'
import AddendumsPanel from '@/components/consulta/AddendumsPanel.vue'
import DiagnosticosPanel from '@/components/consulta/DiagnosticosPanel.vue'
import RecetasPanel from '@/components/consulta/RecetasPanel.vue'
import SignosVitalesPanel from '@/components/consulta/SignosVitalesPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { TIPOS_CONSULTA, TRIAJES } from '@/utils/clinica'
import { errorText } from '@/utils/errors'
import { etiquetaFecha, etiquetaInstante } from '@/utils/fecha'
import { cop } from '@/utils/dinero'
import type { Presupuesto } from '@/types/fase4'
import type { DocumentoCompartible } from '@/types/etapa'
import type { Alergia, Consulta, Paciente, PlantillaSoap, TipoConsulta, Triaje } from '@/types'

const route = useRoute()
const auth = useAuthStore()
const ui = useUiStore()

const id = computed(() => Number(route.params.id))
const consulta = ref<Consulta | null>(null)
const paciente = ref<Paciente | null>(null)
const alergias = ref<Alergia[]>([])
const plantillas = ref<PlantillaSoap[]>([])
const loading = ref(true)
const saving = ref(false)
const cerrando = ref(false)
const confirmarCierre = ref(false)
const confirmarPlantilla = ref(false)
const plantillaSel = ref<number | null>(null)
const errorCarga = ref('')

const form = reactive({
  motivo: '', anamnesis: '', subjetivo: '', objetivo: '', evaluacion: '', plan: '', pronostico: '',
  tipoConsulta: null as TipoConsulta | null, triaje: null as Triaje | null, proximaRevision: '',
})
const original = ref('')

const cerrada = computed(() => !!consulta.value?.cerrada || consulta.value?.estado === 'CERRADA')
const lecturaTexto = computed(() => cerrada.value || !auth.can.clinicaWrite)
const lecturaSignos = computed(() => cerrada.value || !auth.can.signos)
/** Cargo generado al cerrar (presupuesto borrador creado por el evento ConsultaCerrada). */
const presupuestos = ref<Presupuesto[]>([])
const cargandoCargo = ref(false)
const cargoVisible = computed(() => cerrada.value && auth.can.presupuestosRead)
async function cargarCargo() {
  if (!cargoVisible.value) return
  cargandoCargo.value = true
  try { presupuestos.value = (await listPresupuestos({ consultaId: id.value, size: 5 })).items } catch { presupuestos.value = [] } finally { cargandoCargo.value = false }
}
const dirty = computed(() => JSON.stringify(form) !== original.value)

const plantillasOpt = computed(() => {
  const esp = paciente.value?.especie.id
  return plantillas.value
    .filter((p) => p.especieId == null || p.especieId === esp)
    .map((p) => ({ title: p.nombre + (p.especieId == null ? ' (general)' : ''), value: p.id }))
})

function cargarForm(c: Consulta) {
  Object.assign(form, {
    motivo: c.motivo ?? '', anamnesis: c.anamnesis ?? '', subjetivo: c.subjetivo ?? '', objetivo: c.objetivo ?? '',
    evaluacion: c.evaluacion ?? '', plan: c.plan ?? '', pronostico: c.pronostico ?? '',
    tipoConsulta: c.tipoConsulta ?? null, triaje: c.triaje ?? null, proximaRevision: c.proximaRevision ?? '',
  })
  original.value = JSON.stringify(form)
}

/** Aviso de habeas data: el tutor principal sin consentimiento de correo no recibirá recordatorios. */
const sinConsentimientoCorreo = ref(false)
async function cargarConsentimiento(p: Paciente) {
  sinConsentimientoCorreo.value = false
  const principal = p.tutores?.find((t) => t.esPrincipal) ?? p.tutores?.[0]
  if (!principal) return
  if (principal.aceptaEmail !== undefined) { sinConsentimientoCorreo.value = !principal.aceptaEmail; return }
  try { sinConsentimientoCorreo.value = !(await getTutor(principal.id)).aceptaEmail } catch { /* sin acceso: no se avisa */ }
}

async function load(soloConsulta = false) {
  try {
    const c = await getConsulta(id.value)
    consulta.value = c
    if (!soloConsulta || !dirty.value) cargarForm(c)
    if (!soloConsulta) {
      const [p, a, pl] = await Promise.all([
        getPaciente(c.paciente.id), listAlergias(c.paciente.id).catch(() => []),
        listPlantillas().catch(() => []),
      ])
      paciente.value = p
      alergias.value = a
      plantillas.value = pl
      void cargarConsentimiento(p)
      void cargarCargo()
    } else if (paciente.value) {
      paciente.value = await getPaciente(c.paciente.id)
    }
  } catch (e) {
    errorCarga.value = errorText(e, 'No se pudo cargar la consulta')
  } finally {
    loading.value = false
  }
}

async function guardar(silencioso = false): Promise<boolean> {
  if (!consulta.value || lecturaTexto.value) return true
  saving.value = true
  try {
    // '' limpia el campo en el backend; null/ausente lo deja intacto.
    consulta.value = await updateConsulta(id.value, {
      motivo: form.motivo.trim(), anamnesis: form.anamnesis.trim(), subjetivo: form.subjetivo.trim(),
      objetivo: form.objetivo.trim(), evaluacion: form.evaluacion.trim(), plan: form.plan.trim(),
      pronostico: form.pronostico.trim(), tipoConsulta: form.tipoConsulta, triaje: form.triaje,
      proximaRevision: form.proximaRevision || null,
    })
    original.value = JSON.stringify(form)
    if (!silencioso) ui.showSuccess('Consulta guardada')
    return true
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo guardar la consulta'))
    return false
  } finally {
    saving.value = false
  }
}

async function cerrar() {
  cerrando.value = true
  try {
    if (dirty.value && !(await guardar(true))) return
    consulta.value = await cerrarConsulta(id.value)
    cargarForm(consulta.value)
    window.setTimeout(() => void cargarCargo(), 1200)
    ui.showSuccess('Consulta cerrada. Ya no puede modificarse; use addendums para aclaraciones.')
    confirmarCierre.value = false
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo cerrar la consulta'))
    confirmarCierre.value = false
    await load(true)
  } finally {
    cerrando.value = false
  }
}

function pedirPlantilla() {
  const hayTexto = form.subjetivo || form.objetivo || form.evaluacion || form.plan
  if (hayTexto) confirmarPlantilla.value = true
  else aplicarPlantilla()
}
function aplicarPlantilla() {
  const p = plantillas.value.find((x) => x.id === plantillaSel.value)
  if (!p) return
  form.subjetivo = p.subjetivo ?? ''
  form.objetivo = p.objetivo ?? ''
  form.evaluacion = p.evaluacion ?? ''
  form.plan = p.plan ?? ''
  if (p.tipoConsulta && !form.tipoConsulta) form.tipoConsulta = p.tipoConsulta
  confirmarPlantilla.value = false
}

const addendumKey = ref(0)
const pdfCargando = ref<'formula' | 'resumen' | null>(null)
const recetasVigentes = computed(() => (consulta.value?.recetas ?? []).filter((r) => r.estado !== 'CANCELADA'))
async function descargarPdf(tipo: 'formula' | 'resumen') {
  pdfCargando.value = tipo
  try {
    if (tipo === 'formula') await abrirPdf(rutaFormulaConsultaPdf(id.value), `formula-${consulta.value?.codigo}.pdf`)
    else await abrirPdf(rutaResumenConsultaPdf(id.value), `resumen-${consulta.value?.codigo}.pdf`)
  } catch (e) { ui.showError(errorText(e, 'No se pudo generar el PDF')) } finally { pdfCargando.value = null }
}
const documentosCompartibles = computed<DocumentoCompartible[]>(() => {
  const docs: DocumentoCompartible[] = recetasVigentes.value.map((r) => ({
    tipo: 'FORMULA', id: r.id, titulo: `Fórmula médica del ${etiquetaFecha(r.fecha)}`, enviable: cerrada.value,
    motivo: cerrada.value ? undefined : 'La consulta sigue abierta: la fórmula es un borrador. Cierre la consulta para compartirla.',
  }))
  docs.push({
    tipo: 'RESUMEN_CONSULTA', id: id.value, titulo: `Resumen de la consulta ${consulta.value?.codigo ?? ''}`, enviable: cerrada.value,
    motivo: cerrada.value ? undefined : 'Solo se comparte el resumen de consultas cerradas.',
  })
  return docs
})

const alergiasActivas = computed(() => alergias.value.filter((a) => a.activo))
const colorAlergia = (s?: string | null) => (s === 'ALTA' ? 'error' : s === 'MEDIA' ? 'warning' : 'grey')

onMounted(() => load())
watch(id, () => { loading.value = true; void load() })
</script>

<template>
  <div>
    <PageHeader :title="consulta ? `Consulta ${consulta.codigo}` : 'Consulta clínica'"
      :subtitle="consulta ? `${consulta.paciente.nombre} · ${etiquetaFecha(consulta.fecha)} · ${consulta.veterinario.nombre}` : ''">
      <v-btn variant="flat" prepend-icon="mdi-arrow-left" :to="{ name: 'consultas' }">Consultas</v-btn>
      <v-btn v-if="consulta && auth.can.clinicaRead" variant="flat" prepend-icon="mdi-timeline-text-outline"
        :to="{ name: 'historia', params: { id: consulta.paciente.id } }">Historia</v-btn>
      <v-btn v-if="consulta && recetasVigentes.length" variant="flat" prepend-icon="mdi-file-pdf-box" :loading="pdfCargando === 'formula'" @click="descargarPdf('formula')">Descargar fórmula PDF</v-btn>
      <v-btn v-if="consulta && cerrada" variant="flat" prepend-icon="mdi-file-document-outline" :loading="pdfCargando === 'resumen'" @click="descargarPdf('resumen')">Resumen de consulta PDF</v-btn>
      <EnviarTutorButton v-if="consulta" :paciente-id="consulta.paciente.id" :documentos="documentosCompartibles" variant="flat" />
    </PageHeader>

    <v-progress-linear v-if="loading" indeterminate color="primary" />
    <v-alert v-else-if="errorCarga" type="error" variant="tonal" :text="errorCarga" />

    <template v-else-if="consulta">
      <v-alert v-if="cerrada" type="info" variant="tonal" icon="mdi-lock-outline" class="mb-4">
        <strong>Consulta CERRADA</strong> el {{ etiquetaInstante(consulta.cerradaEn) }}. Es inmutable; registre un addendum para aclaraciones.
        <div v-if="consulta.firmaHash" class="text-caption mt-1">Firma SHA-256: <code>{{ consulta.firmaHash }}</code></div>
      </v-alert>
      <v-alert v-else type="success" variant="tonal" icon="mdi-lock-open-variant-outline" class="mb-4">
        <strong>Consulta ABIERTA</strong>
        <span v-if="consulta.cerrada === false && !auth.can.clinicaWrite"> · Su rol permite solo lectura y registro de signos vitales.</span>
      </v-alert>

      <v-alert v-if="sinConsentimientoCorreo" type="warning" variant="tonal" density="compact" class="mb-4" icon="mdi-email-off-outline"
        text="El tutor principal no tiene consentimiento de correo: no se le enviarán recordatorios de vacunas ni citas. Actualice sus consentimientos en Tutores." />
      <div class="vet-info-bar mb-4">
        <div class="d-flex flex-wrap align-center ga-3">
          <strong class="text-h6">{{ consulta.paciente.nombre }}</strong>
          <span>{{ consulta.paciente.especie }}<template v-if="consulta.paciente.raza"> · {{ consulta.paciente.raza }}</template></span>
          <v-chip size="small" variant="tonal" prepend-icon="mdi-weight-kilogram">
            Peso: {{ paciente?.pesoActual != null ? paciente.pesoActual + ' kg' : '—' }}
          </v-chip>
          <EstadoChip :estado="consulta.estado" />
          <v-chip v-if="consulta.tipoCita" size="small" variant="tonal" :text="consulta.tipoCita.nombre" />
          <v-chip v-if="consulta.sala" size="small" variant="tonal" prepend-icon="mdi-door" :text="consulta.sala.nombre" />
        </div>
        <div class="mt-2 d-flex flex-wrap align-center ga-2">
          <span class="font-weight-bold">Alergias:</span>
          <v-chip v-for="a in alergiasActivas" :key="a.id" size="small" :color="colorAlergia(a.severidad)" variant="flat"
            prepend-icon="mdi-alert" :text="`${a.descripcion}${a.severidad ? ' (' + a.severidad.toLowerCase() + ')' : ''}`" />
          <span v-if="!alergiasActivas.length" class="text-medium-emphasis">Sin alergias registradas</span>
        </div>
        <div v-if="paciente?.alertas" class="mt-2"><v-icon icon="mdi-bell-alert-outline" size="small" /> {{ paciente.alertas }}</div>
      </div>

      <v-card border elevation="0" class="mb-4">
        <v-card-text>
          <h2 class="vet-section-title">S — Subjetivo</h2>
          <v-row dense>
            <v-col cols="12" md="6"><v-text-field v-model="form.motivo" label="Motivo de consulta" :readonly="lecturaTexto" /></v-col>
            <v-col cols="6" md="3"><v-select v-model="form.tipoConsulta" :items="TIPOS_CONSULTA" label="Tipo de consulta" clearable :readonly="lecturaTexto" /></v-col>
            <v-col cols="6" md="3"><v-select v-model="form.triaje" :items="TRIAJES" label="Triaje" clearable :readonly="lecturaTexto" /></v-col>
            <v-col v-if="!lecturaTexto" cols="12" class="d-flex flex-wrap ga-2 align-center">
              <v-select v-model="plantillaSel" :items="plantillasOpt" label="Plantilla SOAP" clearable style="max-width: 360px"
                no-data-text="Sin plantillas para esta especie" />
              <v-btn variant="tonal" prepend-icon="mdi-file-document-arrow-right-outline" :disabled="!plantillaSel" @click="pedirPlantilla">
                Aplicar plantilla
              </v-btn>
            </v-col>
            <v-col cols="12"><v-textarea v-model="form.anamnesis" label="Anamnesis" rows="2" auto-grow :readonly="lecturaTexto" /></v-col>
            <v-col cols="12"><v-textarea v-model="form.subjetivo" label="Subjetivo (lo que refiere el tutor)" rows="3" auto-grow :readonly="lecturaTexto" /></v-col>
          </v-row>

          <h2 class="vet-section-title mt-4">O — Objetivo</h2>
          <v-textarea v-model="form.objetivo" label="Examen físico / hallazgos" rows="3" auto-grow :readonly="lecturaTexto" class="mb-3" />
          <SignosVitalesPanel :consulta-id="consulta.id" :signos="consulta.signosVitales ?? []" :readonly="lecturaSignos" @changed="load(true)" />

          <h2 class="vet-section-title mt-6">A — Análisis</h2>
          <v-textarea v-model="form.evaluacion" label="Evaluación clínica" rows="3" auto-grow :readonly="lecturaTexto" class="mb-3" />
          <DiagnosticosPanel :consulta-id="consulta.id" :diagnosticos="consulta.diagnosticos ?? []" :readonly="lecturaTexto" @changed="load(true)" />
          <v-text-field v-model="form.pronostico" label="Pronóstico" :readonly="lecturaTexto" class="mt-3" />

          <h2 class="vet-section-title mt-6">P — Plan</h2>
          <v-textarea v-model="form.plan" label="Plan terapéutico / recomendaciones" rows="3" auto-grow :readonly="lecturaTexto" class="mb-3" />
          <v-text-field v-model="form.proximaRevision" label="Próxima revisión" type="date" :readonly="lecturaTexto" style="max-width: 240px" class="mb-3" />
          <RecetasPanel :consulta-id="consulta.id" :recetas="consulta.recetas ?? []" :readonly="lecturaTexto" @changed="load(true)" />
        </v-card-text>
        <v-card-actions v-if="!lecturaTexto" class="px-4 pb-4">
          <v-spacer />
          <v-btn variant="tonal" prepend-icon="mdi-content-save-outline" :loading="saving" :disabled="!dirty" @click="guardar()">Guardar cambios</v-btn>
          <v-btn color="primary" variant="flat" prepend-icon="mdi-lock-check-outline" @click="confirmarCierre = true">Cerrar consulta</v-btn>
        </v-card-actions>
      </v-card>

      <v-card border elevation="0" class="mb-4">
        <v-card-text>
          <h2 class="vet-section-title">Plan de tratamiento y seguimiento</h2>
          <PlanTratamientoPanel :consulta-id="consulta.id" :cerrada="cerrada" :can-write="auth.can.planWrite" @seguimiento="addendumKey++" />
        </v-card-text>
      </v-card>

      <v-card border elevation="0" class="mb-4">
        <v-card-text>
          <h2 class="vet-section-title">Exámenes, imágenes y patología</h2>
          <OrdenesPacientePanel :paciente-id="consulta.paciente.id" :consulta-id="consulta.id" />
          <h2 class="vet-section-title mt-6">Procedimientos y cirugías</h2>
          <ProcedimientosPanel :paciente-id="consulta.paciente.id" :consulta-id="consulta.id" />
        </v-card-text>
      </v-card>

      <v-card v-if="cargoVisible" border elevation="0" class="mb-4">
        <v-card-text>
          <div class="d-flex align-center ga-2">
            <h2 class="vet-section-title flex-grow-1 mb-0">Cargo de la consulta</h2>
            <v-btn variant="text" size="small" prepend-icon="mdi-refresh" :loading="cargandoCargo" @click="cargarCargo">Actualizar</v-btn>
          </div>
          <p v-if="!presupuestos.length" class="text-medium-emphasis mt-2">{{ cargandoCargo ? 'Buscando…' : 'Aún no hay un presupuesto generado para esta consulta (se crea al cerrarla; actualice en unos segundos).' }}</p>
          <v-list v-else lines="two">
            <v-list-item v-for="pr in presupuestos" :key="pr.id" :to="`/facturacion?tab=documentos&presupuesto=${pr.id}`" prepend-icon="mdi-file-document-outline"
              :title="`${pr.codigo} · ${cop(pr.total)}`" :subtitle="`${pr.items.length} renglón(es) · ${pr.tutor.nombre}`">
              <template #append><EstadoChip :estado="pr.estado" /></template>
            </v-list-item>
          </v-list>
        </v-card-text>
      </v-card>

      <v-card v-if="cerrada" border elevation="0" class="mb-4">
        <v-card-text>
          <h2 class="vet-section-title">Addendums</h2>
          <AddendumsPanel :key="addendumKey" :consulta-id="consulta.id" :can-write="auth.can.clinicaWrite" />
        </v-card-text>
      </v-card>
      <div v-if="consulta.citaId" class="text-caption text-medium-emphasis">Cita vinculada #{{ consulta.citaId }}</div>
    </template>

    <ConfirmDialog v-model="confirmarCierre" title="Cerrar consulta" color="primary" confirm-text="Cerrar consulta" :loading="cerrando"
      message="Al cerrar la consulta quedará firmada e inmutable (solo se podrán agregar addendums) y la cita pasará a Atendida. ¿Desea continuar?"
      @confirm="cerrar" />
    <ConfirmDialog v-model="confirmarPlantilla" title="Aplicar plantilla" color="primary" confirm-text="Reemplazar"
      message="Se reemplazará el texto actual de Subjetivo, Objetivo, Análisis y Plan por el de la plantilla. ¿Continuar?"
      @confirm="aplicarPlantilla" />
  </div>
</template>
