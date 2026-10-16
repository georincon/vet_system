<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { capturarResultados, corregirItem, validarItem } from '@/api/laboratorio'
import { getPaciente } from '@/api/pacientes'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import MotivoDialog from '@/components/MotivoDialog.vue'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'
import { banderaCualitativa, calcularBandera, numeroDe, opcionesDe, textoRango, type RangoAplicado } from '@/utils/lab'
import BanderaChip from './BanderaChip.vue'
import TendenciaDialog from './TendenciaDialog.vue'
import type { Paciente } from '@/types'
import type {
  Bandera, ItemOrden, OrdenDetalle, ParametroExamen, Rango, ResultadoEntrada, ResultadoExamen,
} from '@/types/etapa'

const props = defineProps<{
  orden: OrdenDetalle
  item: ItemOrden
  parametros: ParametroExamen[]
  puedeCapturar: boolean
  puedeValidar: boolean
}>()
const emit = defineEmits<{ cambio: [] }>()
const ui = useUiStore()

const paciente = ref<Paciente | null>(null)
const actual = ref<ItemOrden>(props.item)
const interpretacion = ref('')
const guardando = ref(false)
const validando = ref(false)
const confirmarValidar = ref(false)
const motivoDialog = ref(false)
const motivoCorreccion = ref('')
const corrigiendo = ref(false)
const tendenciaParam = ref<number | null>(null)
const tendenciaDialog = ref(false)
const error = ref('')

interface Fila { valor: string; obs: string; origValor: string; origObs: string }
const filas = reactive<Record<string, Fila>>({})

const activos = computed(() => props.parametros.filter((p) => p.activo !== false).sort((a, b) => (a.orden ?? 0) - (b.orden ?? 0)))
const vigente = (codigo: string): ResultadoExamen | undefined => actual.value.resultados.find((r) => r.parametroCodigo === codigo)

function textoInicial(r?: ResultadoExamen): string {
  if (!r) return ''
  if (r.valorTexto && /^[<>]/.test(r.valorTexto)) return r.valorTexto
  return r.valorNumerico != null ? String(r.valorNumerico) : r.valorTexto ?? ''
}

function iniciar() {
  for (const k of Object.keys(filas)) delete filas[k]
  for (const p of activos.value) {
    const r = vigente(p.codigo)
    const v = textoInicial(r)
    filas[p.codigo] = { valor: v, obs: r?.observacion ?? '', origValor: v, origObs: r?.observacion ?? '' }
  }
  interpretacion.value = actual.value.interpretacion ?? ''
}
watch(() => props.item, (i) => { actual.value = i; corrigiendo.value = false; iniciar() }, { immediate: false })
watch(() => props.parametros, iniciar, { immediate: true })

watch(() => props.orden.pacienteId, async (id) => {
  try { paciente.value = await getPaciente(id) } catch { paciente.value = null }
}, { immediate: true })

function edadMeses(): number | null {
  const n = paciente.value?.fechaNacimiento
  if (!n) return null
  const d = new Date(n)
  const h = new Date()
  return (h.getFullYear() - d.getFullYear()) * 12 + (h.getMonth() - d.getMonth())
}

/** Rango más específico para el paciente (especie, sexo fijado > edad acotada); mismo criterio que el backend. */
function rangoDe(p: ParametroExamen): RangoAplicado | null {
  const pac = paciente.value
  if (!pac) return null
  const edad = edadMeses()
  let mejor: { r: Rango; score: number } | null = null
  for (const r of p.rangos ?? []) {
    if (r.especieId !== pac.especie.id) continue
    if (r.sexo && r.sexo !== pac.sexo) continue
    const conEdad = r.edadMinMeses != null || r.edadMaxMeses != null
    if (conEdad) {
      if (edad === null) continue
      if (r.edadMinMeses != null && edad < r.edadMinMeses) continue
      if (r.edadMaxMeses != null && edad > r.edadMaxMeses) continue
    }
    const score = (r.sexo ? 2 : 0) + (conEdad ? 1 : 0)
    if (!mejor || score > mejor.score) mejor = { r, score }
  }
  return mejor ? { refMin: mejor.r.valorMin, refMax: mejor.r.valorMax, refCriticoMin: mejor.r.criticoMin, refCriticoMax: mejor.r.criticoMax } : null
}

const cambiada = (p: ParametroExamen): boolean => {
  const f = filas[p.codigo]
  return !!f && (f.valor.trim() !== f.origValor.trim() || f.obs.trim() !== f.origObs.trim())
}
const hayCambios = computed(() => activos.value.some(cambiada))

