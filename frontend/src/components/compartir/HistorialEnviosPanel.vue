<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { historialEnvios, reenviar, revocar } from '@/api/compartir'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import MotivoDialog from '@/components/MotivoDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'
import type { Envio } from '@/types/etapa'
import { TIPO_DOCUMENTO_COMPARTIR_LABEL } from '@/types/etapa'

const props = defineProps<{ pacienteId: number }>()
const auth = useAuthStore()
const ui = useUiStore()
const items = ref<Envio[]>([])
const loading = ref(false)
const page = ref(1)
const totalPages = ref(0)
const reenvio = ref<Envio | null>(null)
const revocando = ref<Envio | null>(null)
const trabajando = ref(false)
const nuevo = ref<Envio | null>(null)

const puedeRevocar = computed(() => auth.can.compartirRevocar)
const headers = [
  { title: 'Fecha', key: 'creadoEn' }, { title: 'Estado', key: 'estado' }, { title: 'Canal', key: 'canal' },
  { title: 'Documento', key: 'documentos', sortable: false }, { title: 'Destinatario', key: 'destinatario' },
  { title: 'Usuario', key: 'creadoPorNombre' }, { title: 'Intentos', key: 'intentos' }, { title: 'Error', key: 'ultimoError' },
  { title: 'Acciones', key: 'acciones', sortable: false },
]

async function load() {
  loading.value = true
  try {
    const r = await historialEnvios({ pacienteId: props.pacienteId, page: page.value - 1, size: 10 })
    items.value = r.items; totalPages.value = r.totalPages
  } catch (e) { ui.showError(errorText(e, 'No se pudo cargar el historial de envíos')) } finally { loading.value = false }
}
const docsTexto = (e: Envio): string => e.documentos.map((d) => TIPO_DOCUMENTO_COMPARTIR_LABEL[d.tipo] ?? d.tipo).join(', ')
const activos = (e: Envio) => e.enlaces.some((l) => !l.revocado)

async function hacerReenvio() {
  if (!reenvio.value) return
  trabajando.value = true
  try { nuevo.value = await reenviar(reenvio.value.id); reenvio.value = null; ui.showSuccess('Reenviado'); await load() }
  catch (e) { ui.showError(errorText(e, 'No se pudo reenviar')) } finally { trabajando.value = false }
}
async function hacerRevocar(motivo: string) {
  if (!revocando.value) return
  trabajando.value = true
  try { await revocar(revocando.value.id, motivo); revocando.value = null; ui.showSuccess('Enlaces revocados'); await load() }
  catch (e) { ui.showError(errorText(e, 'No se pudo revocar')) } finally { trabajando.value = false }
}
watch(() => props.pacienteId, () => { page.value = 1; void load() })
onMounted(load)
defineExpose({ load })
</script>

<template>
  <div>
    <v-alert v-if="nuevo?.enlaces.some((e) => e.url) || nuevo?.urlWaMe" type="info" variant="tonal" closable class="mb-3" title="Nuevo envío generado" @click:close="nuevo = null">
      <v-btn v-if="nuevo?.urlWaMe" :href="nuevo.urlWaMe" target="_blank" rel="noopener" size="small" variant="tonal" prepend-icon="mdi-whatsapp">Abrir en WhatsApp</v-btn>
      <div v-for="e in nuevo?.enlaces.filter((x) => x.url)" :key="e.id" class="text-caption mt-1"><code>{{ e.url }}</code></div>
    </v-alert>
    <v-data-table :headers="headers" :items="items" :loading="loading" :items-per-page="-1" hide-default-footer density="compact" no-data-text="Sin envíos registrados">
      <template #item.creadoEn="{ item }">{{ etiquetaInstante(item.creadoEn) }}</template>
      <template #item.estado="{ item }">
        <EstadoChip :estado="item.estado" /><v-chip v-if="item.revocado" size="x-small" color="error" variant="tonal" class="ml-1" text="Enlace revocado" />
      </template>
      <template #item.documentos="{ item }">{{ docsTexto(item) }}</template>
      <template #item.ultimoError="{ item }"><span class="text-error">{{ item.ultimoError ?? '' }}</span></template>
      <template #item.acciones="{ item }">
        <v-btn size="x-small" variant="text" icon="mdi-send-clock-outline" title="Reenviar" aria-label="Reenviar" @click="reenvio = item" />
        <v-btn v-if="puedeRevocar && activos(item)" size="x-small" variant="text" color="error" icon="mdi-link-off" title="Revocar enlace" aria-label="Revocar enlace" @click="revocando = item" />
      </template>
    </v-data-table>
    <div v-if="totalPages > 1" class="d-flex justify-center mt-2"><v-pagination v-model="page" :length="totalPages" :total-visible="6" @update:model-value="load" /></div>
    <ConfirmDialog :model-value="!!reenvio" title="Reenviar documento" color="primary" confirm-text="Reenviar" :loading="trabajando"
      message="Se revalidan los consentimientos, se revocan los enlaces anteriores y se envía de nuevo la misma copia del documento."
      @update:model-value="!$event && (reenvio = null)" @confirm="hacerReenvio" />
    <MotivoDialog :model-value="!!revocando" title="Revocar enlace seguro" message="Los enlaces dejarán de funcionar de inmediato." label="Motivo (opcional)"
      :required="false" confirm-text="Revocar" :loading="trabajando" @update:model-value="!$event && (revocando = null)" @confirm="hacerRevocar" />
  </div>
</template>
