<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { enviar, previsualizar } from '@/api/compartir'
import { listVeterinarios } from '@/api/usuarios'
import { getPaciente } from '@/api/pacientes'
import EstadoChip from '@/components/EstadoChip.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { TIPOS_AUXILIAR } from '@/utils/compartir'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'
import type { VeterinarioOpcion } from '@/types'
import type { CanalCompartir, CompartirRequest, DocumentoCompartible, Envio, Previsualizacion } from '@/types/etapa'

const props = defineProps<{ modelValue: boolean; pacienteId: number; documentos: DocumentoCompartible[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; enviado: [envio: Envio] }>()

const auth = useAuthStore()
const ui = useUiStore()
const esAux = computed(() => auth.rol === 'AUXILIAR')

const clave = (d: DocumentoCompartible) => `${d.tipo}:${d.id}:${d.itemId ?? ''}`
const docs = computed(() => props.documentos.map((d) => {
  if (esAux.value && !TIPOS_AUXILIAR.includes(d.tipo)) {
    return { ...d, enviable: false, motivo: 'Su rol no puede enviar este tipo de documento.' }
  }
  return d
}))
const seleccion = ref<string[]>([])
const canal = ref<CanalCompartir>('EMAIL')
const mensaje = ref('')
const responsableId = ref<number | null>(null)
const veterinarios = ref<VeterinarioOpcion[]>([])
const previa = ref<Previsualizacion | null>(null)
const cargandoPrevia = ref(false)
const errorPrevia = ref('')
const enviando = ref(false)
const resultado = ref<Envio | null>(null)
const tutorId = ref<number | null>(null)
const copiado = ref('')

const seleccionados = computed(() => docs.value.filter((d) => seleccion.value.includes(clave(d)) && d.enviable !== false))
const falta = computed(() => esAux.value && !responsableId.value)

watch(() => props.modelValue, async (v) => {
  if (!v) return
  resultado.value = null; previa.value = null; errorPrevia.value = ''; mensaje.value = ''; copiado.value = ''
  seleccion.value = docs.value.filter((d) => d.enviable !== false).slice(0, 1).map(clave)
  try {
    const p = await getPaciente(props.pacienteId)
    tutorId.value = (p.tutores?.find((t) => t.esPrincipal) ?? p.tutores?.[0])?.id ?? null
  } catch { tutorId.value = null }
  if (esAux.value && !veterinarios.value.length) {
    try { veterinarios.value = await listVeterinarios() } catch { veterinarios.value = [] }
  }
  void cargarPrevia()
})

function cuerpo(): CompartirRequest {
  return {
    pacienteId: props.pacienteId, canal: canal.value, mensaje: mensaje.value.trim() || undefined,
    veterinarioResponsableId: esAux.value ? responsableId.value : undefined,
    documentos: seleccionados.value.map((d) => ({ tipo: d.tipo, id: d.id, itemId: d.itemId })),
  }
}

let timer: number | undefined
async function cargarPrevia() {
  errorPrevia.value = ''
  if (!seleccionados.value.length) { previa.value = null; return }
  cargandoPrevia.value = true
  try { previa.value = await previsualizar(cuerpo()) }
  catch (e) { previa.value = null; errorPrevia.value = errorText(e, 'No se pudo previsualizar el envío') }
  finally { cargandoPrevia.value = false }
}
watch([seleccion, canal, mensaje], () => {
  window.clearTimeout(timer)
  timer = window.setTimeout(() => void cargarPrevia(), 400)
}, { deep: true })

async function confirmar() {
  enviando.value = true
  try {
    resultado.value = await enviar(cuerpo())
    emit('enviado', resultado.value)
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo enviar'))
    await cargarPrevia()
  } finally { enviando.value = false }
}

async function copiar(texto: string) {
  try { await navigator.clipboard.writeText(texto); copiado.value = texto; ui.showSuccess('Enlace copiado') }
  catch { ui.showError('No se pudo copiar; seleccione el texto manualmente') }
}
const cerrar = () => emit('update:modelValue', false)
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="760" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">Enviar al tutor</v-card-title>
      <v-card-text>
        <template v-if="!resultado">
          <h3 class="vet-section-title">1. Documentos</h3>
          <div v-for="d in docs" :key="clave(d)" class="mb-1">
            <v-checkbox v-model="seleccion" :value="clave(d)" :disabled="d.enviable === false" density="compact" hide-details
              :label="d.titulo" />
            <div v-if="d.enviable === false" class="text-caption text-medium-emphasis ml-8">
              <v-icon icon="mdi-lock-outline" size="x-small" /> {{ d.motivo ?? 'No enviable.' }}
            </div>
          </div>
          <div v-if="!docs.length" class="text-medium-emphasis">No hay documentos para compartir.</div>

          <h3 class="vet-section-title mt-4">2. Canal</h3>
          <v-btn-toggle v-model="canal" mandatory color="primary" variant="outlined" divided density="comfortable" aria-label="Canal de envío">
            <v-btn value="EMAIL" prepend-icon="mdi-email-outline">Correo (PDF adjunto)</v-btn>
            <v-btn value="WHATSAPP" prepend-icon="mdi-whatsapp">WhatsApp (enlace seguro)</v-btn>
          </v-btn-toggle>

          <v-select v-if="esAux" v-model="responsableId" class="mt-3" label="Veterinario responsable" :items="veterinarios" item-title="nombre" item-value="id"
            :error-messages="falta ? 'Indique el veterinario responsable' : ''" />
          <v-textarea v-model="mensaje" class="mt-3" label="Nota opcional para el tutor (no escriba datos clínicos)" rows="2" auto-grow counter="500" maxlength="500" />

          <h3 class="vet-section-title mt-4">3. Previsualización</h3>
          <v-progress-linear v-if="cargandoPrevia" indeterminate color="primary" />
          <v-alert v-if="errorPrevia" type="error" variant="tonal" density="compact" :text="errorPrevia" />
          <div v-if="previa" class="vet-info-bar" aria-live="polite">
            <div><strong>Destinatario:</strong> {{ previa.tutor }} · {{ previa.destinatario ?? 'sin contacto' }}</div>
            <div v-if="previa.asunto"><strong>Asunto:</strong> {{ previa.asunto }}</div>
            <div class="mt-2"><strong>Mensaje:</strong></div>
            <div class="text-body-2" style="white-space: pre-wrap">{{ previa.mensaje }}</div>
            <v-alert v-if="previa.faltantes.length" type="warning" variant="tonal" density="compact" class="mt-3" title="No se puede enviar todavía">
              <ul class="pl-4"><li v-for="f in previa.faltantes" :key="f">{{ f }}</li></ul>
              <v-btn v-if="previa.tutorId" size="small" variant="tonal" class="mt-2" prepend-icon="mdi-account-edit-outline"
                :to="{ name: 'tutores', query: { editar: previa.tutorId } }" @click="cerrar">Editar consentimientos del tutor</v-btn>
            </v-alert>
            <div v-for="d in previa.documentos.filter((x) => !x.enviable)" :key="d.tipo + d.id" class="text-caption text-error mt-1">
              <v-icon icon="mdi-alert-circle-outline" size="x-small" /> {{ d.titulo ?? d.tipo }}: {{ d.problema }}
            </div>
          </div>
        </template>

        <template v-else>
          <v-alert :type="resultado.estado === 'FALLIDO' ? 'error' : resultado.estado === 'ENVIADO_SIMULADO' ? 'warning' : 'success'" variant="tonal" class="mb-3"
            :title="resultado.estado === 'ENVIADO' ? 'Enviado' : resultado.estado === 'ENVIADO_SIMULADO' ? 'Enviado en modo simulado' : 'El envío falló'">
            <template v-if="resultado.estado === 'ENVIADO_SIMULADO'">Modo simulado: configure SMTP / WhatsApp Cloud API para enviar de verdad ({{ resultado.proveedor }}).</template>
            <template v-else-if="resultado.estado === 'FALLIDO'">{{ resultado.ultimoError }} Puede reenviar desde el historial de envíos.</template>
            <template v-else>Enviado a {{ resultado.destinatario }}.</template>
          </v-alert>
          <div class="d-flex flex-wrap ga-2 mb-3">
            <EstadoChip :estado="resultado.estado" /><v-chip size="small" variant="tonal" :text="resultado.canal" />
            <v-chip size="small" variant="tonal" :text="etiquetaInstante(resultado.creadoEn)" />
          </div>
          <v-btn v-if="resultado.urlWaMe" :href="resultado.urlWaMe" target="_blank" rel="noopener" color="success" variant="tonal" prepend-icon="mdi-whatsapp" class="mb-3">
            Abrir en WhatsApp</v-btn>
          <div v-if="resultado.enlaces.some((e) => e.url)">
            <strong>Enlaces seguros</strong> <span class="text-caption text-medium-emphasis">(solo se muestran ahora; vencen y tienen límite de descargas)</span>
            <div v-for="e in resultado.enlaces.filter((x) => x.url)" :key="e.id" class="d-flex align-center ga-2 mt-1">
              <v-text-field :model-value="e.url ?? ''" readonly density="compact" hide-details aria-label="Enlace seguro" />
              <v-btn variant="tonal" :prepend-icon="copiado === e.url ? 'mdi-check' : 'mdi-content-copy'" @click="copiar(e.url ?? '')">Copiar</v-btn>
            </div>
          </div>
          <div class="mt-3 text-caption text-medium-emphasis">Documentos: {{ resultado.documentos.map((d) => d.titulo).join(', ') }}</div>
        </template>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="cerrar">{{ resultado ? 'Cerrar' : 'Cancelar' }}</v-btn>
        <v-btn v-if="!resultado" color="primary" variant="flat" prepend-icon="mdi-send-outline" :loading="enviando"
          :disabled="!previa?.puedeEnviar || falta || !seleccionados.length" @click="confirmar">Enviar</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
