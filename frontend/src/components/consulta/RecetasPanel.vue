<script setup lang="ts">
import { ref } from 'vue'
import { createReceta, deleteReceta, updateReceta } from '@/api/consultas'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import { useUiStore } from '@/stores/ui'
import { rutaFormulaRecetaPdf } from '@/api/formula'
import { abrirPdf } from '@/utils/descarga'
import { errorText } from '@/utils/errors'
import { etiquetaFecha } from '@/utils/fecha'
import type { Receta, RecetaItem } from '@/types'

const props = defineProps<{ consultaId: number; recetas: Receta[]; readonly: boolean }>()
const emit = defineEmits<{ changed: [] }>()
const ui = useUiStore()

const dialog = ref(false)
const editando = ref<Receta | null>(null)
const saving = ref(false)
const error = ref('')
const anular = ref<Receta | null>(null)
const indicaciones = ref('')
const validaHasta = ref('')
const controlada = ref(false)
const items = ref<RecetaItem[]>([])

const vacio = (): RecetaItem => ({
  medicamento: '', concentracion: '', presentacion: '', dosis: '', via: '', frecuencia: '', duracion: '', cantidad: null, indicaciones: '',
})

function abrir(r?: Receta) {
  editando.value = r ?? null
  error.value = ''
  indicaciones.value = r?.indicacionesGenerales ?? ''
  validaHasta.value = r?.validaHasta ?? ''
  controlada.value = r?.esControlada ?? false
  items.value = r ? r.items.map((i) => ({ ...i })) : [vacio()]
  dialog.value = true
}

async function guardar() {
  const validos = items.value.filter((i) => i.medicamento?.trim())
  if (!validos.length) { error.value = 'Agregue al menos un medicamento'; return }
  saving.value = true
  error.value = ''
  try {
    const body = {
      indicacionesGenerales: indicaciones.value.trim(),
      validaHasta: validaHasta.value || undefined,
      esControlada: controlada.value,
      items: validos.map((i, idx) => ({
        itemId: i.itemId ?? undefined, medicamento: i.medicamento.trim(), concentracion: i.concentracion?.trim() ?? '', presentacion: i.presentacion?.trim() ?? '', dosis: i.dosis?.trim() ?? '',
        via: i.via?.trim() ?? '', frecuencia: i.frecuencia?.trim() ?? '', duracion: i.duracion?.trim() ?? '',
        cantidad: i.cantidad === null || (i.cantidad as unknown) === '' ? undefined : Number(i.cantidad),
        indicaciones: i.indicaciones?.trim() ?? '', orden: idx + 1,
      })),
    }
    if (editando.value) await updateReceta(props.consultaId, editando.value.id, body)
    else await createReceta(props.consultaId, body)
    ui.showSuccess('Receta guardada')
    dialog.value = false
    emit('changed')
  } catch (e) {
    error.value = errorText(e, 'No se pudo guardar la receta')
  } finally {
    saving.value = false
  }
}

const pdfId = ref<number | null>(null)
async function descargarFormula(r: Receta) {
  pdfId.value = r.id
  try { await abrirPdf(rutaFormulaRecetaPdf(r.id), `formula-${r.id}.pdf`) }
  catch (e) { ui.showError(errorText(e, 'No se pudo generar la fórmula')) }
  finally { pdfId.value = null }
}

async function anularReceta() {
  if (!anular.value) return
  try {
    await deleteReceta(props.consultaId, anular.value.id)
    anular.value = null
    emit('changed')
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo anular la receta'))
  }
}
</script>

