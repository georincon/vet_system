<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { corregirItem, guardarInformePatologia, validarItem } from '@/api/laboratorio'
import { listVeterinarios } from '@/api/usuarios'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import { useUiStore } from '@/stores/ui'
import type { InformePatologia, InformePatologiaRequest, ItemOrden } from '@/types/etapa'
import type { VeterinarioOpcion } from '@/types'
import { errorText } from '@/utils/errors'
import { etiquetaFecha, etiquetaInstante, hoy } from '@/utils/fecha'

const props = defineProps<{ ordenId: number; item: ItemOrden; puedeEditar: boolean; puedeValidar: boolean }>()
const emit = defineEmits<{ cambio: [] }>()
const ui = useUiStore()

const vets = ref<VeterinarioOpcion[]>([])
const nuevo = () => ({
  tipoMuestra: '', sitioAnatomico: '', fechaToma: hoy(), descripcionMacroscopica: '', descripcionMicroscopica: '',
  diagnosticoMorfologico: '', margenes: '', comentarios: '', laboratorioExterno: '', numeroCasoExterno: '', patologo: '',
  fechaInforme: '', informadoPorId: null as number | null,
})
const form = reactive(nuevo())
const corrForm = reactive(nuevo())
const interpretacion = ref('')
const error = ref('')
const guardando = ref(false)
const confirmarValidar = ref(false)
const validando = ref(false)
const corrigiendo = ref(false)
const motivo = ref('')
const errorCorr = ref('')

const validado = computed(() => props.item.estado === 'VALIDADO')
const informe = computed<InformePatologia | null>(() => props.item.informePatologia ?? null)
const editable = computed(() => props.puedeEditar && !validado.value && props.item.estado !== 'CANCELADO')

function cargar() {
  const i = informe.value
  Object.assign(form, {
    tipoMuestra: i?.tipoMuestra ?? '', sitioAnatomico: i?.sitioAnatomico ?? '', fechaToma: i?.fechaToma ?? hoy(),
    descripcionMacroscopica: i?.descripcionMacroscopica ?? '', descripcionMicroscopica: i?.descripcionMicroscopica ?? '',
    diagnosticoMorfologico: i?.diagnosticoMorfologico ?? '', margenes: i?.margenes ?? '', comentarios: i?.comentarios ?? '',
    laboratorioExterno: i?.laboratorioExterno ?? '', numeroCasoExterno: i?.numeroCasoExterno ?? '', patologo: i?.patologo ?? '',
    fechaInforme: i?.fechaInforme ?? '', informadoPorId: i?.informadoPor?.id ?? null,
  })
  interpretacion.value = ''
}
watch(() => props.item, cargar, { immediate: true, deep: true })
onMounted(async () => { try { vets.value = await listVeterinarios() } catch { vets.value = [] } })

function cuerpo(f: ReturnType<typeof nuevo>): InformePatologiaRequest {
  const t = (v: string) => v.trim() || null
  return {
    tipoMuestra: f.tipoMuestra.trim(), sitioAnatomico: t(f.sitioAnatomico), fechaToma: f.fechaToma || null,
    descripcionMacroscopica: t(f.descripcionMacroscopica), descripcionMicroscopica: t(f.descripcionMicroscopica),
    diagnosticoMorfologico: t(f.diagnosticoMorfologico), margenes: t(f.margenes), comentarios: t(f.comentarios),
    laboratorioExterno: t(f.laboratorioExterno), numeroCasoExterno: t(f.numeroCasoExterno), patologo: t(f.patologo),
    fechaInforme: f.fechaInforme || null, informadoPorId: f.informadoPorId,
  }
}

