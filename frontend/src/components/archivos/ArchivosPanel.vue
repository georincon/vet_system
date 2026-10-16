<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { listArchivosPaciente } from '@/api/archivos'
import GaleriaArchivos from '@/components/archivos/GaleriaArchivos.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { TIPOS_ARCHIVO, type ArchivoInfo, type TipoArchivo } from '@/types/etapa'
import { tamanoLegible } from '@/utils/descarga'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'

const props = defineProps<{ pacienteId: number }>()
const emit = defineEmits<{ cambio: [] }>()
const auth = useAuthStore()
const ui = useUiStore()

const archivos = ref<ArchivoInfo[]>([])
const tipo = ref<TipoArchivo | null>(null)
const incluirEliminados = ref(false)
const loading = ref(false)

const activos = computed(() => archivos.value.filter((a) => a.activo))
const eliminados = computed(() => archivos.value.filter((a) => !a.activo))
const puedeVerEliminados = computed(() => auth.can.archivosEliminar)
const vinculados = computed(() => activos.value.filter((a) => a.ordenExamenId || a.cirugiaId || a.consultaId))

async function cargar() {
  loading.value = true
  try {
    archivos.value = await listArchivosPaciente(props.pacienteId, tipo.value, incluirEliminados.value && puedeVerEliminados.value)
  } catch (e) {
    ui.showError(errorText(e, 'No se pudieron cargar los archivos'))
  } finally {
    loading.value = false
  }
}
function cambio() { void cargar(); emit('cambio') }

watch([tipo, incluirEliminados, () => props.pacienteId], cargar)
onMounted(cargar)
</script>

<template>
  <div>
    <div class="d-flex flex-wrap align-center ga-3 mb-3">
      <h2 class="vet-section-title flex-grow-1 mb-0">Archivos del paciente <span class="text-caption text-medium-emphasis">({{ activos.length }})</span></h2>
      <v-select v-model="tipo" :items="TIPOS_ARCHIVO" label="Filtrar por tipo" clearable class="vet-filtro" />
      <v-switch v-if="puedeVerEliminados" v-model="incluirEliminados" label="Incluir eliminados" color="primary" hide-details />
    </div>
    <v-progress-linear v-if="loading" indeterminate color="primary" />

    <GaleriaArchivos :archivos="activos" :paciente-id="pacienteId" tipo-subida="OTRO" tipo-seleccionable
      :puede-subir="auth.can.archivosSubir" :puede-eliminar="auth.can.archivosEliminar" @cambio="cambio" />

    <template v-if="vinculados.length">
      <h3 class="vet-section-title mt-6">Vínculos clínicos</h3>
      <v-table density="compact">
        <thead><tr><th>Archivo</th><th>Tipo</th><th>Vinculado a</th><th>Subido</th></tr></thead>
        <tbody>
          <tr v-for="a in vinculados" :key="a.id">
            <td>{{ a.nombreOriginal }}</td>
            <td>{{ a.tipo }}</td>
            <td>
              <router-link v-if="a.ordenExamenId" :to="`/laboratorio/ordenes/${a.ordenExamenId}`">Orden #{{ a.ordenExamenId }}</router-link>
              <router-link v-if="a.cirugiaId" :to="`/procedimientos/${a.cirugiaId}`" class="ml-2">Procedimiento #{{ a.cirugiaId }}</router-link>
              <router-link v-if="a.consultaId" :to="`/consultas/${a.consultaId}`" class="ml-2">Consulta #{{ a.consultaId }}</router-link>
            </td>
            <td>{{ etiquetaInstante(a.subidoEn) }}</td>
          </tr>
        </tbody>
      </v-table>
    </template>

    <template v-if="incluirEliminados && eliminados.length">
      <h3 class="vet-section-title mt-6">Eliminados (baja lógica)</h3>
      <v-table density="compact">
        <thead><tr><th>Archivo</th><th>Tipo</th><th>Tamaño</th><th>Eliminado</th><th>Motivo</th></tr></thead>
        <tbody>
          <tr v-for="a in eliminados" :key="a.id">
            <td>{{ a.nombreOriginal }}</td><td>{{ a.tipo }}</td><td>{{ tamanoLegible(a.tamanoBytes) }}</td>
            <td>{{ etiquetaInstante(a.eliminadoEn) }}</td><td>{{ a.motivoEliminacion ?? '—' }}</td>
          </tr>
        </tbody>
      </v-table>
    </template>
  </div>
</template>
