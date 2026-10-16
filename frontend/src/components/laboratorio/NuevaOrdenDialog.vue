<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { crearOrden, listExamenes } from '@/api/laboratorio'
import PacienteSelect from '@/components/agenda/PacienteSelect.vue'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { PRIORIDADES, type ExamenCatalogo, type OrdenDetalle, type PrioridadOrden, type TipoExamen } from '@/types/etapa'
import type { PacienteResumen } from '@/types'

const props = defineProps<{
  modelValue: boolean
  pacienteId?: number | null
  pacienteInicial?: PacienteResumen | null
  consultaId?: number | null
  tipos?: TipoExamen[]
  titulo?: string
}>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; creada: [orden: OrdenDetalle] }>()
const ui = useUiStore()

const catalogo = ref<ExamenCatalogo[]>([])
const cargando = ref(false)
const pacienteSel = ref<number | null>(null)
const busqueda = ref('')
const seleccion = ref<number[]>([])
const prioridad = ref<PrioridadOrden>('RUTINA')
const indicacion = ref('')
const externo = ref('')
const guardando = ref(false)
const error = ref('')

const pacienteFinal = computed(() => props.pacienteId ?? pacienteSel.value)

const visibles = computed(() => {
  const q = busqueda.value.trim().toLowerCase()
  return catalogo.value.filter((e) => (!props.tipos?.length || props.tipos.includes(e.tipo))
    && (!q || e.nombre.toLowerCase().includes(q) || e.codigo.toLowerCase().includes(q)))
})
const grupos = computed(() => {
  const m = new Map<string, ExamenCatalogo[]>()
  for (const e of visibles.value) {
    const k = e.categoria || e.tipo
    m.set(k, [...(m.get(k) ?? []), e])
  }
  return [...m.entries()].sort((a, b) => a[0].localeCompare(b[0]))
})
const elegidos = computed(() => catalogo.value.filter((e) => seleccion.value.includes(e.id)))

async function cargar() {
  cargando.value = true
  try { catalogo.value = await listExamenes({ soloActivos: true }) }
  catch (e) { error.value = errorText(e, 'No se pudo cargar el catálogo de exámenes') }
  finally { cargando.value = false }
}

watch(() => props.modelValue, (v) => {
  if (!v) return
  pacienteSel.value = props.pacienteId ?? null
  busqueda.value = ''; seleccion.value = []; prioridad.value = 'RUTINA'; indicacion.value = ''; externo.value = ''; error.value = ''
  if (!catalogo.value.length) void cargar()
})

function alternar(id: number) {
  seleccion.value = seleccion.value.includes(id) ? seleccion.value.filter((x) => x !== id) : [...seleccion.value, id]
}

async function guardar() {
  error.value = ''
  if (!pacienteFinal.value) { error.value = 'Seleccione el paciente'; return }
  if (!seleccion.value.length) { error.value = 'Seleccione al menos un examen'; return }
  guardando.value = true
  try {
    const o = await crearOrden({
      pacienteId: pacienteFinal.value, consultaId: props.consultaId ?? undefined, prioridad: prioridad.value,
      indicacionClinica: indicacion.value.trim() || undefined, laboratorioExterno: externo.value.trim() || undefined,
      examenIds: seleccion.value,
    })
    ui.showSuccess(`Orden ${o.codigo} creada`)
    emit('update:modelValue', false)
    emit('creada', o)
  } catch (e) { error.value = errorText(e, 'No se pudo crear la orden') } finally { guardando.value = false }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="860" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">{{ titulo ?? 'Nueva orden de exámenes' }}</v-card-title>
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <PacienteSelect v-if="!pacienteId" v-model="pacienteSel" :inicial="pacienteInicial" class="mb-3" />
        <v-row dense>
          <v-col cols="12" sm="4">
            <v-select v-model="prioridad" :items="PRIORIDADES" item-title="title" item-value="value" label="Prioridad" />
          </v-col>
          <v-col cols="12" sm="8"><v-text-field v-model="externo" label="Laboratorio externo (opcional)" maxlength="150" /></v-col>
          <v-col cols="12"><v-textarea v-model="indicacion" label="Indicación clínica" rows="2" auto-grow /></v-col>
        </v-row>

        <h3 class="vet-section-title mt-2">Exámenes</h3>
        <v-text-field v-model="busqueda" label="Buscar examen o panel" prepend-inner-icon="mdi-magnify" clearable class="mb-2" />
        <div v-if="elegidos.length" class="d-flex flex-wrap ga-1 mb-2" aria-live="polite">
          <v-chip v-for="e in elegidos" :key="e.id" closable color="primary" variant="tonal" :text="e.nombre" @click:close="alternar(e.id)" />
        </div>
        <v-progress-linear v-if="cargando" indeterminate color="primary" />
        <div v-for="[cat, lista] in grupos" :key="cat" class="mb-3">
          <div class="text-caption font-weight-bold text-medium-emphasis mb-1">{{ cat }}</div>
          <v-row dense>
            <v-col v-for="e in lista" :key="e.id" cols="12" sm="6">
              <v-checkbox :model-value="seleccion.includes(e.id)" density="compact" hide-details @update:model-value="alternar(e.id)">
                <template #label>
                  <div>
                    <div>{{ e.nombre }} <span class="text-caption text-medium-emphasis">({{ e.codigo }})</span></div>
                    <div class="text-caption text-medium-emphasis">
                      <template v-if="e.esPanel">Panel · </template>{{ e.muestra ?? 'Sin muestra' }}<template v-if="e.requiereAyuno"> · requiere ayuno</template>
                    </div>
                  </div>
                </template>
              </v-checkbox>
            </v-col>
          </v-row>
        </div>
        <div v-if="!cargando && !grupos.length" class="text-medium-emphasis">Sin exámenes que coincidan.</div>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn color="primary" variant="flat" :loading="guardando" @click="guardar">Crear orden</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