interface Vista { bandera: Bandera | null; critico: boolean; rango: string; unidad: string; envivo: boolean }
function vista(p: ParametroExamen): Vista {
  const r = vigente(p.codigo)
  const f = filas[p.codigo]
  const rango = rangoDe(p)
  const guardado: RangoAplicado | null = r ? { refMin: r.refMin, refMax: r.refMax, refCriticoMin: r.refCriticoMin, refCriticoMax: r.refCriticoMax } : null
  const aplicable = (r && !cambiada(p) ? guardado : rango) ?? guardado
  const unidad = p.unidad ?? r?.unidad ?? ''
  const texto = textoRango(aplicable, p.valorNormalTexto)
  if (f && cambiada(p) && f.valor.trim() !== '') {
    if (p.tipoValor === 'NUMERICO') {
      const c = calcularBandera(numeroDe(f.valor), aplicable ?? {})
      return { ...c, rango: texto, unidad, envivo: true }
    }
    if (p.tipoValor === 'CUALITATIVO') {
      return { bandera: banderaCualitativa(f.valor, p.valorNormalTexto), critico: false, rango: texto, unidad, envivo: true }
    }
    return { bandera: null, critico: false, rango: texto, unidad, envivo: true }
  }
  return { bandera: r?.bandera ?? null, critico: r?.critico ?? false, rango: texto, unidad, envivo: false }
}

const vistas = computed(() => Object.fromEntries(activos.value.map((p) => [p.codigo, vista(p)])) as Record<string, Vista>)
const criticos = computed(() => activos.value.filter((p) => vistas.value[p.codigo]?.critico || vistas.value[p.codigo]?.bandera === 'LL' || vistas.value[p.codigo]?.bandera === 'HH'))

const validado = computed(() => actual.value.estado === 'VALIDADO')
const sinMuestra = computed(() => props.orden.estado === 'SOLICITADA')
const cancelada = computed(() => props.orden.estado === 'CANCELADA' || actual.value.estado === 'CANCELADO')
const editable = computed(() => !cancelada.value && !sinMuestra.value
  && ((!validado.value && props.puedeCapturar) || (corrigiendo.value && props.puedeValidar)))
const hayResultados = computed(() => actual.value.resultados.length > 0)

function entrada(p: ParametroExamen): ResultadoEntrada | null {
  const f = filas[p.codigo]
  const v = f.valor.trim()
  if (v === '') return null
  const base: ResultadoEntrada = { parametroId: p.id ?? undefined, parametroCodigo: p.codigo, unidad: p.unidad || undefined, observacion: f.obs.trim() || undefined }
  if (p.tipoValor === 'NUMERICO') {
    if (/^[<>]/.test(v)) return { ...base, valorTexto: v }
    const n = numeroDe(v)
    return n === null ? { ...base, valorTexto: v } : { ...base, valorNumerico: n }
  }
  return { ...base, valorTexto: v }
}

function cambios(): ResultadoEntrada[] {
  return activos.value.filter(cambiada).map(entrada).filter((e): e is ResultadoEntrada => e !== null)
}

async function guardarBorrador() {
  const lista = cambios()
  if (!lista.length) { error.value = 'No hay valores nuevos para guardar'; return }
  error.value = ''
  guardando.value = true
  try {
    actual.value = await capturarResultados(props.orden.id, props.item.id, lista)
    iniciar()
    ui.showSuccess('Borrador guardado. Las banderas mostradas son las calculadas por el sistema.')
    emit('cambio')
  } catch (e) { error.value = errorText(e, 'No se pudieron guardar los resultados') } finally { guardando.value = false }
}

function pedirValidar() {
  error.value = ''
  if (hayCambios.value) { error.value = 'Guarde el borrador antes de validar'; return }
  if (!hayResultados.value) { error.value = 'Capture al menos un resultado'; return }
  if (criticos.value.length && !interpretacion.value.trim()) { error.value = 'Hay valores críticos: la interpretación del veterinario es obligatoria'; return }
  confirmarValidar.value = true
}
async function validar() {
  validando.value = true
  try {
    actual.value = await validarItem(props.orden.id, props.item.id, interpretacion.value)
    confirmarValidar.value = false
    ui.showSuccess('Resultados validados')
    emit('cambio')
  } catch (e) { confirmarValidar.value = false; error.value = errorText(e, 'No se pudo validar') } finally { validando.value = false }
}

