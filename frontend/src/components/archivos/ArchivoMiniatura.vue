<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { miniaturaArchivo } from '@/api/archivos'
import type { ArchivoInfo } from '@/types/etapa'

const props = withDefaults(defineProps<{ archivo: ArchivoInfo; tam?: number }>(), { tam: 120 })

const url = ref<string | null>(null)
const fallo = ref(false)

const icono = computed(() => {
  const n = props.archivo.nombreOriginal.toLowerCase()
  if (props.archivo.contentType === 'application/pdf' || n.endsWith('.pdf')) return 'mdi-file-pdf-box'
  if (n.endsWith('.dcm') || props.archivo.tipo === 'DICOM') return 'mdi-radiology-box-outline'
  if (props.archivo.contentType.startsWith('text/') || n.endsWith('.txt')) return 'mdi-file-document-outline'
  return 'mdi-file-image-outline'
})

function liberar() {
  if (url.value) { URL.revokeObjectURL(url.value); url.value = null }
}

async function cargar() {
  liberar()
  fallo.value = false
  if (!props.archivo.tieneMiniatura) return
  try {
    url.value = URL.createObjectURL(await miniaturaArchivo(props.archivo.id))
  } catch {
    fallo.value = true
  }
}

watch(() => props.archivo.id, cargar, { immediate: true })
onBeforeUnmount(liberar)
</script>

<template>
  <img v-if="url" :src="url" :alt="archivo.descripcion || archivo.nombreOriginal" class="vet-miniatura vet-arch-mini"
    :width="tam" :height="tam">
  <div v-else class="vet-miniatura vet-arch-mini vet-arch-icono" role="img" :aria-label="`Archivo ${archivo.nombreOriginal}`">
    <v-icon :icon="fallo ? 'mdi-image-broken-variant' : icono" size="40" />
  </div>
</template>
