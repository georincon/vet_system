<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { listPlantillasMensaje, savePlantillaMensaje } from '@/api/comunicaciones'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { CANALES, type Plantilla } from '@/types/fase3'

const props = defineProps<{ puedeEditar: boolean }>()
const ui = useUiStore()
const items = ref<Plantilla[]>([])
const loading = ref(false)

const VARIABLES = ['tutor', 'paciente', 'vacuna', 'fecha', 'clinica', 'hora', 'sede']
const EJEMPLO: Record<string, string> = {
  tutor: 'María Pérez', paciente: 'Firulais', vacuna: 'Antirrábica canina', fecha: '15 nov 2026',
  clinica: 'Clínica Veterinaria', hora: '10:30', sede: 'Clínica Veterinaria',
}

const dialog = ref(false)
const editId = ref<number | null>(null)
const guardando = ref(false)
const error = ref('')
const form = reactive({ codigo: '', canal: 'EMAIL' as Plantilla['canal'], idioma: 'es', asunto: '', cuerpo: '', activo: true })
const campo = ref<'asunto' | 'cuerpo'>('cuerpo')
const cuerpoRef = ref<{ $el: HTMLElement } | null>(null)

function render(t: string) {
  return t.replace(/\{\{\s*(\w+)\s*\}\}/g, (m, k: string) => EJEMPLO[k] ?? `⚠ ${m}`)
}
const desconocidas = computed(() => {
  const set = new Set<string>()
  for (const m of `${form.asunto} ${form.cuerpo}`.matchAll(/\{\{\s*(\w+)\s*\}\}/g)) if (!VARIABLES.includes(m[1])) set.add(m[1])
  return [...set]
})

function insertar(v: string) {
  const token = `{{${v}}}`
  const el = campo.value === 'cuerpo' ? cuerpoRef.value?.$el.querySelector('textarea') : document.getElementById('plantilla-asunto') as HTMLInputElement | null
  if (el && typeof el.selectionStart === 'number') {
    const s = el.selectionStart, e = el.selectionEnd ?? s
    form[campo.value] = form[campo.value].slice(0, s) + token + form[campo.value].slice(e)
    const pos = s + token.length
    requestAnimationFrame(() => { el.focus(); el.setSelectionRange(pos, pos) })
  } else form[campo.value] += token
}

async function cargar() {
  loading.value = true
  try { items.value = await listPlantillasMensaje() } catch (e) { ui.showError(errorText(e, 'No se pudieron cargar las plantillas')) } finally { loading.value = false }
}
function abrir(p?: Plantilla) {
  editId.value = p?.id ?? null
  Object.assign(form, p ? { codigo: p.codigo, canal: p.canal, idioma: p.idioma, asunto: p.asunto ?? '', cuerpo: p.cuerpo, activo: p.activo }
    : { codigo: '', canal: 'EMAIL', idioma: 'es', asunto: '', cuerpo: '', activo: true })
  error.value = ''
  dialog.value = true
}
async function guardar() {
  if (!form.codigo.trim() || !form.cuerpo.trim()) { error.value = 'Código y cuerpo son obligatorios'; return }
  guardando.value = true
  try {
    await savePlantillaMensaje(editId.value, { ...form, codigo: form.codigo.trim(), asunto: form.asunto })
    ui.showSuccess('Plantilla guardada')
    dialog.value = false
    await cargar()
  } catch (e) { error.value = errorText(e, 'No se pudo guardar la plantilla') } finally { guardando.value = false }
}
defineExpose({ recargar: cargar })
onMounted(cargar)
</script>

