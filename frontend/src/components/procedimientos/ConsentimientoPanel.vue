<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { subirArchivo } from '@/api/archivos'
import { getPaciente } from '@/api/pacientes'
import { registrarConsentimiento } from '@/api/procedimientos'
import EstadoChip from '@/components/EstadoChip.vue'
import { useUiStore } from '@/stores/ui'
import { METODOS_FIRMA, type ArchivoInfo, type MetodoFirma, type Procedimiento } from '@/types/etapa'
import { tamanoLegible } from '@/utils/descarga'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'

const props = defineProps<{ procedimiento: Procedimiento; puedeRegistrar: boolean }>()
const emit = defineEmits<{ cambio: [] }>()
const ui = useUiStore()

const vigente = computed(() => props.procedimiento.consentimiento ?? null)
const firmado = computed(() => vigente.value?.estado === 'FIRMADO')
const mostrarFormulario = computed(() => props.puedeRegistrar && !firmado.value)

const form = reactive({
  aceptado: true, firmanteNombre: '', firmanteDocumento: '', metodoFirma: 'TABLET' as MetodoFirma,
  fecha: '', texto: '', editarTexto: false,
})
const tutorId = ref<number | null>(null)
const archivo = ref<ArchivoInfo | null>(null)
const subiendo = ref(false)
const progreso = ref(0)
const guardando = ref(false)
const error = ref('')
const inputArchivo = ref<HTMLInputElement | null>(null)

onMounted(async () => {
  if (!mostrarFormulario.value) return
  try {
    const p = await getPaciente(props.procedimiento.pacienteId)
    const t = p.tutores?.find((x) => x.esPrincipal) ?? p.tutores?.[0]
    if (t) {
      tutorId.value = t.id
      form.firmanteNombre = `${t.nombres} ${t.apellidos}`.trim()
    }
  } catch { /* se puede digitar a mano */ }
})

async function elegirArchivo(e: Event) {
  const el = e.target as HTMLInputElement
  const f = el.files?.[0]
  el.value = ''
  if (!f) return
  const ext = f.name.split('.').pop()?.toLowerCase() ?? ''
  if (!['pdf', 'jpg', 'jpeg', 'png', 'webp'].includes(ext)) { error.value = 'El consentimiento firmado debe ser PDF o imagen (jpg, png, webp)'; return }
  if (f.size > 50 * 1024 * 1024) { error.value = 'El archivo supera 50 MB'; return }
  error.value = ''
  subiendo.value = true
  progreso.value = 0
  try {
    archivo.value = await subirArchivo({
      archivo: f, pacienteId: props.procedimiento.pacienteId, tipo: 'CONSENTIMIENTO', cirugiaId: props.procedimiento.id,
      descripcion: 'Consentimiento informado firmado',
    }, (p) => { progreso.value = p })
    ui.showSuccess('Consentimiento escaneado subido')
  } catch (err) {
    error.value = errorText(err, 'No se pudo subir el archivo')
  } finally {
    subiendo.value = false
  }
}

async function registrar() {
  error.value = ''
  if (!form.firmanteNombre.trim()) { error.value = 'El nombre de quien firma es obligatorio'; return }
  guardando.value = true
  try {
    await registrarConsentimiento(props.procedimiento.id, {
      tutorId: tutorId.value, aceptado: form.aceptado, firmanteNombre: form.firmanteNombre.trim(),
      firmanteDocumento: form.firmanteDocumento.trim() || null, metodoFirma: form.metodoFirma,
      texto: form.editarTexto && form.texto.trim() ? form.texto.trim() : null,
      fecha: form.fecha ? (form.fecha.length === 16 ? `${form.fecha}:00` : form.fecha) : null,
      archivoId: archivo.value?.id ?? null,
    })
    ui.showSuccess(form.aceptado ? 'Consentimiento firmado registrado' : 'Rechazo del consentimiento registrado')
    emit('cambio')
  } catch (e) {
    error.value = errorText(e, 'No se pudo registrar el consentimiento')
  } finally {
    guardando.value = false
  }
}
</script>

