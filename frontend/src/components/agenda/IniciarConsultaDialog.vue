<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { listPlantillas, iniciarConsulta } from '@/api/consultas'
import { useCatalogosStore } from '@/stores/catalogos'
import { errorText } from '@/utils/errors'
import { TIPOS_CONSULTA } from '@/utils/clinica'
import type { Cita, Consulta, PlantillaSoap, TipoConsulta } from '@/types'

const props = defineProps<{ modelValue: boolean; cita: Cita | null }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; iniciada: [c: Consulta] }>()

const catalogos = useCatalogosStore()
const plantillas = ref<PlantillaSoap[]>([])
const plantillaId = ref<number | null>(null)
const tipo = ref<TipoConsulta | null>(null)
const loading = ref(false)
const error = ref('')

const especieId = computed(
  () => catalogos.especies.find((e) => e.nombre === props.cita?.paciente.especie)?.id ?? null,
)
const opciones = computed(() =>
  plantillas.value
    .filter((p) => p.especieId == null || p.especieId === especieId.value)
    .map((p) => ({ title: p.nombre + (p.especieId == null ? ' (general)' : ''), value: p.id })),
)

watch(
  () => props.modelValue,
  async (abierto) => {
    if (!abierto) return
    error.value = ''
    plantillaId.value = null
    tipo.value = null
    try {
      await catalogos.ensure()
      plantillas.value = await listPlantillas()
    } catch (e) {
      error.value = errorText(e, 'No se pudieron cargar las plantillas')
    }
  },
)

async function iniciar() {
  if (!props.cita) return
  loading.value = true
  error.value = ''
  try {
    const consulta = await iniciarConsulta(props.cita.id, {
      plantillaId: plantillaId.value ?? undefined,
      tipoConsulta: tipo.value ?? undefined,
    })
    emit('iniciada', consulta)
    emit('update:modelValue', false)
  } catch (e) {
    error.value = errorText(e, 'No se pudo iniciar la consulta')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="520" @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">Iniciar consulta</v-card-title>
      <v-card-text>
        <p v-if="cita" class="mb-4">
          Paciente <strong>{{ cita.paciente.nombre }}</strong> ({{ cita.paciente.especie }}) · cita {{ cita.codigo }}.
          La cita pasará a <em>En consulta</em>.
        </p>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-select v-model="tipo" label="Tipo de consulta" :items="TIPOS_CONSULTA" clearable class="mb-3" />
        <v-select
          v-model="plantillaId"
          label="Plantilla SOAP (opcional)"
          :items="opciones"
          clearable
          no-data-text="Sin plantillas para esta especie"
          hint="Precarga Subjetivo, Objetivo, Análisis y Plan"
          persistent-hint
        />
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn color="primary" variant="flat" prepend-icon="mdi-stethoscope" :loading="loading" @click="iniciar">
          Iniciar consulta
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
