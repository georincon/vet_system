<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { importarArchivo, importarJson, rutaPlantillaCsv } from '@/api/laboratorio'
import { useUiStore } from '@/stores/ui'
import { descargar } from '@/utils/descarga'
import { errorText } from '@/utils/errors'
import BanderaChip from './BanderaChip.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import type { FormatoImportacion, ImportacionResultado } from '@/types/etapa'

const props = defineProps<{ modelValue: boolean; ordenId?: number | null; ordenCodigo?: string }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; importado: [] }>()
const ui = useUiStore()

const archivo = ref<File | null>(null)
const texto = ref('')
const formato = ref<FormatoImportacion>('CSV')
const ordenCodigo = ref('')
const resultado = ref<ImportacionResultado | null>(null)
const simulado = ref(false)
const trabajando = ref(false)
const error = ref('')
const confirmar = ref(false)

watch(() => props.modelValue, (v) => {
  if (!v) return
  archivo.value = null; texto.value = ''; resultado.value = null; simulado.value = false; error.value = ''
  ordenCodigo.value = props.ordenCodigo ?? ''
})

const FORMATOS = [{ title: 'CSV', value: 'CSV' }, { title: 'HL7 v2 (ORU^R01)', value: 'HL7' }, { title: 'JSON', value: 'JSON' }]

function alElegir(f: File | File[] | null) {
  const a = Array.isArray(f) ? f[0] ?? null : f
  archivo.value = a
  resultado.value = null; simulado.value = false
  if (!a) return
  const n = a.name.toLowerCase()
  formato.value = n.endsWith('.json') ? 'JSON' : n.endsWith('.hl7') ? 'HL7' : n.endsWith('.csv') ? 'CSV' : formato.value
}

const hayEntrada = computed(() => !!archivo.value || texto.value.trim().length > 0)

async function ejecutar(dryRun: boolean) {
  error.value = ''
  if (!hayEntrada.value) { error.value = 'Seleccione un archivo o pegue el contenido'; return }
  trabajando.value = true
  try {
    const codigo = ordenCodigo.value.trim() || undefined
    if (archivo.value) {
      resultado.value = await importarArchivo({ archivo: archivo.value, formato: formato.value, ordenId: props.ordenId, ordenCodigo: codigo, dryRun })
    } else {
      resultado.value = await importarJson(formato.value === 'JSON'
        ? { formato: 'JSON', ordenId: props.ordenId, ordenCodigo: codigo, dryRun, resultados: parseJson(texto.value) }
        : { formato: formato.value, contenido: texto.value, ordenId: props.ordenId, ordenCodigo: codigo, dryRun })
    }
    simulado.value = dryRun
    if (!dryRun) {
      ui.showSuccess(`Importados ${resultado.value.importados} resultado(s) como BORRADOR`)
      emit('importado')
    }
  } catch (e) { error.value = errorText(e, 'No se pudo importar') } finally { trabajando.value = false; confirmar.value = false }
}

function parseJson(t: string) {
  const v: unknown = JSON.parse(t)
  const lista = Array.isArray(v) ? v : (v as { resultados?: unknown }).resultados
  return lista as import('@/types/etapa').LineaJson[]
}

async function plantilla() {
  try { await descargar(rutaPlantillaCsv, 'plantilla-resultados.csv') } catch (e) { ui.showError(errorText(e, 'No se pudo descargar la plantilla')) }
}