<template>
  <div>
    <h2 class="vet-section-title">Consentimiento informado</h2>

    <v-card v-if="vigente" border elevation="0" class="mb-3">
      <v-card-text>
        <div class="d-flex flex-wrap align-center ga-2 mb-2">
          <EstadoChip :estado="vigente.estado" />
          <span v-if="vigente.firmadoEn" class="text-caption text-medium-emphasis">{{ etiquetaInstante(vigente.firmadoEn) }}</span>
          <span v-if="vigente.metodoFirma" class="text-caption text-medium-emphasis">· {{ METODOS_FIRMA.find((m) => m.value === vigente!.metodoFirma)?.title ?? vigente.metodoFirma }}</span>
        </div>
        <div><strong>Firmante:</strong> {{ vigente.firmanteNombre ?? '—' }}<template v-if="vigente.firmanteDocumento"> · Doc. {{ vigente.firmanteDocumento }}</template></div>
        <div v-if="vigente.tutor"><strong>Tutor:</strong> {{ vigente.tutor.nombre }}</div>
        <div v-if="vigente.registradoPor" class="text-caption text-medium-emphasis">Registrado por {{ vigente.registradoPor.nombre }}</div>
        <v-alert v-if="vigente.estado === 'RECHAZADO'" type="warning" variant="tonal" density="compact" class="mt-2"
          text="El tutor rechazó el consentimiento: esto no habilita la cirugía. Puede registrar uno nuevo si el tutor cambia de decisión." />
        <v-expansion-panels v-if="vigente.textoFirmado" variant="accordion" class="mt-2">
          <v-expansion-panel title="Texto firmado">
            <v-expansion-panel-text><div class="vet-proc-consent-texto">{{ vigente.textoFirmado }}</div></v-expansion-panel-text>
          </v-expansion-panel>
        </v-expansion-panels>
      </v-card-text>
    </v-card>
    <div v-else-if="!puedeRegistrar" class="text-medium-emphasis text-body-2">Sin consentimiento registrado.</div>

    <v-card v-if="mostrarFormulario" border elevation="0">
      <v-card-text>
        <div class="font-weight-bold mb-2">{{ vigente ? 'Registrar un nuevo consentimiento' : 'Registrar consentimiento del tutor' }}</div>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-radio-group v-model="form.aceptado" inline hide-details class="mb-2" label="Decisión del tutor">
          <v-radio label="Acepta" :value="true" color="primary" />
          <v-radio label="Rechaza" :value="false" color="error" />
        </v-radio-group>
        <v-row dense>
          <v-col cols="12" md="5"><v-text-field v-model="form.firmanteNombre" label="Nombre de quien firma *" /></v-col>
          <v-col cols="12" md="3"><v-text-field v-model="form.firmanteDocumento" label="Documento" maxlength="30" /></v-col>
          <v-col cols="12" md="4"><v-select v-model="form.metodoFirma" :items="METODOS_FIRMA" label="Método de firma" /></v-col>
          <v-col cols="12" md="4"><v-text-field v-model="form.fecha" label="Fecha y hora (vacío = ahora)" type="datetime-local" /></v-col>
          <v-col cols="12" md="8" class="d-flex flex-wrap align-center ga-2">
            <input ref="inputArchivo" type="file" accept=".pdf,.jpg,.jpeg,.png,.webp" class="vet-sr-only" tabindex="-1" @change="elegirArchivo">
            <v-btn variant="tonal" prepend-icon="mdi-paperclip" :loading="subiendo" @click="inputArchivo?.click()">Subir consentimiento firmado</v-btn>
            <span v-if="archivo" class="text-body-2"><v-icon icon="mdi-check-circle" color="success" size="small" /> {{ archivo.nombreOriginal }} ({{ tamanoLegible(archivo.tamanoBytes) }})</span>
            <v-progress-linear v-if="subiendo" :model-value="progreso" color="primary" height="6" rounded />
          </v-col>
          <v-col cols="12">
            <v-switch v-model="form.editarTexto" color="primary" hide-details label="Escribir un texto propio (si no, se usa la plantilla activa)" />
            <v-textarea v-if="form.editarTexto" v-model="form.texto" label="Texto del consentimiento" rows="5" auto-grow class="mt-2" />
          </v-col>
        </v-row>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn :color="form.aceptado ? 'primary' : 'error'" variant="flat" prepend-icon="mdi-draw-pen" :loading="guardando" @click="registrar">
          {{ form.aceptado ? 'Registrar firma' : 'Registrar rechazo' }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </div>
</template>
