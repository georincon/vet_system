<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { eliminarArchivo, subirArchivo } from '@/api/archivos'
import MotivoDialog from '@/components/MotivoDialog.vue'
import ArchivoMiniatura from '@/components/archivos/ArchivoMiniatura.vue'
import VisorArchivoDialog from '@/components/archivos/VisorArchivoDialog.vue'
import { useUiStore } from '@/stores/ui'
import {
  EXT_ARCHIVO_PERMITIDAS, MAX_BYTES_ARCHIVO, MAX_BYTES_DICOM, TIPOS_ARCHIVO,
  type ArchivoInfo, type ArchivoVinculo, type TipoArchivo,
} from '@/types/etapa'
import { tamanoLegible } from '@/utils/descarga'
import { errorText } from '@/utils/errors'

const props = withDefaults(defineProps<{
  archivos: ArchivoInfo[]
  pacienteId: number
  vinculo?: ArchivoVinculo
  tipoSubida?: TipoArchivo
  /** Si es true el usuario elige el tipo de cada archivo (por defecto el tipo viene fijado por tipoSubida). */
  tipoSeleccionable?: boolean
  puedeSubir?: boolean
  puedeEliminar?: boolean
  titulo?: string
}>(), { tipoSubida: 'OTRO', tipoSeleccionable: false, puedeSubir: false, puedeEliminar: false })
const emit = defineEmits<{ cambio: [] }>()
const ui = useUiStore()

interface EnCola {
  clave: number
  archivo: File
  tipo: TipoArchivo
  descripcion: string
  previa: string | null
  progreso: number
  subiendo: boolean
  error: string
}

const IMAGENES = ['jpg', 'jpeg', 'png', 'webp', 'dcm']
let contador = 0
const cola = ref<EnCola[]>([])
const arrastrando = ref(false)
const errorValidacion = ref('')
const entrada = ref<HTMLInputElement | null>(null)
const visorAbierto = ref(false)
const visorArchivo = ref<ArchivoInfo | null>(null)
const aEliminar = ref<ArchivoInfo | null>(null)
const eliminando = ref(false)
const idInput = `arch-input-${Math.random().toString(36).slice(2, 8)}`

const activos = computed(() => props.archivos.filter((a) => a.activo))
const accept = EXT_ARCHIVO_PERMITIDAS.map((e) => '.' + e).join(',')
const tiposOpc = TIPOS_ARCHIVO

const extensionDe = (n: string) => (n.includes('.') ? n.split('.').pop()!.toLowerCase() : '')

function validar(f: File, tipo: TipoArchivo): string {
  const ext = extensionDe(f.name)
  if (!EXT_ARCHIVO_PERMITIDAS.includes(ext)) return `"${f.name}": tipo no permitido (use ${EXT_ARCHIVO_PERMITIDAS.join(', ')}).`
  const max = ext === 'dcm' ? MAX_BYTES_DICOM : MAX_BYTES_ARCHIVO
  if (f.size > max) return `"${f.name}" pesa ${tamanoLegible(f.size)} y supera el máximo de ${tamanoLegible(max)}.`
  if (f.size === 0) return `"${f.name}" está vacío.`
  return coherencia(ext, tipo, f.name)
}
function coherencia(ext: string, tipo: TipoArchivo, nombre: string): string {
  if (['IMAGEN', 'RADIOGRAFIA', 'ECOGRAFIA'].includes(tipo) && !IMAGENES.includes(ext)) return `"${nombre}": un archivo de tipo ${tipo} debe ser jpg, png, webp o dcm.`
  if (tipo === 'DICOM' && ext !== 'dcm') return `"${nombre}": el tipo DICOM solo acepta .dcm.`
  if (tipo === 'PDF_LABORATORIO' && ext !== 'pdf') return `"${nombre}": el PDF de laboratorio debe ser .pdf.`
  return ''
}

