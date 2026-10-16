<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { saveExamen } from '@/api/laboratorio'
import { useCatalogosStore } from '@/stores/catalogos'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { TIPOS_EXAMEN, type ExamenCatalogo, type ExamenRequest, type ParametroExamen, type Rango } from '@/types/etapa'

const props = defineProps<{ modelValue: boolean; examen?: ExamenCatalogo | null }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; guardado: [] }>()
const ui = useUiStore()
const catalogos = useCatalogosStore()

const form = reactive<ExamenRequest>({ codigo: '', nombre: '', tipo: 'LAB', activo: true })
const parametros = ref<ParametroExamen[]>([])
const guardando = ref(false)
const error = ref('')

const num = (v: unknown): number | null => (v === '' || v === null || v === undefined || Number.isNaN(Number(v)) ? null : Number(v))
const txt = (v: string | null | undefined): string | null => (v && v.trim() ? v.trim() : null)

watch(() => props.modelValue, async (v) => {
  if (!v) return
  error.value = ''
  void catalogos.ensure()
  const e = props.examen
  Object.assign(form, e
    ? { codigo: e.codigo, nombre: e.nombre, tipo: e.tipo, categoria: e.categoria ?? '', descripcion: e.descripcion ?? '', muestra: e.muestra ?? '',
        requiereAyuno: e.requiereAyuno, tiempoEntregaHoras: e.tiempoEntregaHoras ?? null, esExterno: e.esExterno, codigoLoinc: e.codigoLoinc ?? '',
        loincEstado: e.loincEstado ?? 'POR_VALIDAR', activo: e.activo }
    : { codigo: '', nombre: '', tipo: 'LAB', categoria: '', descripcion: '', muestra: '', requiereAyuno: false, tiempoEntregaHoras: null,
        esExterno: false, codigoLoinc: '', loincEstado: 'POR_VALIDAR', activo: true })
  parametros.value = (e?.parametros ?? []).map((p) => ({ ...p, rangos: (p.rangos ?? []).map((r) => ({ ...r })) }))
})

function nuevoParametro() {
  parametros.value.push({ codigo: '', nombre: '', unidad: '', unidadUcum: '', tipoValor: 'NUMERICO', orden: parametros.value.length + 1,
    codigoLoinc: '', codificacionEstado: 'POR_VALIDAR', valorNormalTexto: '', opciones: '', activo: true, rangos: [] })
}
function nuevoRango(p: ParametroExamen) {
  const r: Rango = { especieId: catalogos.especies[0]?.id ?? 0, sexo: null, edadMinMeses: null, edadMaxMeses: null, valorMin: null, valorMax: null, criticoMin: null, criticoMax: null, fuente: '' }
  p.rangos = [...(p.rangos ?? []), r]
}

function validar(): string {
  if (!form.codigo.trim() || !form.nombre.trim()) return 'Código y nombre son obligatorios'
  for (const p of parametros.value) {
    if (!p.codigo.trim() || !p.nombre.trim()) return 'Cada parámetro necesita código y nombre'
    for (const r of p.rangos ?? []) {
      const [mn, mx, cmn, cmx] = [num(r.valorMin), num(r.valorMax), num(r.criticoMin), num(r.criticoMax)]
      if (mn !== null && mx !== null && mn > mx) return `${p.codigo}: el mínimo no puede superar el máximo`
      if (cmn !== null && mn !== null && cmn > mn) return `${p.codigo}: el crítico mínimo debe ser menor o igual al mínimo`
      if (cmx !== null && mx !== null && cmx < mx) return `${p.codigo}: el crítico máximo debe ser mayor o igual al máximo`
      const [e1, e2] = [num(r.edadMinMeses), num(r.edadMaxMeses)]
      if (e1 !== null && e2 !== null && e1 > e2) return `${p.codigo}: la edad mínima no puede superar la máxima`
    }
  }
  return ''
}