<template>
  <div>
    <div class="d-flex align-center mb-2">
      <strong class="flex-grow-1">Receta</strong>
      <v-btn v-if="!readonly" size="small" color="primary" variant="tonal" prepend-icon="mdi-plus" @click="abrir()">Nueva receta</v-btn>
    </div>
    <v-card v-for="r in recetas" :key="r.id" border elevation="0" class="mb-3">
      <v-card-text>
        <div class="d-flex align-center flex-wrap ga-2 mb-2">
          <EstadoChip :estado="r.estado" />
          <v-chip v-if="r.esControlada" size="small" color="error" variant="tonal" text="Controlada" />
          <span class="text-caption text-medium-emphasis">
            {{ etiquetaFecha(r.fecha) }}<template v-if="r.validaHasta"> · válida hasta {{ etiquetaFecha(r.validaHasta) }}</template>
          </span>
          <v-spacer />
          <v-btn size="x-small" variant="tonal" prepend-icon="mdi-file-pdf-box" :loading="pdfId === r.id" @click="descargarFormula(r)">Descargar fórmula PDF</v-btn>
          <template v-if="!readonly && r.estado !== 'CANCELADA'">
            <v-btn size="x-small" variant="text" icon="mdi-pencil" title="Editar" @click="abrir(r)" />
            <v-btn size="x-small" variant="text" icon="mdi-cancel" color="error" title="Anular receta" @click="anular = r" />
          </template>
        </div>
        <v-table density="compact">
          <thead><tr><th>Medicamento</th><th>Dosis</th><th>Vía</th><th>Frecuencia</th><th>Duración</th><th>Cant.</th></tr></thead>
          <tbody>
            <tr v-for="i in r.items" :key="i.id">
              <td>{{ i.medicamento }}<span v-if="i.concentracion || i.presentacion" class="text-medium-emphasis"> · {{ [i.concentracion, i.presentacion].filter(Boolean).join(' ') }}</span><div v-if="i.indicaciones" class="text-caption text-medium-emphasis">{{ i.indicaciones }}</div></td>
              <td>{{ i.dosis || '—' }}</td><td>{{ i.via || '—' }}</td><td>{{ i.frecuencia || '—' }}</td>
              <td>{{ i.duracion || '—' }}</td><td>{{ i.cantidad ?? '—' }}</td>
            </tr>
          </tbody>
        </v-table>
        <div v-if="r.indicacionesGenerales" class="text-body-2 mt-2">{{ r.indicacionesGenerales }}</div>
      </v-card-text>
    </v-card>
    <div v-if="!recetas.length" class="text-medium-emphasis text-body-2">Sin recetas.</div>

    <v-dialog v-model="dialog" max-width="980" scrollable>
      <v-card>
        <v-card-title class="pt-4">{{ editando ? 'Editar receta' : 'Nueva receta' }}</v-card-title>
        <v-card-text>
          <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
          <div v-for="(it, idx) in items" :key="idx" class="border rounded pa-3 mb-3">
            <v-row dense>
              <v-col cols="12" md="4"><v-text-field v-model="it.medicamento" label="Medicamento" /></v-col>
              <v-col cols="6" md="4"><v-text-field v-model="it.concentracion" label="Concentración (p. ej. 500 mg)" /></v-col>
              <v-col cols="6" md="4"><v-text-field v-model="it.presentacion" label="Presentación (p. ej. tabletas)" /></v-col>
              <v-col cols="6" md="3"><v-text-field v-model="it.dosis" label="Dosis" /></v-col>
              <v-col cols="6" md="3"><v-text-field v-model="it.via" label="Vía" /></v-col>
              <v-col cols="6" md="3"><v-text-field v-model="it.frecuencia" label="Frecuencia" /></v-col>
              <v-col cols="6" md="3"><v-text-field v-model="it.duracion" label="Duración" /></v-col>
              <v-col cols="4" md="2"><v-text-field v-model.number="it.cantidad" label="Cantidad" type="number" /></v-col>
              <v-col cols="8" md="9"><v-text-field v-model="it.indicaciones" label="Indicaciones" /></v-col>
              <v-col cols="12" md="1" class="d-flex align-center">
                <v-btn icon="mdi-delete-outline" variant="text" color="error" :disabled="items.length === 1" title="Quitar ítem"
                  @click="items.splice(idx, 1)" />
              </v-col>
            </v-row>
          </div>
          <v-btn size="small" variant="tonal" prepend-icon="mdi-plus" class="mb-4" @click="items.push(vacio())">Agregar medicamento</v-btn>
          <v-row dense>
            <v-col cols="12" md="6"><v-text-field v-model="validaHasta" label="Válida hasta" type="date" /></v-col>
            <v-col cols="12" md="6" class="d-flex align-center"><v-switch v-model="controlada" label="Medicamento controlado" color="primary" hide-details /></v-col>
            <v-col cols="12"><v-textarea v-model="indicaciones" label="Indicaciones generales" rows="2" auto-grow /></v-col>
          </v-row>
        </v-card-text>
        <v-card-actions class="px-4 pb-4">
          <v-spacer />
          <v-btn variant="text" @click="dialog = false">Cancelar</v-btn>
          <v-btn color="primary" variant="flat" :loading="saving" @click="guardar">Guardar receta</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    <ConfirmDialog :model-value="!!anular" title="Anular receta" message="La receta quedará en estado CANCELADA. ¿Desea continuar?"
      confirm-text="Anular" @update:model-value="!$event && (anular = null)" @confirm="anularReceta" />
  </div>
</template>