async function guardar(): Promise<boolean> {
  error.value = ''
  if (!form.tipoMuestra.trim()) { error.value = 'El tipo de muestra es obligatorio'; return false }
  guardando.value = true
  try {
    await guardarInformePatologia(props.ordenId, props.item.id, cuerpo(form))
    ui.showSuccess('Borrador del informe guardado')
    emit('cambio')
    return true
  } catch (e) {
    error.value = errorText(e, 'No se pudo guardar el informe (recuerde que requiere la toma de muestra registrada)')
    return false
  } finally {
    guardando.value = false
  }
}
async function pedirValidar() {
  error.value = ''
  if (!form.diagnosticoMorfologico.trim()) { error.value = 'Para validar se exige el diagnóstico morfológico'; return }
  if (!(await guardar())) return
  confirmarValidar.value = true
}
async function validar() {
  validando.value = true
  try {
    await validarItem(props.ordenId, props.item.id, interpretacion.value)
    confirmarValidar.value = false
    ui.showSuccess('Informe validado')
    emit('cambio')
  } catch (e) {
    confirmarValidar.value = false
    error.value = errorText(e, 'No se pudo validar el informe')
  } finally {
    validando.value = false
  }
}
function abrirCorreccion() {
  Object.assign(corrForm, form)
  motivo.value = ''
  errorCorr.value = ''
  corrigiendo.value = true
}
async function corregir() {
  errorCorr.value = ''
  if (motivo.value.trim().length < 5) { errorCorr.value = 'Escriba el motivo de la corrección (mínimo 5 caracteres)'; return }
  if (!corrForm.tipoMuestra.trim()) { errorCorr.value = 'El tipo de muestra es obligatorio'; return }
  guardando.value = true
  try {
    await corregirItem(props.ordenId, props.item.id, { motivo: motivo.value.trim(), informePatologia: cuerpo(corrForm) })
    corrigiendo.value = false
    ui.showSuccess('Corrección registrada como nueva versión enlazada')
    emit('cambio')
  } catch (e) {
    errorCorr.value = errorText(e, 'No se pudo registrar la corrección')
  } finally {
    guardando.value = false
  }
}
</script>

