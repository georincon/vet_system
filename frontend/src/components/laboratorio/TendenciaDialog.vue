<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getExamen, getTendencia, listExamenes } from '@/api/laboratorio'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import TendenciaChart from './TendenciaChart.vue'
import type { Tendencia } from '@/types/etapa'

const props = defineProps<{ modelValue: boolean; pacienteId: number; parametroId?: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean] }>()
const ui = useUiStore()

interface Opcion { title: string; value: number }
const opciones = ref<Opcion[]>([])
const seleccionado = ref<number | null>(null)
const tendencia = ref<Tendencia | null>(null)
const loading = ref(false)

async function cargarOpciones() {
  if (opciones.value.length) return
  try {
    const lista = await listExamenes({ tipo: 'LAB' })
    const detalles = await Promise.all(lista.map((e) => getExamen(e.id)))
    const porCodigo = new Map<string, Opcion>()
    for (const d of detalles) {
      for (const p of d.parametros ?? []) {
        if (p.tipoValor !== 'NUMERICO' || p.id == null || p.activo === false) continue
        if (!porCodigo.has(p.codigo)) porCodigo.set(p.codigo, { title: `${p.nombre} (${p.codigo})`, value: p.id })
      }
    }
    opciones.value = [...porCodigo.values()].sort((a, b) => a.title.localeCompare(b.title))
  } catch (e) { ui.showError(errorText(e, 'No se pudo cargar el catálogo de parámetros')) }
}

async function cargar() {
  if (!seleccionado.value) { tendencia.value = null; return }
  loading.value = true
  try { tendencia.value = await getTendencia(props.pacienteId, seleccionado.value) }
  catch (e) { tendencia.value = null; ui.showError(errorText(e, 'No se pudo cargar la tendencia')) }
  finally { loading.value = false }
}

watch(() => props.modelValue, (v) => {
  if (!v) return
  seleccionado.value = props.parametroId ?? null
  tendencia.value = null
  if (!props.parametroId) void cargarOpciones()
  void cargar()
})
watch(seleccionado, () => void cargar())

const dir = computed(() => {
  const d = tendencia.value?.resumen.direccion
  return d === 'SUBE' ? { icon: 'mdi-trending-up', t: 'Sube' } : d === 'BAJA' ? { icon: 'mdi-trending-down', t: 'Baja' }
    : d === 'ESTABLE' ? { icon: 'mdi-trending-neutral', t: 'Estable' } : { icon: 'mdi-help-circle-outline', t: 'Sin datos' }
})
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="860" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">Tendencia del parámetro{{ tendencia ? `: ${tendencia.parametro.nombre}` : '' }}</v-card-title>
      <v-card-text>
        <v-autocomplete v-if="!parametroId" v-model="seleccionado" :items="opciones" label="Parámetro" clearable class="mb-3"
          no-data-text="Sin parámetros numéricos" />
        <v-progress-linear v-if="loading" indeterminate color="primary" />
        <template v-if="tendencia">
          <div class="d-flex flex-wrap ga-3 mb-3" aria-live="polite">
            <v-chip variant="tonal" :text="`Último: ${tendencia.resumen.ultimo ?? '—'} ${tendencia.parametro.unidad ?? ''}`" />
            <v-chip variant="tonal" :text="`Anterior: ${tendencia.resumen.anterior ?? '—'}`" />
            <v-chip variant="tonal" :text="`Variación: ${tendencia.resumen.variacionPorcentual ?? '—'} %`" />
            <v-chip variant="tonal" :prepend-icon="dir.icon" :text="dir.t" />
            <v-chip variant="tonal" :text="`Mín ${tendencia.resumen.minimo ?? '—'} · Máx ${tendencia.resumen.maximo ?? '—'}`" />
          </div>
          <TendenciaChart :puntos="tendencia.puntos" :unidad="tendencia.parametro.unidad" />
        </template>
        <div v-else-if="!loading && !parametroId && !seleccionado" class="text-medium-emphasis text-center py-6">Seleccione un parámetro.</div>
      </v-card-text>
      <v-card-actions class="px-4 pb-4"><v-spacer /><v-btn variant="text" @click="emit('update:modelValue', false)">Cerrar</v-btn></v-card-actions>
    </v-card>
  </v-dialog>
</template>