<template>
  <div>
    <div class="d-flex mb-2">
      <v-spacer />
      <v-btn v-if="props.puedeEditar" color="primary" variant="flat" prepend-icon="mdi-plus" @click="abrir()">Nueva plantilla</v-btn>
    </div>
    <v-data-table :loading="loading" :items="items" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin plantillas"
      :headers="[
        { title: 'Código', key: 'codigo' }, { title: 'Canal', key: 'canal' }, { title: 'Asunto / cuerpo', key: 'cuerpo', sortable: false },
        { title: 'Estado', key: 'activo' }, { title: '', key: 'acciones', sortable: false, align: 'end' },
      ]">
      <template #item.codigo="{ item }"><span class="font-weight-medium">{{ item.codigo }}</span><div class="text-caption text-medium-emphasis">{{ item.idioma }}</div></template>
      <template #item.cuerpo="{ item }">
        <div v-if="item.asunto" class="font-weight-medium">{{ item.asunto }}</div>
        <div class="text-body-2 text-medium-emphasis text-truncate" style="max-width: 420px">{{ item.cuerpo }}</div>
      </template>
      <template #item.activo="{ item }"><v-chip size="small" variant="tonal" :color="item.activo ? 'success' : 'grey'" :text="item.activo ? 'Activa' : 'Inactiva'" /></template>
      <template #item.acciones="{ item }">
        <v-btn size="small" variant="text" :icon="props.puedeEditar ? 'mdi-pencil' : 'mdi-eye-outline'" :title="props.puedeEditar ? 'Editar' : 'Ver'" @click="abrir(item)" />
      </template>
    </v-data-table>

    <v-dialog v-model="dialog" max-width="900" scrollable>
      <v-card>
        <v-card-title class="pt-4">{{ editId ? (props.puedeEditar ? 'Editar plantilla' : 'Plantilla') : 'Nueva plantilla' }}</v-card-title>
        <v-card-text>
          <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
          <v-row>
            <v-col cols="12" md="7">
              <v-row dense>
                <v-col cols="12" sm="5"><v-text-field v-model="form.codigo" label="Código" :readonly="!props.puedeEditar" /></v-col>
                <v-col cols="6" sm="4"><v-select v-model="form.canal" :items="CANALES" label="Canal" :readonly="!props.puedeEditar" /></v-col>
                <v-col cols="6" sm="3"><v-text-field v-model="form.idioma" label="Idioma" :readonly="!props.puedeEditar" /></v-col>
              </v-row>
              <v-text-field id="plantilla-asunto" v-model="form.asunto" label="Asunto" :readonly="!props.puedeEditar" @focus="campo = 'asunto'" />
              <v-textarea ref="cuerpoRef" v-model="form.cuerpo" label="Cuerpo" rows="6" auto-grow :readonly="!props.puedeEditar" @focus="campo = 'cuerpo'" />
              <div v-if="props.puedeEditar" class="mb-2">
                <div class="text-caption text-medium-emphasis mb-1">Insertar variable en {{ campo === 'asunto' ? 'el asunto' : 'el cuerpo' }}:</div>
                <div class="d-flex flex-wrap ga-1">
                  <v-chip v-for="v in VARIABLES" :key="v" size="small" variant="outlined" color="primary" :text="`{{${v}}}`" @click="insertar(v)" />
                </div>
              </div>
              <v-alert v-if="desconocidas.length" type="warning" variant="tonal" density="compact" :text="`Variables desconocidas (el servidor las rechazará): ${desconocidas.join(', ')}`" />
              <v-switch v-model="form.activo" label="Plantilla activa" color="primary" hide-details :readonly="!props.puedeEditar" />
            </v-col>
            <v-col cols="12" md="5">
              <h3 class="vet-section-title">Vista previa</h3>
              <div class="vet-info-bar">
                <div class="font-weight-bold mb-2">{{ render(form.asunto) || '(sin asunto)' }}</div>
                <div style="white-space: pre-wrap">{{ render(form.cuerpo) || '—' }}</div>
              </div>
              <div class="text-caption text-medium-emphasis mt-2">Con datos de ejemplo. <code v-pre>{{vacuna}}</code> es la vacuna o el antiparasitario; <code v-pre>{{hora}}</code> solo aplica a citas. Los correos añaden al final un enlace de baja.</div>
            </v-col>
          </v-row>
        </v-card-text>
        <v-card-actions class="px-4 pb-4">
          <v-spacer /><v-btn variant="text" @click="dialog = false">{{ props.puedeEditar ? 'Cancelar' : 'Cerrar' }}</v-btn>
          <v-btn v-if="props.puedeEditar" color="primary" variant="flat" :loading="guardando" @click="guardar">Guardar</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
