<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { actualizarProcedimiento, crearProcedimiento } from '@/api/procedimientos'
import { listVeterinarios } from '@/api/usuarios'
import PacienteSelect from '@/components/agenda/PacienteSelect.vue'
import type { PacienteResumen, VeterinarioOpcion } from '@/types'
import {
  TIPOS_ANESTESIA, type EstadoProcedimiento, type InsumoProcedimiento, type Procedimiento, type ProcedimientoCampos,
  type TipoAnestesia, type TipoProcedimiento,
} from '@/types/etapa'
import { errorText } from '@/utils/errors'

const props = withDefaults(defineProps<{
  modelValue: boolean
  pacienteId?: number | null
  pacienteInicial?: PacienteResumen | null
  consultaId?: number | null
  tipo?: TipoProcedimiento
  procedimiento?: Procedimiento | null
}>(), { tipo: 'PROCEDIMIENTO' })
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; guardado: [p: Procedimiento] }>()

const vets = ref<VeterinarioOpcion[]>([])
const pacienteSel = ref<number | null>(null)
const error = ref('')
const saving = ref(false)
const abiertos = ref<string[]>(['basico'])

const vacio = () => ({
  tipo: 'PROCEDIMIENTO' as TipoProcedimiento, procedimiento: '', veterinarioId: null as number | null,
  ayudanteId: null as number | null, anestesiologoId: null as number | null,
  programadaPara: '', inicioEn: '', finEn: '', asa: null as number | null, urgencia: false,
  ayunoHoras: null as number | null, pesoKg: null as number | null, anestesiaTipo: null as TipoAnestesia | null,
  premedicacion: '', induccion: '', mantenimiento: '', analgesia: '', diagnosticoPreoperatorio: '',
  descripcionTecnica: '', hallazgos: '', complicaciones: '', materialSutura: '', muestrasHistopatologia: false,
  diagnosticoPostoperatorio: '', resultado: '', indicacionesPostoperatorias: '', controles: '', proximoControl: '',
  estado: null as EstadoProcedimiento | null,
})
const form = reactive(vacio())
const insumos = ref<InsumoProcedimiento[]>([])

const editando = computed(() => !!props.procedimiento)
const pacienteFijo = computed(() => !!(props.pacienteId ?? props.procedimiento?.pacienteId))
const esCirugia = computed(() => form.tipo === 'CIRUGIA')

