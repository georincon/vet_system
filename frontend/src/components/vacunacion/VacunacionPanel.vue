<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { anularInmunizacion, descargarCarnet, getEsquemaSugerido, listInmunizaciones } from '@/api/inmunizacion'
import AplicarInmunizacionDialog from '@/components/vacunacion/AplicarInmunizacionDialog.vue'
import EsquemaSugeridoPanel from '@/components/vacunacion/EsquemaSugeridoPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaFecha, etiquetaInstante } from '@/utils/fecha'
import type { Paciente } from '@/types'
import type { EsquemaSugerido, EsquemaSugeridoItem, Inmunizacion, TipoInmunizacion } from '@/types/fase3'

const props = defineProps<{ paciente: Paciente }>()
const emit = defineEmits<{ cambio: [] }>()

const auth = useAuthStore()
const ui = useUiStore()

const items = ref<Inmunizacion[]>([])
const esquema = ref<EsquemaSugerido | null>(null)
const loading = ref(false)
const loadingEsquema = ref(false)
const filtro = ref<'TODOS' | TipoInmunizacion>('TODOS')
const verAnuladas = ref(true)
const errorCarga = ref('')

const filtradas = computed(() =>
  items.value.filter((i) => (filtro.value === 'TODOS' || i.tipo === filtro.value) && (verAnuladas.value || i.estado !== 'ANULADA')),
)

async function cargar() {
  loading.value = true
  loadingEsquema.value = true
  errorCarga.value = ''
  const [a, b] = await Promise.allSettled([listInmunizaciones(props.paciente.id), getEsquemaSugerido(props.paciente.id)])
  if (a.status === 'fulfilled') items.value = a.value
  else errorCarga.value = errorText(a.reason, 'No se pudo cargar la vacunación')
  esquema.value = b.status === 'fulfilled' ? b.value : null
  loading.value = false
  loadingEsquema.value = false
}

// Aplicar
const aplicarOpen = ref(false)
const inicial = ref<{ tipo: TipoInmunizacion; productoId?: number | null } | null>(null)
function abrirAplicar(item?: EsquemaSugeridoItem) {
  inicial.value = item ? { tipo: 'VACUNA', productoId: item.vacunaId } : null
  aplicarOpen.value = true
}
async function tras() { await cargar(); emit('cambio') }

// Anular
const anular = ref<Inmunizacion | null>(null)
const motivo = ref('')
const anulando = ref(false)
const errorAnular = ref('')
function pedirAnular(i: Inmunizacion) { anular.value = i; motivo.value = ''; errorAnular.value = '' }
async function confirmarAnular() {
  if (!anular.value) return
  if (!motivo.value.trim()) { errorAnular.value = 'El motivo es obligatorio'; return }
  anulando.value = true
  try {
    await anularInmunizacion(props.paciente.id, anular.value.id, anular.value.tipo, motivo.value.trim())
    anular.value = null
    ui.showSuccess('Aplicación anulada; la dosis volvió al lote si correspondía')
    await tras()
  } catch (e) { errorAnular.value = errorText(e, 'No se pudo anular') } finally { anulando.value = false }
}

// Carnet
const descargando = ref(false)
async function carnet() {
  descargando.value = true
  try {
    const { url, nombre } = await descargarCarnet(props.paciente.id)
    const a = document.createElement('a')
    a.href = url
    a.download = nombre
    a.target = '_blank'
    a.rel = 'noopener'
    document.body.appendChild(a)
    a.click()
    a.remove()
    window.open(url, '_blank', 'noopener')
    window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
    ui.showSuccess(`Carnet generado: ${nombre}`)
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo generar el carnet'))
  } finally { descargando.value = false }
}

const headers = [
  { title: 'Fecha', key: 'fechaAplicacion' },
  { title: 'Tipo', key: 'tipo', sortable: false },
  { title: 'Producto', key: 'producto' },
  { title: 'Dosis', key: 'dosisNumero', sortable: false },
  { title: 'Lote', key: 'lote', sortable: false },
  { title: 'Próxima', key: 'proximaFecha' },
  { title: 'Veterinario', key: 'veterinario', sortable: false },
  { title: 'Estado', key: 'estado', sortable: false },
  { title: '', key: 'acciones', sortable: false, align: 'end' as const },
]

watch(() => props.paciente.id, cargar)
onMounted(cargar)
</script>