function importarClick() { if (simulado.value) void ejecutar(false); else confirmar.value = true }
const OK = new Set(['IMPORTABLE', 'IMPORTADO'])
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="1000" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">Importar resultados</v-card-title>
      <v-card-text>
        <v-alert type="info" variant="tonal" density="compact" class="mb-3" icon="mdi-information-outline"
          text="Los resultados importados entran como BORRADOR: un veterinario debe revisarlos y validarlos. Use 'Simulacro' para ver qué se importaría sin guardar nada." />
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-row dense>
          <v-col cols="12" md="6">
            <v-file-input label="Archivo (.hl7, .txt, .csv, .json; máx. 2 MB)" accept=".hl7,.txt,.csv,.json" prepend-icon="" prepend-inner-icon="mdi-paperclip"
              clearable @update:model-value="alElegir" />
          </v-col>
          <v-col cols="6" md="3"><v-select v-model="formato" :items="FORMATOS" label="Formato" /></v-col>
          <v-col cols="6" md="3"><v-text-field v-model="ordenCodigo" label="Código de orden (opcional)" :disabled="!!ordenId" /></v-col>
          <v-col v-if="!archivo" cols="12"><v-textarea v-model="texto" label="…o pegue el contenido" rows="4" auto-grow /></v-col>
        </v-row>
        <v-btn v-if="formato === 'CSV'" size="small" variant="text" prepend-icon="mdi-download" @click="plantilla">Descargar plantilla CSV</v-btn>

        <div v-if="resultado" class="mt-3" aria-live="polite">
          <h3 class="vet-section-title">{{ resultado.dryRun ? 'Simulacro (no se guardó nada)' : 'Resultado de la importación' }}</h3>
          <div class="d-flex flex-wrap ga-2 mb-2">
            <v-chip variant="tonal" :text="`Total: ${resultado.total}`" />
            <v-chip color="success" variant="tonal" :text="`${resultado.dryRun ? 'Importables' : 'Importados'}: ${resultado.dryRun ? resultado.lineas.filter((l) => l.estado === 'IMPORTABLE').length : resultado.importados}`" />
            <v-chip color="warning" variant="tonal" :text="`No coinciden: ${resultado.noCoinciden}`" />
            <v-chip color="error" variant="tonal" :text="`Rechazados: ${resultado.rechazados}`" />
          </div>
          <v-alert v-for="(a, i) in resultado.advertencias" :key="i" type="warning" variant="tonal" density="compact" class="mb-1" :text="a" />
          <div v-if="resultado.ordenIds.length" class="mb-2">
            Órdenes afectadas:
            <router-link v-for="id in resultado.ordenIds" :key="id" class="mr-2" :to="{ name: 'orden-lab', params: { id } }" @click="emit('update:modelValue', false)">#{{ id }}</router-link>
          </div>
          <v-table density="compact" class="vet-tabla-resultados">
            <thead><tr><th>Línea</th><th>Estado</th><th>Examen</th><th>Parámetro</th><th>Valor</th><th>Bandera</th><th>Mensaje</th></tr></thead>
            <tbody>
              <tr v-for="l in resultado.lineas" :key="l.linea" :class="{ 'vet-fila-critica': l.critico, 'vet-fila-alerta': !OK.has(l.estado) }">
                <td>{{ l.linea }}</td>
                <td><v-chip size="x-small" variant="tonal" :color="OK.has(l.estado) ? 'success' : 'warning'" :prepend-icon="OK.has(l.estado) ? 'mdi-check' : 'mdi-alert-outline'" :text="l.estado" /></td>
                <td>{{ l.examenCodigo ?? '—' }}</td>
                <td>{{ l.parametro ?? l.parametroCodigo ?? l.loinc ?? '—' }}</td>
                <td>{{ l.valor ?? '—' }} {{ l.unidad ?? '' }}</td>
                <td><BanderaChip :bandera="l.bandera" :critico="!!l.critico" /></td>
                <td class="text-wrap">{{ l.mensaje ?? '' }}</td>
              </tr>
            </tbody>
          </v-table>
        </div>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cerrar</v-btn>
        <v-btn variant="tonal" prepend-icon="mdi-flask-outline" :loading="trabajando" :disabled="!hayEntrada" @click="ejecutar(true)">Simulacro</v-btn>
        <v-btn color="primary" variant="flat" prepend-icon="mdi-upload" :loading="trabajando" :disabled="!hayEntrada || (simulado && resultado?.dryRun && !resultado.lineas.some((l) => l.estado === 'IMPORTABLE'))" @click="importarClick">Importar</v-btn>
      </v-card-actions>
    </v-card>
    <ConfirmDialog v-model="confirmar" title="Importar sin simulacro" color="primary" confirm-text="Importar" :loading="trabajando"
      message="No ha ejecutado el simulacro. Se importará directamente y los resultados quedarán como BORRADOR. ¿Continuar?" @confirm="ejecutar(false)" />
  </v-dialog>
</template>