/** ISO (UTC o local) a valor de <input type="datetime-local"> en hora local. */
function aLocal(iso?: string | null): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso.slice(0, 16)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`
}
const aApi = (v: string): string | null => (v ? (v.length === 16 ? `${v}:00` : v) : null)

function iniciar() {
  error.value = ''
  abiertos.value = ['basico']
  Object.assign(form, vacio())
  const p = props.procedimiento
  if (p) {
    Object.assign(form, {
      tipo: p.tipo, procedimiento: p.procedimiento, veterinarioId: p.cirujano?.id ?? null, ayudanteId: p.ayudante?.id ?? null,
      anestesiologoId: p.anestesiologo?.id ?? null, programadaPara: aLocal(p.programadaPara), inicioEn: aLocal(p.inicioEn),
      finEn: aLocal(p.finEn), asa: p.asa ?? null, urgencia: p.urgencia, ayunoHoras: p.ayunoHoras ?? null, pesoKg: p.pesoKg ?? null,
      anestesiaTipo: p.anestesiaTipo ?? null, premedicacion: p.premedicacion ?? '', induccion: p.induccion ?? '',
      mantenimiento: p.mantenimiento ?? '', analgesia: p.analgesia ?? '', diagnosticoPreoperatorio: p.diagnosticoPreoperatorio ?? '',
      descripcionTecnica: p.descripcionTecnica ?? '', hallazgos: p.hallazgos ?? '', complicaciones: p.complicaciones ?? '',
      materialSutura: p.materialSutura ?? '', muestrasHistopatologia: p.muestrasHistopatologia,
      diagnosticoPostoperatorio: p.diagnosticoPostoperatorio ?? '', resultado: p.resultado ?? '',
      indicacionesPostoperatorias: p.indicacionesPostoperatorias ?? '', controles: p.controles ?? '', proximoControl: p.proximoControl ?? '',
    })
    insumos.value = p.insumos.map((i) => ({ ...i }))
    pacienteSel.value = p.pacienteId
  } else {
    form.tipo = props.tipo
    insumos.value = []
    pacienteSel.value = props.pacienteId ?? null
  }
}
watch(() => props.modelValue, (v) => { if (v) iniciar() }, { immediate: true })
onMounted(async () => { try { vets.value = await listVeterinarios() } catch { vets.value = [] } })

function campos(): ProcedimientoCampos {
  const t = (v: string) => v.trim()
  return {
    procedimiento: t(form.procedimiento), veterinarioId: form.veterinarioId, ayudanteId: form.ayudanteId,
    anestesiologoId: form.anestesiologoId, programadaPara: aApi(form.programadaPara), inicioEn: aApi(form.inicioEn),
    finEn: aApi(form.finEn), asa: form.asa, urgencia: form.urgencia, ayunoHoras: form.ayunoHoras, pesoKg: form.pesoKg,
    anestesiaTipo: form.anestesiaTipo, premedicacion: t(form.premedicacion), induccion: t(form.induccion),
    mantenimiento: t(form.mantenimiento), analgesia: t(form.analgesia), diagnosticoPreoperatorio: t(form.diagnosticoPreoperatorio),
    descripcionTecnica: t(form.descripcionTecnica), hallazgos: t(form.hallazgos), complicaciones: t(form.complicaciones),
    materialSutura: t(form.materialSutura), muestrasHistopatologia: form.muestrasHistopatologia,
    diagnosticoPostoperatorio: t(form.diagnosticoPostoperatorio), resultado: t(form.resultado),
    indicacionesPostoperatorias: t(form.indicacionesPostoperatorias), controles: t(form.controles),
    proximoControl: form.proximoControl || null,
    insumos: insumos.value.filter((i) => i.descripcion?.trim()).map((i) => ({
      itemId: i.itemId ?? null, descripcion: i.descripcion.trim(), cantidad: Number(i.cantidad) || 1,
      lote: i.lote?.trim() || null, notas: i.notas?.trim() || null,
    })),
  }
}

async function guardar() {
  error.value = ''
  if (!pacienteSel.value) { error.value = 'Seleccione el paciente'; abiertos.value = ['basico']; return }
  if (!form.procedimiento.trim()) { error.value = 'El nombre del procedimiento es obligatorio'; abiertos.value = ['basico']; return }
  saving.value = true
  try {
    const c = campos()
    const r = props.procedimiento
      ? await actualizarProcedimiento(props.procedimiento.id, c)
      : await crearProcedimiento({
        ...c, pacienteId: pacienteSel.value, consultaId: props.consultaId ?? null, tipo: form.tipo,
        ...(form.estado ? { estado: form.estado } : {}),
      })
    emit('guardado', r)
    emit('update:modelValue', false)
  } catch (e) {
    error.value = errorText(e, 'No se pudo guardar el procedimiento')
  } finally {
    saving.value = false
  }
}

const ESTADOS_INICIALES: { title: string; value: EstadoProcedimiento }[] = [
  { title: 'Programada', value: 'PROGRAMADA' }, { title: 'En curso', value: 'EN_CURSO' }, { title: 'Realizada', value: 'REALIZADA' },
]
const vetOpc = computed(() => vets.value.map((v) => ({ title: v.nombre, value: v.id })))
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="980" scrollable persistent @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">
        {{ editando ? 'Editar' : 'Nueva' }} {{ esCirugia ? 'cirugía' : 'procedimiento' }}
      </v-card-title>
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-alert v-if="esCirugia && !editando" type="info" variant="tonal" density="compact" class="mb-3" icon="mdi-file-sign">
          Una cirugía no puede pasar a EN_CURSO ni REALIZADA sin consentimiento informado FIRMADO, salvo urgencia.
        </v-alert>

        <v-expansion-panels v-model="abiertos" multiple variant="accordion">
          <v-expansion-panel value="basico" title="Datos generales">
            <v-expansion-panel-text>
              <v-row dense>
                <v-col v-if="!pacienteFijo" cols="12">
                  <PacienteSelect v-model="pacienteSel" :inicial="pacienteInicial" />
                </v-col>
                <v-col cols="12" md="4">
                  <v-select v-model="form.tipo" :items="[{ title: 'Procedimiento', value: 'PROCEDIMIENTO' }, { title: 'Cirugía', value: 'CIRUGIA' }]"
                    label="Tipo" :disabled="editando" />
                </v-col>
                <v-col cols="12" md="8"><v-text-field v-model="form.procedimiento" label="Procedimiento *" maxlength="255" /></v-col>
                <v-col cols="12" md="4"><v-select v-model="form.veterinarioId" :items="vetOpc" label="Cirujano / veterinario" clearable placeholder="Usuario actual" persistent-placeholder /></v-col>
                <v-col cols="12" md="4"><v-select v-model="form.ayudanteId" :items="vetOpc" label="Ayudante" clearable /></v-col>
                <v-col cols="12" md="4"><v-select v-model="form.anestesiologoId" :items="vetOpc" label="Anestesiólogo" clearable /></v-col>
                <v-col cols="12" md="4"><v-text-field v-model="form.programadaPara" label="Programada para" type="datetime-local" /></v-col>
                <v-col cols="12" md="4"><v-text-field v-model="form.inicioEn" label="Inicio" type="datetime-local" /></v-col>
                <v-col cols="12" md="4"><v-text-field v-model="form.finEn" label="Fin" type="datetime-local" /></v-col>
                <v-col v-if="!editando" cols="12" md="4"><v-select v-model="form.estado" :items="ESTADOS_INICIALES" label="Estado inicial (opcional)" clearable hint="Por defecto: Realizada (procedimiento) / Programada (cirugía)" persistent-hint /></v-col>
                <v-col cols="12" md="8" class="d-flex align-center">
                  <v-switch v-model="form.urgencia" color="error" hide-details label="Urgencia (permite iniciar sin consentimiento firmado)" />
                </v-col>
              </v-row>
            </v-expansion-panel-text>
          </v-expansion-panel>

          <v-expansion-panel value="valoracion" title="Valoración preoperatoria">
            <v-expansion-panel-text>
              <v-row dense>
                <v-col cols="4" md="3"><v-select v-model="form.asa" :items="[1, 2, 3, 4, 5]" label="ASA" clearable /></v-col>
                <v-col cols="4" md="3"><v-text-field v-model.number="form.ayunoHoras" label="Ayuno (h)" type="number" min="0" /></v-col>
                <v-col cols="4" md="3"><v-text-field v-model.number="form.pesoKg" label="Peso (kg)" type="number" step="0.01" min="0" /></v-col>
                <v-col cols="12"><v-textarea v-model="form.diagnosticoPreoperatorio" label="Diagnóstico preoperatorio" rows="2" auto-grow /></v-col>
              </v-row>
            </v-expansion-panel-text>
          </v-expansion-panel>

          <v-expansion-panel value="anestesia" title="Anestesia">
            <v-expansion-panel-text>
              <v-row dense>
                <v-col cols="12" md="4"><v-select v-model="form.anestesiaTipo" :items="TIPOS_ANESTESIA" label="Tipo de anestesia" clearable /></v-col>
                <v-col cols="12" md="8"><v-text-field v-model="form.premedicacion" label="Premedicación" /></v-col>
                <v-col cols="12" md="4"><v-text-field v-model="form.induccion" label="Inducción" /></v-col>
                <v-col cols="12" md="4"><v-text-field v-model="form.mantenimiento" label="Mantenimiento" /></v-col>
                <v-col cols="12" md="4"><v-text-field v-model="form.analgesia" label="Analgesia" /></v-col>
              </v-row>
            </v-expansion-panel-text>
          </v-expansion-panel>

          <v-expansion-panel value="tecnica" title="Técnica, hallazgos y complicaciones">
            <v-expansion-panel-text>
              <v-row dense>
                <v-col cols="12"><v-textarea v-model="form.descripcionTecnica" label="Técnica / descripción" rows="3" auto-grow /></v-col>
                <v-col cols="12"><v-textarea v-model="form.hallazgos" label="Hallazgos" rows="2" auto-grow /></v-col>
                <v-col cols="12"><v-textarea v-model="form.complicaciones" label="Complicaciones" rows="2" auto-grow /></v-col>
                <v-col cols="12" md="6"><v-text-field v-model="form.materialSutura" label="Material de sutura" maxlength="255" /></v-col>
                <v-col cols="12" md="6" class="d-flex align-center"><v-switch v-model="form.muestrasHistopatologia" color="primary" hide-details label="Muestras para histopatología" /></v-col>
              </v-row>
            </v-expansion-panel-text>
          </v-expansion-panel>

          <v-expansion-panel value="insumos" title="Insumos utilizados">
            <v-expansion-panel-text>
              <div v-for="(i, idx) in insumos" :key="idx" class="mb-2">
                <v-row dense>
                  <v-col cols="12" md="4"><v-text-field v-model="i.descripcion" label="Descripción" /></v-col>
                  <v-col cols="4" md="2"><v-text-field v-model.number="i.cantidad" label="Cantidad" type="number" step="0.01" min="0" /></v-col>
                  <v-col cols="8" md="2"><v-text-field v-model="i.lote" label="Lote" /></v-col>
                  <v-col cols="10" md="3"><v-text-field v-model="i.notas" label="Notas" /></v-col>
                  <v-col cols="2" md="1" class="d-flex align-center">
                    <v-btn icon="mdi-delete-outline" variant="text" color="error" aria-label="Quitar insumo" @click="insumos.splice(idx, 1)" />
                  </v-col>
                </v-row>
              </div>
              <v-btn size="small" variant="tonal" prepend-icon="mdi-plus" @click="insumos.push({ descripcion: '', cantidad: 1, lote: '', notas: '' })">Agregar insumo</v-btn>
              <div class="text-caption text-medium-emphasis mt-2">Solo se guardan las líneas con descripción. Hoy es un registro: no descuenta inventario.</div>
            </v-expansion-panel-text>
          </v-expansion-panel>

          <v-expansion-panel value="post" title="Resultado y cuidados postoperatorios">
            <v-expansion-panel-text>
              <v-row dense>
                <v-col cols="12"><v-textarea v-model="form.diagnosticoPostoperatorio" label="Diagnóstico postoperatorio" rows="2" auto-grow /></v-col>
                <v-col cols="12"><v-textarea v-model="form.resultado" label="Resultado" rows="2" auto-grow /></v-col>
                <v-col cols="12"><v-textarea v-model="form.indicacionesPostoperatorias" label="Indicaciones postoperatorias" rows="2" auto-grow /></v-col>
                <v-col cols="12" md="8"><v-textarea v-model="form.controles" label="Controles" rows="2" auto-grow /></v-col>
                <v-col cols="12" md="4"><v-text-field v-model="form.proximoControl" label="Próximo control" type="date" /></v-col>
              </v-row>
            </v-expansion-panel-text>
          </v-expansion-panel>
        </v-expansion-panels>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn color="primary" variant="flat" :loading="saving" @click="guardar">{{ editando ? 'Guardar cambios' : 'Crear' }}</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