<template>
  <div>
    <div class="d-flex align-center flex-wrap ga-2 mb-3">
      <v-btn v-if="auth.can.inmunizacionAplicar" color="primary" variant="flat" prepend-icon="mdi-needle" @click="abrirAplicar()">Aplicar vacuna / antiparasitario</v-btn>
      <v-btn variant="tonal" color="primary" prepend-icon="mdi-file-pdf-box" :loading="descargando" @click="carnet">Descargar carnet (PDF)</v-btn>
      <v-spacer />
      <v-btn-toggle v-model="filtro" mandatory density="compact" color="primary" variant="outlined" divided>
        <v-btn value="TODOS" size="small">Todo</v-btn>
        <v-btn value="VACUNA" size="small">Vacunas</v-btn>
        <v-btn value="ANTIPARASITARIO" size="small">Antiparasitarios</v-btn>
      </v-btn-toggle>
      <v-checkbox v-model="verAnuladas" label="Ver anuladas" density="compact" hide-details />
    </div>

    <v-alert v-if="errorCarga" type="error" variant="tonal" density="compact" class="mb-3" :text="errorCarga" />

    <v-card border elevation="0" class="mb-6">
      <v-data-table :headers="headers" :items="filtradas" :loading="loading" :items-per-page="10" density="comfortable"
        no-data-text="Sin aplicaciones registradas" items-per-page-text="Filas">
        <template #item.fechaAplicacion="{ item }">{{ etiquetaFecha(item.fechaAplicacion) }}</template>
        <template #item.tipo="{ item }">
          <v-chip size="small" variant="tonal" :color="item.tipo === 'VACUNA' ? 'info' : 'secondary'"
            :text="item.tipo === 'VACUNA' ? 'Vacuna' : 'Antiparasitario'" />
        </template>
        <template #item.producto="{ item }">
          <div :class="{ 'text-decoration-line-through text-medium-emphasis': item.estado === 'ANULADA' }" class="font-weight-medium">{{ item.producto }}</div>
          <div v-if="item.clasificacion && item.tipo === 'ANTIPARASITARIO'" class="text-caption text-medium-emphasis">{{ item.clasificacion.toLowerCase() }}</div>
          <div v-if="item.externa" class="text-caption text-medium-emphasis">Aplicada en otra clínica</div>
        </template>
        <template #item.dosisNumero="{ item }">
          <template v-if="item.tipo === 'VACUNA'">{{ item.dosisNumero ?? '—' }}<span v-if="item.dosisNombre" class="text-caption text-medium-emphasis"> · {{ item.dosisNombre }}</span></template>
          <template v-else>{{ item.dosis ?? '—' }}</template>
        </template>
        <template #item.lote="{ item }">
          {{ item.lote ?? '—' }}
          <div v-if="item.fechaVencimientoLote" class="text-caption text-medium-emphasis">vence {{ etiquetaFecha(item.fechaVencimientoLote) }}</div>
        </template>
        <template #item.proximaFecha="{ item }">{{ item.estado === 'ANULADA' ? '—' : etiquetaFecha(item.proximaFecha) }}</template>
        <template #item.veterinario="{ item }">{{ item.veterinario?.nombre ?? '—' }}</template>
        <template #item.estado="{ item }">
          <v-chip v-if="item.estado === 'ANULADA'" size="small" variant="tonal" color="error" prepend-icon="mdi-cancel"
            :text="'Anulada'" :title="`${item.motivoAnulacion ?? ''} ${item.anuladoEn ? '· ' + etiquetaInstante(item.anuladoEn) : ''}`" />
          <v-chip v-else size="small" variant="tonal" color="success" text="Aplicada" />
          <div v-if="item.reaccionAdversa" class="text-caption text-error">Reacción: {{ item.reaccionAdversa }}</div>
        </template>
        <template #item.acciones="{ item }">
          <v-btn v-if="auth.can.inmunizacionAnular && item.estado !== 'ANULADA'" size="small" variant="text" color="error" icon="mdi-cancel"
            title="Anular aplicación" @click="pedirAnular(item)" />
        </template>
      </v-data-table>
    </v-card>

    <EsquemaSugeridoPanel :esquema="esquema" :loading="loadingEsquema" :puede-aplicar="auth.can.inmunizacionAplicar" @aplicar="abrirAplicar" />

    <AplicarInmunizacionDialog v-model="aplicarOpen" :paciente-id="paciente.id" :paciente-nombre="paciente.nombre"
      :especie-id="paciente.especie.id" :fecha-nacimiento="paciente.fechaNacimiento" :inicial="inicial" :sugeridos="esquema?.items"
      @aplicada="tras" />

    <v-dialog :model-value="!!anular" max-width="480" @update:model-value="!$event && (anular = null)">
      <v-card>
        <v-card-title class="pt-4">Anular aplicación</v-card-title>
        <v-card-text>
          <p class="mb-3">Se anulará <strong>{{ anular?.producto }}</strong> del {{ etiquetaFecha(anular?.fechaAplicacion) }}. No se borra: queda en el historial y la dosis vuelve al lote.</p>
          <v-alert v-if="errorAnular" type="error" variant="tonal" density="compact" class="mb-3" :text="errorAnular" />
          <v-textarea v-model="motivo" label="Motivo de la anulación (obligatorio)" rows="2" auto-grow autofocus />
        </v-card-text>
        <v-card-actions class="px-4 pb-4">
          <v-spacer />
          <v-btn variant="text" @click="anular = null">Cancelar</v-btn>
          <v-btn color="error" variant="flat" :loading="anulando" @click="confirmarAnular">Anular</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