function iniciarCorreccion(motivo: string) {
  motivoCorreccion.value = motivo
  motivoDialog.value = false
  corrigiendo.value = true
}
async function guardarCorreccion() {
  const lista = cambios()
  if (!lista.length && interpretacion.value.trim() === (actual.value.interpretacion ?? '')) { error.value = 'No hay cambios para corregir'; return }
  guardando.value = true
  try {
    actual.value = await corregirItem(props.orden.id, props.item.id, {
      motivo: motivoCorreccion.value, resultados: lista,
      interpretacion: interpretacion.value.trim() || undefined,
    })
    corrigiendo.value = false
    iniciar()
    ui.showSuccess('Corrección registrada (el registro anterior queda enlazado)')
    emit('cambio')
  } catch (e) { error.value = errorText(e, 'No se pudo registrar la corrección') } finally { guardando.value = false }
}

function verTendencia(p: ParametroExamen) {
  if (p.id == null) return
  tendenciaParam.value = p.id
  tendenciaDialog.value = true
}
const claseFila = (p: ParametroExamen) => {
  const v = vistas.value[p.codigo]
  return v?.critico || v?.bandera === 'LL' || v?.bandera === 'HH' ? 'vet-fila-critica' : v?.bandera && v.bandera !== 'N' ? 'vet-fila-alerta' : ''
}
const OR_COLOR: Record<string, string> = { MANUAL: 'grey', ARCHIVO: 'info', INTEGRACION: 'secondary' }
</script>

