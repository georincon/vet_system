<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { contenidoArchivo } from '@/api/archivos'
import { useUiStore } from '@/stores/ui'
import type { ArchivoInfo } from '@/types/etapa'
import { errorText } from '@/utils/errors'
import { abrirPdf, guardarBlob, tamanoLegible } from '@/utils/descarga'
import { etiquetaInstante } from '@/utils/fecha'

const props = defineProps<{ modelValue: boolean; archivo: ArchivoInfo | null; archivos?: ArchivoInfo[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const ui = useUiStore()

const actual = ref<ArchivoInfo | null>(null)
const url = ref<string | null>(null)
const cargando = ref(false)
const zoom = ref(1)
const ajustar = ref(true)

const esImagen = computed(() => !!actual.value && /^image\/(jpeg|png|webp)$/.test(actual.value.contentType))
const esPdf = computed(() => actual.value?.contentType === 'application/pdf')
const lista = computed(() => props.archivos ?? [])
const indice = computed(() => lista.value.findIndex((a) => a.id === actual.value?.id))

function liberar() {
  if (url.value) { URL.revokeObjectURL(url.value); url.value = null }
}

async function cargar() {
  liberar()
  zoom.value = 1
  ajustar.value = true
  const a = actual.value
  if (!a || !props.modelValue) return
  if (!/^image\/(jpeg|png|webp)$/.test(a.contentType)) return
  cargando.value = true
  try {
    url.value = URL.createObjectURL(await contenidoArchivo(a.id))
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo cargar la imagen'))
  } finally {
    cargando.value = false
  }
}

watch(() => [props.modelValue, props.archivo] as const, ([abierto, a]) => {
  if (abierto) { actual.value = a; void cargar() } else liberar()
}, { immediate: true })
onBeforeUnmount(liberar)

function ir(delta: number) {
  const i = indice.value + delta
  if (i < 0 || i >= lista.value.length) return
  actual.value = lista.value[i]
  void cargar()
}
const acercar = (f: number) => {
  ajustar.value = false
  zoom.value = Math.min(8, Math.max(0.1, +(zoom.value * f).toFixed(3))) }

function teclas(e: KeyboardEvent) {
  if (e.key === '+' || e.key === '=') acercar(1.25)
  else if (e.key === '-') acercar(0.8)
  else if (e.key === '0') { zoom.value = 1; ajustar.value = true }
  else if (e.key === 'ArrowRight') ir(1)
  else if (e.key === 'ArrowLeft') ir(-1)
}
function rueda(e: WheelEvent) {
  if (!e.ctrlKey) return
  e.preventDefault()
  acercar(e.deltaY < 0 ? 1.1 : 0.9)
}

async function descargar() {
  if (!actual.value) return
  try { guardarBlob(await contenidoArchivo(actual.value.id), actual.value.nombreOriginal) }
  catch (e) { ui.showError(errorText(e, 'No se pudo descargar el archivo')) }
}
async function verPdf() {
  if (!actual.value) return
  try { await abrirPdf(`/archivos/${actual.value.id}/contenido`, actual.value.nombreOriginal) }
  catch (e) { ui.showError(errorText(e, 'No se pudo abrir el PDF')) }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="1100" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card v-if="actual" tabindex="0" @keydown="teclas">
      <v-card-title class="pt-4 d-flex align-center ga-2">
        <span class="flex-grow-1 text-truncate">{{ actual.nombreOriginal }}</span>
        <v-btn icon="mdi-close" variant="text" aria-label="Cerrar visor" @click="emit('update:modelValue', false)" />
      </v-card-title>
      <v-card-text>
        <div v-if="esImagen" class="d-flex flex-wrap align-center ga-2 mb-2">
          <v-btn size="small" variant="tonal" icon="mdi-magnify-minus-outline" aria-label="Alejar" @click="acercar(0.8)" />
          <v-btn size="small" variant="tonal" icon="mdi-magnify-plus-outline" aria-label="Acercar" @click="acercar(1.25)" />
          <v-btn size="small" variant="tonal" prepend-icon="mdi-fit-to-screen-outline" @click="zoom = 1; ajustar = true">Ajustar</v-btn>
          <v-btn size="small" variant="tonal" @click="zoom = 1; ajustar = false">100 %</v-btn>
          <span class="text-caption text-medium-emphasis">Zoom {{ Math.round(zoom * 100) }} % · Ctrl + rueda, teclas + / - / 0, flechas para navegar</span>
        </div>
        <div v-if="esImagen" class="vet-visor" @wheel="rueda">
          <v-progress-circular v-if="cargando" indeterminate color="white" />
          <img v-else-if="url" :src="url" :alt="actual.descripcion || actual.nombreOriginal" class="vet-arch-visor-img" :class="{ 'vet-arch-visor-ajuste': ajustar }"
            :style="{ transform: `scale(${zoom})` }">
        </div>
        <v-alert v-else-if="esPdf" type="info" variant="tonal" icon="mdi-file-pdf-box">
          Documento PDF. <v-btn variant="text" color="primary" prepend-icon="mdi-open-in-new" @click="verPdf">Abrir en pestaña nueva</v-btn>
        </v-alert>
        <v-alert v-else type="warning" variant="tonal" icon="mdi-radiology-box-outline">
          {{ actual.tipo === 'DICOM' || actual.nombreOriginal.toLowerCase().endsWith('.dcm') ? 'Visor DICOM no disponible.' : 'Vista previa no disponible para este tipo de archivo.' }}
          Puede descargarlo.
        </v-alert>
        <div class="vet-info-bar mt-3 text-body-2">
          <div><strong>Tipo:</strong> {{ actual.tipo }} · <strong>Tamaño:</strong> {{ tamanoLegible(actual.tamanoBytes) }}
            <template v-if="actual.anchoPx"> · <strong>Dimensiones:</strong> {{ actual.anchoPx }} × {{ actual.altoPx }} px</template></div>
          <div><strong>Subido por:</strong> {{ actual.subidoPorNombre ?? '—' }} · {{ etiquetaInstante(actual.subidoEn) }}</div>
          <div v-if="actual.descripcion"><strong>Descripción:</strong> {{ actual.descripcion }}</div>
          <div v-if="actual.hashSha256"><strong>SHA-256:</strong> <span class="vet-arch-hash" :title="actual.hashSha256">{{ actual.hashSha256.slice(0, 16) }}…</span></div>
        </div>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-btn v-if="lista.length > 1" variant="text" prepend-icon="mdi-chevron-left" :disabled="indice <= 0" @click="ir(-1)">Anterior</v-btn>
        <v-btn v-if="lista.length > 1" variant="text" append-icon="mdi-chevron-right" :disabled="indice >= lista.length - 1" @click="ir(1)">Siguiente</v-btn>
        <v-spacer />
        <v-btn color="primary" variant="flat" prepend-icon="mdi-download" @click="descargar">Descargar</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
