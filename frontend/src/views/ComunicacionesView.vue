<script setup lang="ts">
import { ref } from 'vue'
import { ejecutarJob } from '@/api/comunicaciones'
import BitacoraTab from '@/components/comunicaciones/BitacoraTab.vue'
import PlantillasTab from '@/components/comunicaciones/PlantillasTab.vue'
import RecordatoriosTab from '@/components/comunicaciones/RecordatoriosTab.vue'
import ReglasTab from '@/components/comunicaciones/ReglasTab.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import type { ResultadoEjecucion } from '@/types/fase3'

const auth = useAuthStore()
const ui = useUiStore()
const tab = ref('recordatorios')
const confirmar = ref(false)
const ejecutando = ref(false)
const resultado = ref<ResultadoEjecucion | null>(null)
const recs = ref<InstanceType<typeof RecordatoriosTab> | null>(null)
const bit = ref<InstanceType<typeof BitacoraTab> | null>(null)

const ETIQUETAS: [keyof ResultadoEjecucion, string][] = [
  ['generados', 'Generados'], ['yaExistian', 'Ya existían'], ['enviados', 'Enviados'], ['simulados', 'Simulados'],
  ['fallidos', 'Fallidos'], ['reintentosPendientes', 'Reintentos pendientes'], ['cancelados', 'Cancelados'],
  ['omitidosSinConsentimiento', 'Sin consentimiento'], ['omitidosSinContacto', 'Sin contacto'], ['omitidosOptout', 'Omitidos por baja'], ['reglasSinCanal', 'Reglas sin canal'],
]

async function ejecutar() {
  ejecutando.value = true
  try {
    resultado.value = await ejecutarJob()
    confirmar.value = false
    ui.showSuccess('Job ejecutado')
    await Promise.all([recs.value?.recargar(), bit.value?.recargar()])
  } catch (e) { confirmar.value = false; ui.showError(errorText(e, 'No se pudo ejecutar el job')) } finally { ejecutando.value = false }
}
</script>

<template>
  <div>
    <PageHeader title="Comunicaciones" subtitle="Recordatorios, plantillas, reglas y bitácora de envíos">
      <v-btn v-if="auth.can.comunicacionesWrite" variant="flat" prepend-icon="mdi-play-circle-outline" @click="confirmar = true">Ejecutar ahora</v-btn>
    </PageHeader>

    <v-alert v-if="resultado" type="success" variant="tonal" closable class="mb-4" title="Resultado de la ejecución" @click:close="resultado = null">
      <div class="text-caption mb-2">Fecha de proceso: {{ resultado.fecha }}</div>
      <div class="d-flex flex-wrap ga-2">
        <v-chip v-for="[k, label] in ETIQUETAS" :key="k" size="small" variant="tonal" :color="(k === 'fallidos' && resultado[k] > 0) ? 'error' : undefined">
          {{ label }}: <strong class="ml-1">{{ resultado[k] }}</strong>
        </v-chip>
      </div>
    </v-alert>
    <v-alert v-if="!auth.can.comunicacionesWrite" type="info" variant="tonal" density="compact" class="mb-4"
      text="Plantillas y reglas son de solo lectura para su rol; los recordatorios pueden consultarse y cancelarse." />

    <v-card border elevation="0">
      <v-tabs v-model="tab" color="primary" density="comfortable">
        <v-tab value="recordatorios" prepend-icon="mdi-bell-outline">Recordatorios</v-tab>
        <v-tab value="plantillas" prepend-icon="mdi-file-document-edit-outline">Plantillas</v-tab>
        <v-tab value="reglas" prepend-icon="mdi-cog-outline">Reglas</v-tab>
        <v-tab value="bitacora" prepend-icon="mdi-history">Bitácora de envíos</v-tab>
      </v-tabs>
      <v-divider />
      <v-card-text>
        <v-tabs-window v-model="tab">
          <v-tabs-window-item value="recordatorios"><RecordatoriosTab ref="recs" /></v-tabs-window-item>
          <v-tabs-window-item value="plantillas"><PlantillasTab :puede-editar="auth.can.comunicacionesWrite" /></v-tabs-window-item>
          <v-tabs-window-item value="reglas"><ReglasTab :puede-editar="auth.can.comunicacionesWrite" /></v-tabs-window-item>
          <v-tabs-window-item value="bitacora"><BitacoraTab ref="bit" /></v-tabs-window-item>
        </v-tabs-window>
      </v-card-text>
    </v-card>

    <ConfirmDialog v-model="confirmar" title="Ejecutar el job de recordatorios" color="primary" confirm-text="Ejecutar" :loading="ejecutando"
      message="Se generarán los recordatorios que toquen hoy y se procesará la cola de envío. Es idempotente: no duplica recordatorios." @confirm="ejecutar" />
  </div>
</template>