<template>
  <div class="vet-informe">
    <div class="vet-informe-cabecera d-flex flex-wrap align-center ga-2">
      <strong>{{ item.examenNombre }}</strong>
      <EstadoChip :estado="item.estado" />
      <v-chip v-if="informe" size="small" variant="tonal" :text="`Versión ${informe.version}`" />
      <span v-if="validado" class="text-caption text-medium-emphasis">
        Validado por {{ item.validadoPor?.nombre ?? '—' }} · {{ etiquetaInstante(item.validadoEn) }}
      </span>
    </div>

    <div class="pa-4">
      <v-alert v-if="informe?.reemplazaA" type="info" variant="tonal" density="compact" icon="mdi-history" class="mb-3">
        Corrige la versión anterior. Motivo: {{ informe.motivoCorreccion ?? '—' }}
      </v-alert>
      <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />

      <template v-if="editable">
        <v-row dense>
          <v-col cols="12" md="6"><v-text-field v-model="form.tipoMuestra" label="Tipo de muestra *" maxlength="100" /></v-col>
          <v-col cols="12" md="6"><v-text-field v-model="form.sitioAnatomico" label="Sitio anatómico" maxlength="150" /></v-col>
          <v-col cols="6" md="3"><v-text-field v-model="form.fechaToma" label="Fecha de toma" type="date" /></v-col>
          <v-col cols="6" md="3"><v-text-field v-model="form.fechaInforme" label="Fecha del informe" type="date" /></v-col>
          <v-col cols="12" md="6">
            <v-select v-model="form.informadoPorId" :items="vets" item-title="nombre" item-value="id" label="Informante"
              clearable placeholder="Usuario actual" persistent-placeholder />
          </v-col>
          <v-col cols="12"><v-textarea v-model="form.descripcionMacroscopica" label="Descripción macroscópica" rows="3" auto-grow /></v-col>
          <v-col cols="12"><v-textarea v-model="form.descripcionMicroscopica" label="Descripción microscópica" rows="3" auto-grow /></v-col>
          <v-col cols="12"><v-textarea v-model="form.diagnosticoMorfologico" label="Diagnóstico morfológico *" rows="3" auto-grow /></v-col>
          <v-col cols="12" md="6"><v-textarea v-model="form.margenes" label="Márgenes" rows="2" auto-grow /></v-col>
          <v-col cols="12" md="6"><v-textarea v-model="form.comentarios" label="Comentarios" rows="2" auto-grow /></v-col>
          <v-col cols="12" md="4"><v-text-field v-model="form.laboratorioExterno" label="Laboratorio externo" maxlength="150" /></v-col>
          <v-col cols="12" md="4"><v-text-field v-model="form.numeroCasoExterno" label="N.º de caso externo" maxlength="60" /></v-col>
          <v-col cols="12" md="4"><v-text-field v-model="form.patologo" label="Patólogo" maxlength="150" /></v-col>
          <v-col v-if="puedeValidar" cols="12">
            <v-textarea v-model="interpretacion" label="Interpretación de la validación (opcional)" rows="2" auto-grow />
          </v-col>
        </v-row>
        <div class="d-flex flex-wrap ga-2 justify-end">
          <v-btn variant="tonal" prepend-icon="mdi-content-save-outline" :loading="guardando" @click="guardar">Guardar borrador</v-btn>
          <v-btn v-if="puedeValidar" color="primary" variant="flat" prepend-icon="mdi-check-decagram" @click="pedirValidar">Validar informe</v-btn>
        </div>
      </template>

      <template v-else-if="informe">
        <v-row dense class="vet-proc-texto">
          <v-col cols="12" sm="6"><strong>Tipo de muestra</strong><div>{{ informe.tipoMuestra }}</div></v-col>
          <v-col cols="12" sm="6"><strong>Sitio anatómico</strong><div>{{ informe.sitioAnatomico || '—' }}</div></v-col>
          <v-col cols="6"><strong>Fecha de toma</strong><div>{{ etiquetaFecha(informe.fechaToma) }}</div></v-col>
          <v-col cols="6"><strong>Fecha del informe</strong><div>{{ etiquetaFecha(informe.fechaInforme) }}</div></v-col>
          <v-col cols="12"><strong>Descripción macroscópica</strong><div>{{ informe.descripcionMacroscopica || '—' }}</div></v-col>
          <v-col cols="12"><strong>Descripción microscópica</strong><div>{{ informe.descripcionMicroscopica || '—' }}</div></v-col>
          <v-col cols="12"><strong>Diagnóstico morfológico</strong><div>{{ informe.diagnosticoMorfologico || '—' }}</div></v-col>
          <v-col cols="12" sm="6"><strong>Márgenes</strong><div>{{ informe.margenes || '—' }}</div></v-col>
          <v-col cols="12" sm="6"><strong>Comentarios</strong><div>{{ informe.comentarios || '—' }}</div></v-col>
          <v-col cols="12" sm="4"><strong>Laboratorio externo</strong><div>{{ informe.laboratorioExterno || '—' }}</div></v-col>
          <v-col cols="12" sm="4"><strong>Caso externo</strong><div>{{ informe.numeroCasoExterno || '—' }}</div></v-col>
          <v-col cols="12" sm="4"><strong>Patólogo</strong><div>{{ informe.patologo || '—' }}</div></v-col>
          <v-col v-if="item.interpretacion" cols="12"><strong>Interpretación</strong><div>{{ item.interpretacion }}</div></v-col>
        </v-row>
        <div v-if="validado && puedeValidar" class="d-flex justify-end mt-2">
          <v-btn variant="tonal" color="warning" prepend-icon="mdi-pencil-box-outline" @click="abrirCorreccion">Corregir</v-btn>
        </div>
      </template>
      <div v-else class="text-medium-emphasis">Aún no se ha redactado el informe.</div>
    </div>

    <ConfirmDialog v-model="confirmarValidar" title="Validar informe" color="primary" confirm-text="Validar" :loading="validando"
      message="Al validar, el informe queda inmutable; cualquier cambio posterior se hará como corrección enlazada con motivo. ¿Desea continuar?"
      @confirm="validar" />

    <v-dialog v-model="corrigiendo" max-width="860" scrollable>
      <v-card>
        <v-card-title class="pt-4">Corregir informe validado</v-card-title>
        <v-card-text>
          <v-alert v-if="errorCorr" type="error" variant="tonal" density="compact" class="mb-3" :text="errorCorr" />
          <v-textarea v-model="motivo" label="Motivo de la corrección * (mínimo 5 caracteres)" rows="2" auto-grow class="mb-3" />
          <v-row dense>
            <v-col cols="12" md="6"><v-text-field v-model="corrForm.tipoMuestra" label="Tipo de muestra *" /></v-col>
            <v-col cols="12" md="6"><v-text-field v-model="corrForm.sitioAnatomico" label="Sitio anatómico" /></v-col>
            <v-col cols="12"><v-textarea v-model="corrForm.descripcionMacroscopica" label="Descripción macroscópica" rows="2" auto-grow /></v-col>
            <v-col cols="12"><v-textarea v-model="corrForm.descripcionMicroscopica" label="Descripción microscópica" rows="2" auto-grow /></v-col>
            <v-col cols="12"><v-textarea v-model="corrForm.diagnosticoMorfologico" label="Diagnóstico morfológico" rows="3" auto-grow /></v-col>
            <v-col cols="12" md="6"><v-textarea v-model="corrForm.margenes" label="Márgenes" rows="2" auto-grow /></v-col>
            <v-col cols="12" md="6"><v-textarea v-model="corrForm.comentarios" label="Comentarios" rows="2" auto-grow /></v-col>
          </v-row>
        </v-card-text>
        <v-card-actions class="px-4 pb-4">
          <v-spacer />
          <v-btn variant="text" @click="corrigiendo = false">Cancelar</v-btn>
          <v-btn color="warning" variant="flat" :loading="guardando" @click="corregir">Registrar corrección</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
