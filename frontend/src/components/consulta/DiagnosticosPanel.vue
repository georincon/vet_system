<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { buscarDiagnosticos, createDiagnostico, deleteDiagnostico, updateDiagnostico } from '@/api/consultas'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import type { Diagnostico, DiagnosticoCatalogo, TipoDiagnostico } from '@/types'

const props = defineProps<{ consultaId: number; diagnosticos: Diagnostico[]; readonly: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const ui = useUiStore()

const TIPOS: { title: string; value: TipoDiagnostico }[] = [
  { title: 'Presuntivo', value: 'PRESUNTIVO' }, { title: 'Diferencial', value: 'DIFERENCIAL' }, { title: 'Definitivo', value: 'DEFINITIVO' },
]
const tipo = ref<TipoDiagnostico>('PRESUNTIVO')
const seleccion = ref<number | null>(null)
const search = ref('')
const opciones = ref<DiagnosticoCatalogo[]>([])
const buscando = ref(false)
const adding = ref(false)
let timer: number | undefined

watch(search, (q) => {
  window.clearTimeout(timer)
  if (!q || q.length < 2 || opciones.value.some((o) => o.nombre === q)) return
  timer = window.setTimeout(async () => {
    buscando.value = true
    try { opciones.value = await buscarDiagnosticos(q) } catch { opciones.value = [] } finally { buscando.value = false }
  }, 250)
})
onBeforeUnmount(() => window.clearTimeout(timer))

async function agregar(catalogoId: number | null, descripcion?: string) {
  adding.value = true
  try {
    await createDiagnostico(props.consultaId, {
      catalogoId: catalogoId ?? undefined,
      descripcion: descripcion || undefined,
      tipo: tipo.value,
      orden: props.diagnosticos.length + 1,
    })
    seleccion.value = null
    search.value = ''
    emit('changed')
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo agregar el diagnóstico'))
  } finally {
    adding.value = false
  }
}

function agregarSeleccion() {
  if (seleccion.value) void agregar(seleccion.value)
  else if (search.value.trim()) void agregar(null, search.value.trim())
}

async function marcarResuelto(d: Diagnostico) {
  try { await updateDiagnostico(props.consultaId, d.id, { resuelto: !d.resuelto }); emit('changed') }
  catch (e) { ui.showError(errorText(e, 'No se pudo actualizar')) }
}
async function cambiarTipo(d: Diagnostico, t: TipoDiagnostico) {
  try { await updateDiagnostico(props.consultaId, d.id, { tipo: t }); emit('changed') }
  catch (e) { ui.showError(errorText(e, 'No se pudo actualizar')) }
}
async function quitar(d: Diagnostico) {
  try { await deleteDiagnostico(props.consultaId, d.id); emit('changed') }
  catch (e) { ui.showError(errorText(e, 'No se pudo eliminar')) }
}
</script>

<template>
  <div>
    <strong>Diagnósticos</strong>
    <v-list v-if="diagnosticos.length" density="compact" class="border rounded mt-2">
      <v-list-item v-for="d in diagnosticos" :key="d.id" :title="d.descripcion"
        :subtitle="[d.codigoCatalogo ?? d.codigoCie, d.lateralidad].filter(Boolean).join(' · ')">
        <template #prepend>
          <v-icon :icon="d.resuelto ? 'mdi-check-circle' : 'mdi-clipboard-pulse-outline'" :color="d.resuelto ? 'success' : undefined" />
        </template>
        <template #append>
          <v-select v-if="!readonly" :model-value="d.tipo" :items="TIPOS" density="compact" hide-details
            style="width: 150px" class="mr-2" @update:model-value="cambiarTipo(d, $event)" />
          <v-chip v-else size="small" variant="tonal" :text="TIPOS.find((t) => t.value === d.tipo)?.title" class="mr-2" />
          <template v-if="!readonly">
            <v-btn size="x-small" variant="text" :icon="d.resuelto ? 'mdi-undo' : 'mdi-check'"
              :title="d.resuelto ? 'Marcar como no resuelto' : 'Marcar como resuelto'" @click="marcarResuelto(d)" />
            <v-btn size="x-small" variant="text" icon="mdi-delete-outline" color="error" title="Quitar" @click="quitar(d)" />
          </template>
        </template>
      </v-list-item>
    </v-list>
    <div v-else class="text-medium-emphasis text-body-2 mt-1">Sin diagnósticos registrados.</div>

    <div v-if="!readonly" class="d-flex align-start flex-wrap ga-2 mt-3">
      <v-autocomplete
        v-model="seleccion"
        v-model:search="search"
        :items="opciones"
        item-title="nombre"
        item-value="id"
        label="Buscar en el catálogo (o escriba un diagnóstico libre)"
        :loading="buscando"
        no-filter
        clearable
        hide-no-data
        style="min-width: 280px; flex: 1 1 320px"
      >
        <template #item="{ props: ip, item }">
          <v-list-item v-bind="ip" :title="item.nombre" :subtitle="[item.codigo, item.categoria].filter(Boolean).join(' · ')" />
        </template>
      </v-autocomplete>
      <v-select v-model="tipo" :items="TIPOS" label="Tipo" style="max-width: 170px" />
      <v-btn color="primary" variant="tonal" prepend-icon="mdi-plus" :loading="adding" size="large" @click="agregarSeleccion">
        Agregar
      </v-btn>
    </div>
  </div>
</template>