function tipoPorDefecto(f: File): TipoArchivo {
  if (props.tipoSeleccionable) {
    const ext = extensionDe(f.name)
    if (ext === 'dcm') return 'DICOM'
    if (['jpg', 'jpeg', 'png', 'webp'].includes(ext)) return 'IMAGEN'
    if (ext === 'pdf') return 'INFORME'
  }
  return props.tipoSubida
}

function agregar(lista: FileList | File[]) {
  errorValidacion.value = ''
  const errores: string[] = []
  for (const f of Array.from(lista)) {
    const tipo = tipoPorDefecto(f)
    const err = validar(f, tipo)
    if (err) { errores.push(err); continue }
    cola.value.push({
      clave: ++contador, archivo: f, tipo, descripcion: '', progreso: 0, subiendo: false, error: '',
      previa: f.type.startsWith('image/') ? URL.createObjectURL(f) : null,
    })
  }
  errorValidacion.value = errores.join(' ')
}

function alSoltar(e: DragEvent) {
  arrastrando.value = false
  if (!props.puedeSubir || !e.dataTransfer) return
  agregar(e.dataTransfer.files)
}
function alElegir(e: Event) {
  const el = e.target as HTMLInputElement
  if (el.files) agregar(el.files)
  el.value = ''
}

function quitar(c: EnCola) {
  if (c.previa) URL.revokeObjectURL(c.previa)
  cola.value = cola.value.filter((x) => x.clave !== c.clave)
}
onBeforeUnmount(() => cola.value.forEach((c) => c.previa && URL.revokeObjectURL(c.previa)))

async function subir(c: EnCola) {
  const err = coherencia(extensionDe(c.archivo.name), c.tipo, c.archivo.name)
  if (err) { c.error = err; return }
  c.error = ''
  c.subiendo = true
  c.progreso = 0
  try {
    await subirArchivo({
      archivo: c.archivo, pacienteId: props.pacienteId, tipo: c.tipo, descripcion: c.descripcion, ...props.vinculo,
    }, (p) => { c.progreso = p })
    quitar(c)
    ui.showSuccess('Archivo subido')
    emit('cambio')
  } catch (e) {
    c.error = errorText(e, 'No se pudo subir el archivo')
  } finally {
    c.subiendo = false
  }
}
async function subirTodos() {
  for (const c of [...cola.value]) if (!c.subiendo) await subir(c)
}

function abrir(a: ArchivoInfo) { visorArchivo.value = a; visorAbierto.value = true }

async function confirmarEliminar(motivo: string) {
  if (!aEliminar.value) return
  eliminando.value = true
  try {
    await eliminarArchivo(aEliminar.value.id, motivo)
    aEliminar.value = null
    ui.showSuccess('Archivo dado de baja')
    emit('cambio')
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo dar de baja el archivo'))
  } finally {
    eliminando.value = false
  }
}
</script>

