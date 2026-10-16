<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { corregirItem, guardarInformeImagen, validarItem } from '@/api/laboratorio'
import { listVeterinarios } from '@/api/usuarios'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import { useUiStore } from '@/stores/ui'
import type { InformeImagen, InformeImagenRequest, ItemOrden } from '@/types/etapa'
import type { VeterinarioOpcion } from '@/types'
import { errorText } from '@/utils/errors'
import { etiquetaFecha, etiquetaInstante, hoy } from '@/utils/fecha'

const props = defineProps<{ ordenId: number; item: ItemOrden; puedeEditar: boolean; puedeValidar: boolean }>()
const emit = defineEmits<{ cambio: [] }>()
const ui = useUiStore()

const vets = ref<VeterinarioOpcion[]>([])
const form = reactive({
  region: '', tecnica: '', hallazgos: '', impresionDiagnostica: '', recomendaciones: '', equipo: '',
  fechaEstudio: hoy(), informadoPorId: null as number | null,
})
const interpretacion = ref('')
const error = ref('')
const guardando = ref(false)
const confirmarValidar = ref(false)
const validando = ref(false)

const validado = computed(() => props.item.estado === 'VALIDADO')
const informe = computed<InformeImagen | null>(() => props.item.informeImagen ?? null)
const editable = computed(() => props.puedeEditar && !validado.value && props.item.estado !== 'CANCELADO')

// Corrección
const corrigiendo = ref(false)
const motivo = ref('')
const errorCorr = ref('')
const corrForm = reactive({ ...form })

function cargar() {
  const i = informe.value
  Object.assign(form, {
    region: i?.region ?? '', tecnica: i?.tecnica ?? '', hallazgos: i?.hallazgos ?? '',
    impresionDiagnostica: i?.impresionDiagnostica ?? '', recomendaciones: i?.recomendaciones ?? '',
    equipo: i?.equipo ?? '', fechaEstudio: i?.fechaEstudio ?? hoy(), informadoPorId: i?.informadoPor?.id ?? null,
  })
  interpretacion.value = ''
}
watch(() => props.item, cargar, { immediate: true, deep: true })
onMounted(async () => { try { vets.value = await listVeterinarios() } catch { vets.value = [] } })

function cuerpo(f: typeof form): InformeImagenRequest {
  const t = (v: string) => v.trim() || null
  return {
    region: f.region.trim(), tecnica: t(f.tecnica), hallazgos: t(f.hallazgos), impresionDiagnostica: t(f.impresionDiagnostica),
    recomendaciones: t(f.recomendaciones), equipo: t(f.equipo), fechaEstudio: f.fechaEstudio || null, informadoPorId: f.informadoPorId,
  }
}

async function guardar(): Promise<boolean> {
  error.value = ''
  if (!form.region.trim()) { error.value = 'La región u órgano es obligatoria'; return false }
  guardando.value = true
  try {
    await guardarInformeImagen(props.ordenId, props.item.id, cuerpo(form))
    ui.showSuccess('Borrador del informe guardado')
    emit('cambio')
    return true
  } catch (e) {
    error.value = errorText(e, 'No se pudo guardar el informe')
    return false
  } finally {
    guardando.value = false
  }
}

