<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { listConsultas } from '@/api/consultas'
import { rutaFormulaRecetaPdf } from '@/api/formula'
import EnviarTutorButton from '@/components/compartir/EnviarTutorButton.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import { useUiStore } from '@/stores/ui'
import { abrirPdf } from '@/utils/descarga'
import { errorText } from '@/utils/errors'
import { etiquetaFecha } from '@/utils/fecha'
import type { Consulta, Receta } from '@/types'

const props = defineProps<{ pacienteId: number }>()
const ui = useUiStore()
const consultas = ref<Consulta[]>([])
const loading = ref(false)
const pdfId = ref<number | null>(null)

const formulas = computed(() => consultas.value.flatMap((c) => (c.recetas ?? []).map((r) => ({ c, r }))))
async function load() {
  loading.value = true
  try { consultas.value = (await listConsultas(undefined, props.pacienteId, 0, 50)).items }
  catch (e) { ui.showError(errorText(e, 'No se pudieron cargar las fórmulas')) } finally { loading.value = false }
}
async function pdf(r: Receta) {
  pdfId.value = r.id
  try { await abrirPdf(rutaFormulaRecetaPdf(r.id), `formula-${r.id}.pdf`) }
  catch (e) { ui.showError(errorText(e, 'No se pudo generar la fórmula')) } finally { pdfId.value = null }
}
watch(() => props.pacienteId, load)
onMounted(load)
</script>

<template>
  <div>
    <v-progress-linear v-if="loading" indeterminate color="primary" />
    <v-card v-for="{ c, r } in formulas" :key="r.id" border elevation="0" class="mb-2 vet-evento-tarjeta">
      <v-card-text class="d-flex flex-wrap align-center ga-2">
        <div class="flex-grow-1">
          <div class="font-weight-bold">Fórmula del {{ etiquetaFecha(r.fecha) }} · {{ r.items.length }} medicamento(s)</div>
          <div class="text-body-2 text-medium-emphasis">{{ r.items.map((i) => i.medicamento).join(', ') }}</div>
          <router-link :to="{ name: 'consulta', params: { id: c.id } }" class="text-caption">Consulta {{ c.codigo }}</router-link>
        </div>
        <EstadoChip :estado="r.estado" /><EstadoChip :estado="c.estado" />
        <v-btn size="small" variant="tonal" prepend-icon="mdi-file-pdf-box" :loading="pdfId === r.id" @click="pdf(r)">Fórmula PDF</v-btn>
        <EnviarTutorButton v-if="r.estado !== 'CANCELADA'" :paciente-id="pacienteId" size="small" label="Enviar"
          :documentos="[{ tipo: 'FORMULA', id: r.id, titulo: `Fórmula médica del ${etiquetaFecha(r.fecha)}`, enviable: c.estado === 'CERRADA', motivo: 'La consulta sigue abierta: la fórmula es un borrador.' }]" />
      </v-card-text>
    </v-card>
    <div v-if="!formulas.length && !loading" class="text-medium-emphasis text-center py-6">Sin fórmulas registradas.</div>
  </div>
</template>