<template>
  <div>
    <v-alert v-if="sinMuestra && !cancelada" type="warning" variant="tonal" density="compact" class="mb-3"
      text="Registre primero la toma de muestra para poder capturar resultados." />
    <div aria-live="assertive">
      <v-alert v-if="criticos.length" type="error" variant="flat" density="compact" class="mb-3" icon="mdi-alert-octagon"
        :title="`Valores críticos (${criticos.length})`" :text="criticos.map((p) => p.nombre).join(', ')" />
    </div>
    <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
    <v-alert v-if="corrigiendo" type="info" variant="tonal" density="compact" class="mb-3" icon="mdi-pencil-box-outline">
      Modo corrección. Motivo: <strong>{{ motivoCorreccion }}</strong>. Modifique solo los parámetros a corregir.
    </v-alert>

    <div class="vet-informe">
      <div class="vet-informe-cabecera d-flex flex-wrap align-center ga-2">
        <strong>{{ item.examenNombre }}</strong>
        <EstadoChip :estado="actual.estado" />
        <span v-if="validado" class="text-caption text-medium-emphasis">
          Validado por {{ actual.validadoPor?.nombre ?? '—' }} el {{ etiquetaInstante(actual.validadoEn) }} · versión {{ actual.version }}
        </span>
      </div>
      <v-table density="compact" class="vet-tabla-resultados">
        <caption class="vet-sr-only">Resultados de {{ item.examenNombre }}</caption>
        <thead>
          <tr><th>Parámetro</th><th>Valor</th><th>Unidad</th><th>Rango de referencia</th><th>Bandera</th><th>Observación</th><th /></tr>
        </thead>
        <tbody>
          <tr v-for="p in activos" :key="p.codigo" :class="claseFila(p)">
            <td>
              {{ p.nombre }} <span class="text-caption text-medium-emphasis">({{ p.codigo }})</span>
              <div v-if="vigente(p.codigo)" class="d-flex ga-1 mt-1">
                <v-chip size="x-small" variant="tonal" :color="OR_COLOR[vigente(p.codigo)!.origen]" :text="vigente(p.codigo)!.origen" />
                <v-chip v-if="vigente(p.codigo)!.estado === 'BORRADOR'" size="x-small" variant="tonal" color="warning" text="BORRADOR" />
              </div>
            </td>
            <td>
              <template v-if="editable">
                <v-select v-if="p.tipoValor === 'CUALITATIVO'" v-model="filas[p.codigo].valor" :items="opcionesDe(p)" :label="`Valor ${p.nombre}`"
                  class="vet-valor-input" density="compact" clearable />
                <v-text-field v-else v-model="filas[p.codigo].valor" :label="`Valor ${p.nombre}`" class="vet-valor-input" density="compact"
                  :inputmode="p.tipoValor === 'NUMERICO' ? 'decimal' : 'text'" />
              </template>
              <span v-else class="font-weight-bold">{{ filas[p.codigo]?.valor || '—' }}</span>
            </td>
            <td>{{ vistas[p.codigo]?.unidad || '—' }}</td>
            <td>{{ vistas[p.codigo]?.rango }}</td>
            <td>
              <BanderaChip :bandera="vistas[p.codigo]?.bandera" :critico="vistas[p.codigo]?.critico" />
              <div v-if="vistas[p.codigo]?.envivo" class="text-caption text-medium-emphasis">estimada</div>
            </td>
            <td>
              <v-text-field v-if="editable" v-model="filas[p.codigo].obs" :label="`Observación ${p.nombre}`" density="compact" />
              <span v-else>{{ filas[p.codigo]?.obs }}</span>
            </td>
            <td>
              <v-btn v-if="p.tipoValor === 'NUMERICO' && p.id != null" size="x-small" variant="text" icon="mdi-chart-line"
                :aria-label="`Ver tendencia de ${p.nombre}`" title="Ver tendencia" @click="verTendencia(p)" />
            </td>
          </tr>
        </tbody>
      </v-table>
    </div>

    <v-textarea v-model="interpretacion" label="Interpretación del veterinario" rows="2" auto-grow class="mt-3"
      :readonly="!editable"
      :hint="criticos.length ? 'Obligatoria: hay valores críticos' : ''" persistent-hint />

    <div class="d-flex flex-wrap ga-2 mt-3">
      <v-btn v-if="editable && !corrigiendo" variant="tonal" prepend-icon="mdi-content-save-outline" :loading="guardando" :disabled="!hayCambios" @click="guardarBorrador">
        Guardar borrador
      </v-btn>
      <v-btn v-if="editable && !corrigiendo && puedeValidar" color="primary" variant="flat" prepend-icon="mdi-check-decagram" @click="pedirValidar">Validar</v-btn>
      <v-btn v-if="corrigiendo" color="primary" variant="flat" prepend-icon="mdi-content-save-edit-outline" :loading="guardando" @click="guardarCorreccion">Guardar corrección</v-btn>
      <v-btn v-if="corrigiendo" variant="text" @click="corrigiendo = false; iniciar()">Cancelar corrección</v-btn>
      <v-btn v-if="validado && puedeValidar && !corrigiendo && !cancelada" variant="tonal" color="warning" prepend-icon="mdi-pencil-box-outline" @click="motivoDialog = true">Corregir</v-btn>
    </div>

    <v-expansion-panels v-if="actual.resultadosCorregidos.length" class="mt-4" variant="accordion">
      <v-expansion-panel>
        <v-expansion-panel-title>Historial de correcciones ({{ actual.resultadosCorregidos.length }})</v-expansion-panel-title>
        <v-expansion-panel-text>
          <v-table density="compact">
            <thead><tr><th>Parámetro</th><th>Valor anterior</th><th>Bandera</th><th>Registro</th><th>Reemplazado por</th></tr></thead>
            <tbody>
              <tr v-for="r in actual.resultadosCorregidos" :key="r.id">
                <td>{{ r.parametroNombre }}</td>
                <td>{{ r.valorNumerico ?? r.valorTexto ?? '—' }} {{ r.unidad ?? '' }}</td>
                <td><BanderaChip :bandera="r.bandera" :critico="r.critico" /></td>
                <td>#{{ r.id }} · {{ etiquetaInstante(r.registradoEn) }}</td>
                <td>{{ r.reemplazadoPor ? '#' + r.reemplazadoPor : '—' }}<div v-if="r.motivoCorreccion" class="text-caption">{{ r.motivoCorreccion }}</div></td>
              </tr>
            </tbody>
          </v-table>
          <div v-for="r in actual.resultados.filter((x) => x.reemplazaA)" :key="r.id" class="text-caption mt-1">
            #{{ r.id }} ({{ r.parametroNombre }}) reemplaza a #{{ r.reemplazaA }}: {{ r.motivoCorreccion }}
          </div>
        </v-expansion-panel-text>
      </v-expansion-panel>
    </v-expansion-panels>

    <ConfirmDialog v-model="confirmarValidar" title="Validar resultados" color="primary" confirm-text="Validar" :loading="validando"
      message="Al validar, los resultados quedan inmutables y firmados a su nombre. Solo podrán modificarse con una corrección enlazada que conserva el registro original. ¿Desea continuar?"
      @confirm="validar" />
    <MotivoDialog v-model="motivoDialog" title="Corregir resultados validados" label="Motivo de la corrección" color="warning" confirm-text="Continuar"
      message="Se creará un registro nuevo enlazado al original. Indique el motivo (mínimo 5 caracteres)." @confirm="iniciarCorreccion" />
    <TendenciaDialog v-model="tendenciaDialog" :paciente-id="orden.pacienteId" :parametro-id="tendenciaParam" />
  </div>
</template>