<template>
  <div>
    <h3 v-if="titulo" class="vet-section-title">{{ titulo }}</h3>

    <template v-if="puedeSubir">
      <div class="vet-dropzone mb-3" :class="{ 'vet-dropzone--activa': arrastrando }" role="button" tabindex="0"
        :aria-label="`Subir archivos: arrastre aquí o presione Enter para elegir. Permitidos: ${EXT_ARCHIVO_PERMITIDAS.join(', ')}`"
        @click="entrada?.click()" @keydown.enter.prevent="entrada?.click()" @keydown.space.prevent="entrada?.click()"
        @dragover.prevent="arrastrando = true" @dragleave.prevent="arrastrando = false" @drop.prevent="alSoltar">
        <v-icon icon="mdi-cloud-upload-outline" size="36" />
        <div class="font-weight-bold">Arrastre archivos aquí o haga clic para elegirlos</div>
        <div class="text-caption text-medium-emphasis">
          {{ EXT_ARCHIVO_PERMITIDAS.join(', ') }} · hasta {{ tamanoLegible(MAX_BYTES_ARCHIVO) }} (DICOM {{ tamanoLegible(MAX_BYTES_DICOM) }})
        </div>
        <input :id="idInput" ref="entrada" type="file" multiple class="vet-sr-only" :accept="accept" tabindex="-1" @change="alElegir">
      </div>
      <v-alert v-if="errorValidacion" type="error" variant="tonal" density="compact" class="mb-3" closable :text="errorValidacion"
        @click:close="errorValidacion = ''" />

      <div v-if="cola.length" class="mb-4">
        <div v-for="c in cola" :key="c.clave" class="vet-arch-cola mb-2">
          <div class="d-flex flex-wrap ga-3 align-start">
            <img v-if="c.previa" :src="c.previa" :alt="`Vista previa de ${c.archivo.name}`" class="vet-miniatura vet-arch-cola-img">
            <div v-else class="vet-miniatura vet-arch-cola-img vet-arch-icono"><v-icon icon="mdi-file-outline" size="32" /></div>
            <div class="flex-grow-1">
              <div class="font-weight-bold">{{ c.archivo.name }} <span class="text-caption text-medium-emphasis">({{ tamanoLegible(c.archivo.size) }})</span></div>
              <v-row dense class="mt-1">
                <v-col v-if="tipoSeleccionable" cols="12" sm="4">
                  <v-select v-model="c.tipo" :items="tiposOpc" label="Tipo de archivo" :disabled="c.subiendo" />
                </v-col>
                <v-col cols="12" :sm="tipoSeleccionable ? 8 : 12">
                  <v-text-field v-model="c.descripcion" label="Descripción (opcional)" maxlength="255" :disabled="c.subiendo" />
                </v-col>
              </v-row>
              <v-progress-linear v-if="c.subiendo" :model-value="c.progreso" color="primary" height="8" class="mt-2" rounded />
              <v-alert v-if="c.error" type="error" variant="tonal" density="compact" class="mt-2" :text="c.error" />
            </div>
            <div class="d-flex ga-1">
              <v-btn color="primary" variant="flat" size="small" prepend-icon="mdi-upload" :loading="c.subiendo" @click="subir(c)">Subir</v-btn>
              <v-btn variant="text" size="small" :disabled="c.subiendo" @click="quitar(c)">Quitar</v-btn>
            </div>
          </div>
        </div>
        <v-btn v-if="cola.length > 1" variant="tonal" size="small" prepend-icon="mdi-upload-multiple" @click="subirTodos">Subir todos</v-btn>
      </div>
    </template>

    <div v-if="activos.length" class="vet-arch-grid">
      <div v-for="a in activos" :key="a.id" class="vet-arch-item">
        <button type="button" class="vet-arch-miniatura-btn" :aria-label="`Abrir ${a.nombreOriginal}`" @click="abrir(a)">
          <ArchivoMiniatura :archivo="a" />
        </button>
        <div class="vet-arch-nombre" :title="a.nombreOriginal">{{ a.nombreOriginal }}</div>
        <div class="d-flex align-center">
          <v-chip size="x-small" variant="tonal" :text="a.tipo" />
          <v-spacer />
          <v-btn v-if="puedeEliminar" size="x-small" variant="text" color="error" icon="mdi-delete-outline"
            :aria-label="`Dar de baja ${a.nombreOriginal}`" title="Dar de baja" @click="aEliminar = a" />
        </div>
      </div>
    </div>
    <div v-else class="text-medium-emphasis text-body-2">Sin archivos adjuntos.</div>

    <VisorArchivoDialog v-model="visorAbierto" :archivo="visorArchivo" :archivos="activos" />
    <MotivoDialog :model-value="!!aEliminar" title="Dar de baja el archivo" label="Motivo (mínimo 5 caracteres)"
      message="El archivo no se borra: queda con baja lógica y trazabilidad." confirm-text="Dar de baja" :loading="eliminando"
      @update:model-value="!$event && (aEliminar = null)" @confirm="confirmarEliminar" />
  </div>
</template>