async function pedirValidar() {
  error.value = ''
  if (!form.hallazgos.trim() || !form.impresionDiagnostica.trim()) {
    error.value = 'Para validar se exigen los hallazgos y la impresión diagnóstica'
    return
  }
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
  if (!corrForm.region.trim()) { errorCorr.value = 'La región u órgano es obligatoria'; return }
  guardando.value = true
  try {
    await corregirItem(props.ordenId, props.item.id, { motivo: motivo.value.trim(), informeImagen: cuerpo(corrForm) })
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
          <v-col cols="12" md="6"><v-text-field v-model="form.region" label="Región / órgano *" maxlength="150" /></v-col>
          <v-col cols="12" md="6"><v-text-field v-model="form.equipo" label="Equipo" maxlength="150" /></v-col>
          <v-col cols="12" md="6"><v-text-field v-model="form.fechaEstudio" label="Fecha del estudio" type="date" /></v-col>
          <v-col cols="12" md="6">
            <v-select v-model="form.informadoPorId" :items="vets" item-title="nombre" item-value="id" label="Informante"
              clearable placeholder="Usuario actual" persistent-placeholder />
          </v-col>
          <v-col cols="12"><v-textarea v-model="form.tecnica" label="Técnica" rows="2" auto-grow /></v-col>
          <v-col cols="12"><v-textarea v-model="form.hallazgos" label="Hallazgos *" rows="4" auto-grow /></v-col>
          <v-col cols="12"><v-textarea v-model="form.impresionDiagnostica" label="Impresión diagnóstica *" rows="3" auto-grow /></v-col>
          <v-col cols="12"><v-textarea v-model="form.recomendaciones" label="Recomendaciones" rows="2" auto-grow /></v-col>
          <v-col v-if="puedeValidar" cols="12">
            <v-textarea v-model="interpretacion" label="Interpretación de la validación (opcional; por defecto la impresión diagnóstica)" rows="2" auto-grow />
          </v-col>
        </v-row>
        <div class="d-flex flex-wrap ga-2 justify-end">
          <v-btn variant="tonal" prepend-icon="mdi-content-save-outline" :loading="guardando" @click="guardar">Guardar borrador</v-btn>
          <v-btn v-if="puedeValidar" color="primary" variant="flat" prepend-icon="mdi-check-decagram" @click="pedirValidar">Validar informe</v-btn>
        </div>
      </template>

      <template v-else-if="informe">
        <dl class="vet-proc-texto">
          <v-row dense>
            <v-col cols="12" sm="6"><strong>Región / órgano</strong><div>{{ informe.region }}</div></v-col>
            <v-col cols="6" sm="3"><strong>Fecha</strong><div>{{ etiquetaFecha(informe.fechaEstudio) }}</div></v-col>
            <v-col cols="6" sm="3"><strong>Equipo</strong><div>{{ informe.equipo || '—' }}</div></v-col>
            <v-col cols="12"><strong>Técnica</strong><div>{{ informe.tecnica || '—' }}</div></v-col>
            <v-col cols="12"><strong>Hallazgos</strong><div>{{ informe.hallazgos || '—' }}</div></v-col>
            <v-col cols="12"><strong>Impresión diagnóstica</strong><div>{{ informe.impresionDiagnostica || '—' }}</div></v-col>
            <v-col cols="12"><strong>Recomendaciones</strong><div>{{ informe.recomendaciones || '—' }}</div></v-col>
            <v-col cols="12"><strong>Informante</strong><div>{{ informe.informadoPor?.nombre ?? '—' }}</div></v-col>
            <v-col v-if="item.interpretacion" cols="12"><strong>Interpretación</strong><div>{{ item.interpretacion }}</div></v-col>
          </v-row>
        </dl>
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
            <v-col cols="12" md="6"><v-text-field v-model="corrForm.region" label="Región / órgano *" /></v-col>
            <v-col cols="12" md="6"><v-text-field v-model="corrForm.equipo" label="Equipo" /></v-col>
            <v-col cols="12"><v-textarea v-model="corrForm.tecnica" label="Técnica" rows="2" auto-grow /></v-col>
            <v-col cols="12"><v-textarea v-model="corrForm.hallazgos" label="Hallazgos" rows="3" auto-grow /></v-col>
            <v-col cols="12"><v-textarea v-model="corrForm.impresionDiagnostica" label="Impresión diagnóstica" rows="3" auto-grow /></v-col>
            <v-col cols="12"><v-textarea v-model="corrForm.recomendaciones" label="Recomendaciones" rows="2" auto-grow /></v-col>
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
