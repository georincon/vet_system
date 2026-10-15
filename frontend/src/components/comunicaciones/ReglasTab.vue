<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listPlantillasMensaje, listReglas, saveRegla } from '@/api/comunicaciones'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { CANALES, EVENTOS_REGLA, type Plantilla, type Regla } from '@/types/fase3'

const props = defineProps<{ puedeEditar: boolean }>()
const ui = useUiStore()
const items = ref<Regla[]>([])
const plantillas = ref<Plantilla[]>([])
const loading = ref(false)
const dialog = ref(false)
const editId = ref<number | null>(null)
const guardando = ref(false)
const error = ref('')
const form = reactive({ codigo: '', evento: 'VACUNA_PROXIMA', diasOffset: -7, canal: 'EMAIL' as Regla['canal'], plantillaId: null as number | null, activo: true })

function plantillasDelCanal() {
  return plantillas.value.filter((p) => p.canal === form.canal && p.activo).map((p) => ({ title: `${p.codigo} (${p.idioma})`, value: p.id }))
}
function describir(r: Regla) {
  const d = r.diasOffset
  const cuando = d === 0 ? 'el mismo día' : d < 0 ? `${Math.abs(d)} día(s) antes` : `${d} día(s) después`
  return `${cuando} · ${r.evento}`
}
async function cargar() {
  loading.value = true
  try {
    const [r, p] = await Promise.all([listReglas(), listPlantillasMensaje()])
    items.value = r; plantillas.value = p
  } catch (e) { ui.showError(errorText(e, 'No se pudieron cargar las reglas')) } finally { loading.value = false }
}
function abrir(r?: Regla) {
  editId.value = r?.id ?? null
  Object.assign(form, r ? { codigo: r.codigo, evento: r.evento, diasOffset: r.diasOffset, canal: r.canal, plantillaId: r.plantillaId, activo: r.activo }
    : { codigo: '', evento: 'VACUNA_PROXIMA', diasOffset: -7, canal: 'EMAIL', plantillaId: null, activo: true })
  error.value = ''
  dialog.value = true
}
async function guardar() {
  if (!form.codigo.trim() || !form.plantillaId) { error.value = 'Código y plantilla son obligatorios'; return }
  guardando.value = true
  try {
    await saveRegla(editId.value, { ...form, codigo: form.codigo.trim(), plantillaId: form.plantillaId, diasOffset: Number(form.diasOffset) })
    ui.showSuccess('Regla guardada')
    dialog.value = false
    await cargar()
  } catch (e) { error.value = errorText(e, 'No se pudo guardar la regla') } finally { guardando.value = false }
}
defineExpose({ recargar: cargar })
onMounted(cargar)
</script>

<template>
  <div>
    <div class="d-flex mb-2">
      <v-spacer />
      <v-btn v-if="props.puedeEditar" color="primary" variant="flat" prepend-icon="mdi-plus" @click="abrir()">Nueva regla</v-btn>
    </div>
    <v-data-table :loading="loading" :items="items" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin reglas"
      :headers="[
        { title: 'Código', key: 'codigo' }, { title: 'Cuándo', key: 'evento', sortable: false }, { title: 'Canal', key: 'canal' },
        { title: 'Plantilla', key: 'plantillaCodigo' }, { title: 'Estado', key: 'activo' }, { title: '', key: 'acciones', sortable: false, align: 'end' },
      ]">
      <template #item.codigo="{ item }"><span class="font-weight-medium">{{ item.codigo }}</span></template>
      <template #item.evento="{ item }">{{ describir(item) }}</template>
      <template #item.canal="{ item }">
        <v-chip size="small" variant="outlined" :text="item.canal" />
        <v-chip v-if="item.canalDisponible === false" size="x-small" color="warning" variant="tonal" class="ml-1" text="Sin adaptador: se ignora" />
      </template>
      <template #item.activo="{ item }"><v-chip size="small" variant="tonal" :color="item.activo ? 'success' : 'grey'" :text="item.activo ? 'Activa' : 'Inactiva'" /></template>
      <template #item.acciones="{ item }">
        <v-btn v-if="props.puedeEditar" size="small" variant="text" icon="mdi-pencil" title="Editar" @click="abrir(item)" />
      </template>
    </v-data-table>

    <v-dialog v-model="dialog" max-width="560">
      <v-card>
        <v-card-title class="pt-4">{{ editId ? 'Editar regla' : 'Nueva regla' }}</v-card-title>
        <v-card-text>
          <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
          <v-text-field v-model="form.codigo" label="Código" />
          <v-row dense>
            <v-col cols="12" sm="7"><v-select v-model="form.evento" :items="EVENTOS_REGLA" label="Evento" hint="El job procesa VACUNA_PROXIMA, DESPARASITACION_PROXIMA y CITA" persistent-hint /></v-col>
            <v-col cols="12" sm="5"><v-text-field v-model.number="form.diasOffset" type="number" label="Días de desfase" hint="Negativo = antes (-7)" persistent-hint /></v-col>
          </v-row>
          <v-row dense class="mt-1">
            <v-col cols="12" sm="4"><v-select v-model="form.canal" :items="CANALES" label="Canal" @update:model-value="form.plantillaId = null" /></v-col>
            <v-col cols="12" sm="8"><v-select v-model="form.plantillaId" :items="plantillasDelCanal()" label="Plantilla (del mismo canal)" no-data-text="No hay plantillas activas para este canal" /></v-col>
          </v-row>
          <v-alert v-if="form.canal !== 'EMAIL'" type="warning" variant="tonal" density="compact" class="mt-2" text="Solo EMAIL tiene adaptador de envío; las reglas de otros canales se ignoran." />
          <v-switch v-model="form.activo" label="Regla activa" color="primary" hide-details />
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer /><v-btn variant="text" @click="dialog = false">Cancelar</v-btn>
          <v-btn color="primary" variant="flat" :loading="guardando" @click="guardar">Guardar</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