async function guardar() {
  error.value = validar()
  if (error.value) return
  guardando.value = true
  try {
    const body: ExamenRequest = {
      ...form, codigo: form.codigo.trim(), nombre: form.nombre.trim(), categoria: txt(form.categoria), descripcion: txt(form.descripcion),
      muestra: txt(form.muestra), codigoLoinc: txt(form.codigoLoinc), tiempoEntregaHoras: num(form.tiempoEntregaHoras),
      parametros: form.tipo === 'LAB' || parametros.value.length || props.examen
        ? parametros.value.map((p, i) => ({
            ...p, orden: i + 1, unidad: txt(p.unidad), unidadUcum: txt(p.unidadUcum), codigoLoinc: txt(p.codigoLoinc),
            valorNormalTexto: txt(p.valorNormalTexto), opciones: txt(p.opciones),
            rangos: (p.rangos ?? []).map((r) => ({
              especieId: r.especieId, sexo: r.sexo || null, edadMinMeses: num(r.edadMinMeses), edadMaxMeses: num(r.edadMaxMeses),
              valorMin: num(r.valorMin), valorMax: num(r.valorMax), criticoMin: num(r.criticoMin), criticoMax: num(r.criticoMax), fuente: txt(r.fuente),
            })),
          }))
        : undefined,
    }
    await saveExamen(props.examen?.id ?? null, body)
    ui.showSuccess('Examen guardado')
    emit('update:modelValue', false)
    emit('guardado')
  } catch (e) { error.value = errorText(e, 'No se pudo guardar el examen') } finally { guardando.value = false }
}
const SEXOS = [{ title: 'Ambos', value: null }, { title: 'Macho', value: 'M' }, { title: 'Hembra', value: 'F' }]
const ESTADOS = [{ title: 'Por validar', value: 'POR_VALIDAR' }, { title: 'Validado', value: 'VALIDADO' }]
const TIPOS_VALOR = ['NUMERICO', 'TEXTO', 'CUALITATIVO']
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="1100" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">{{ examen ? 'Editar examen' : 'Nuevo examen' }}</v-card-title>
      <v-card-text>
        <v-alert v-if="examen" type="warning" variant="tonal" density="compact" class="mb-3"
          text="Al guardar, los parámetros que quite de la lista se DESACTIVAN (los resultados históricos los conservan). Los rangos de cada parámetro se reemplazan por los que figuran aquí." />
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-row dense>
          <v-col cols="6" md="3"><v-text-field v-model="form.codigo" label="Código" :disabled="!!examen" maxlength="30" /></v-col>
          <v-col cols="12" md="5"><v-text-field v-model="form.nombre" label="Nombre" maxlength="200" /></v-col>
          <v-col cols="6" md="4"><v-select v-model="form.tipo" :items="TIPOS_EXAMEN" item-title="title" item-value="value" label="Tipo" /></v-col>
          <v-col cols="6" md="3"><v-text-field v-model="form.categoria" label="Categoría" /></v-col>
          <v-col cols="6" md="3"><v-text-field v-model="form.muestra" label="Muestra (p. ej. Sangre EDTA)" /></v-col>
          <v-col cols="6" md="3"><v-text-field v-model.number="form.tiempoEntregaHoras" label="Entrega (horas)" type="number" /></v-col>
          <v-col cols="6" md="3"><v-text-field v-model="form.codigoLoinc" label="LOINC del examen" /></v-col>
          <v-col cols="6" md="3"><v-select v-model="form.loincEstado" :items="ESTADOS" item-title="title" item-value="value" label="Estado LOINC" /></v-col>
          <v-col cols="12" md="9"><v-text-field v-model="form.descripcion" label="Descripción" /></v-col>
          <v-col cols="12" class="d-flex flex-wrap ga-4">
            <v-switch v-model="form.requiereAyuno" label="Requiere ayuno" color="primary" hide-details />
            <v-switch v-model="form.esExterno" label="Examen externo" color="primary" hide-details />
            <v-switch v-model="form.activo" label="Activo" color="primary" hide-details />
          </v-col>
        </v-row>

        <h3 class="vet-section-title mt-4">Parámetros y rangos de referencia</h3>
        <v-expansion-panels variant="accordion">
          <v-expansion-panel v-for="(p, i) in parametros" :key="i">
            <v-expansion-panel-title>
              <strong>{{ p.codigo || 'Nuevo parámetro' }}</strong>&nbsp;{{ p.nombre }}
              <v-chip v-if="p.codificacionEstado === 'POR_VALIDAR' && p.codigoLoinc" size="x-small" color="warning" variant="tonal" class="ml-2" text="LOINC por validar" />
              <v-chip v-if="p.activo === false" size="x-small" class="ml-2" text="Inactivo" />
            </v-expansion-panel-title>
            <v-expansion-panel-text>
              <v-row dense>
                <v-col cols="6" md="2"><v-text-field v-model="p.codigo" label="Código" maxlength="20" /></v-col>
                <v-col cols="12" md="4"><v-text-field v-model="p.nombre" label="Nombre" /></v-col>
                <v-col cols="4" md="2"><v-text-field v-model="p.unidad" label="Unidad" /></v-col>
                <v-col cols="4" md="2"><v-text-field v-model="p.unidadUcum" label="UCUM" /></v-col>
                <v-col cols="4" md="2"><v-select v-model="p.tipoValor" :items="TIPOS_VALOR" label="Tipo de valor" /></v-col>
                <v-col cols="6" md="3"><v-text-field v-model="p.codigoLoinc" label="LOINC" /></v-col>
                <v-col cols="6" md="3"><v-select v-model="p.codificacionEstado" :items="ESTADOS" item-title="title" item-value="value" label="Estado codificación" /></v-col>
                <v-col cols="6" md="3"><v-text-field v-model="p.opciones" label="Opciones (A|B)" :disabled="p.tipoValor !== 'CUALITATIVO'" /></v-col>
                <v-col cols="6" md="3"><v-text-field v-model="p.valorNormalTexto" label="Valor normal (texto)" :disabled="p.tipoValor !== 'CUALITATIVO'" /></v-col>
              </v-row>
              <div v-if="p.tipoValor === 'NUMERICO'">
                <div class="text-caption font-weight-bold mb-1">Rangos (orientativos, por validar con el laboratorio)</div>
                <div v-for="(r, j) in p.rangos" :key="j" class="border rounded pa-2 mb-2">
                  <v-row dense>
                    <v-col cols="6" md="3"><v-select v-model="r.especieId" :items="catalogos.especies" item-title="nombre" item-value="id" label="Especie" /></v-col>
                    <v-col cols="6" md="2"><v-select v-model="r.sexo" :items="SEXOS" item-title="title" item-value="value" label="Sexo" /></v-col>
                    <v-col cols="6" md="2"><v-text-field v-model.number="r.edadMinMeses" label="Edad mín. (meses)" type="number" /></v-col>
                    <v-col cols="6" md="2"><v-text-field v-model.number="r.edadMaxMeses" label="Edad máx. (meses)" type="number" /></v-col>
                    <v-col cols="12" md="3" class="d-flex align-center"><v-text-field v-model="r.fuente" label="Fuente" /></v-col>
                    <v-col cols="6" md="2"><v-text-field v-model.number="r.valorMin" label="Mín." type="number" /></v-col>
                    <v-col cols="6" md="2"><v-text-field v-model.number="r.valorMax" label="Máx." type="number" /></v-col>
                    <v-col cols="6" md="2"><v-text-field v-model.number="r.criticoMin" label="Crítico mín." type="number" /></v-col>
                    <v-col cols="6" md="2"><v-text-field v-model.number="r.criticoMax" label="Crítico máx." type="number" /></v-col>
                    <v-col cols="12" md="4" class="d-flex align-center"><v-btn size="small" variant="text" color="error" prepend-icon="mdi-delete-outline" @click="p.rangos?.splice(j, 1)">Quitar rango</v-btn></v-col>
                  </v-row>
                </div>
                <v-btn size="small" variant="tonal" prepend-icon="mdi-plus" @click="nuevoRango(p)">Agregar rango</v-btn>
              </div>
              <v-btn size="small" variant="text" color="error" class="mt-2" prepend-icon="mdi-delete-outline" @click="parametros.splice(i, 1)">Quitar parámetro</v-btn>
            </v-expansion-panel-text>
          </v-expansion-panel>
        </v-expansion-panels>
        <v-btn size="small" variant="tonal" prepend-icon="mdi-plus" class="mt-3" @click="nuevoParametro">Agregar parámetro</v-btn>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn color="primary" variant="flat" :loading="guardando" @click="guardar">Guardar</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
