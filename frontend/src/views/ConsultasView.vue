<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { createConsulta, listConsultas } from '@/api/consultas'
import EstadoChip from '@/components/EstadoChip.vue'
import PageHeader from '@/components/PageHeader.vue'
import PacienteSelect from '@/components/agenda/PacienteSelect.vue'
import { useAuthStore } from '@/stores/auth'
import { useCatalogosStore } from '@/stores/catalogos'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaFecha, hoy } from '@/utils/fecha'
import type { Consulta } from '@/types'

const router = useRouter()
const auth = useAuthStore()
const ui = useUiStore()
const catalogos = useCatalogosStore()

const items = ref<Consulta[]>([])
const loading = ref(false)
const filtros = reactive({ fecha: '', pacienteId: null as number | null, estado: null as string | null })
const pag = reactive({ page: 0, size: 20, totalPages: 0 })

const dialog = ref(false)
const saving = ref(false)
const error = ref('')
const nueva = reactive({ pacienteId: null as number | null, veterinarioId: null as number | null, fecha: hoy(), motivo: '' })
const vetOptions = computed(() => catalogos.veterinarios.map((v) => ({ title: v.nombre, value: v.id })))

async function load() {
  loading.value = true
  try {
    const p = await listConsultas(filtros.fecha || undefined, filtros.pacienteId ?? undefined, pag.page, pag.size, filtros.estado ?? undefined)
    items.value = p.items
    pag.totalPages = p.totalPages
  } catch (e) {
    ui.showError(errorText(e, 'No se pudieron cargar las consultas'))
  } finally {
    loading.value = false
  }
}

function abrirNueva() {
  Object.assign(nueva, { pacienteId: null, veterinarioId: auth.usuario?.rol === 'VETERINARIO' ? auth.usuario.id : null, fecha: hoy(), motivo: '' })
  error.value = ''
  dialog.value = true
}

async function crear() {
  if (!nueva.pacienteId || !nueva.veterinarioId) { error.value = 'Seleccione paciente y veterinario'; return }
  saving.value = true
  try {
    const c = await createConsulta({
      pacienteId: nueva.pacienteId, veterinarioId: nueva.veterinarioId, fecha: nueva.fecha, motivo: nueva.motivo.trim() || undefined,
    })
    dialog.value = false
    await router.push({ name: 'consulta', params: { id: c.id } })
  } catch (e) {
    error.value = errorText(e, 'No se pudo crear la consulta')
  } finally {
    saving.value = false
  }
}

watch(filtros, () => { pag.page = 0; void load() })
onMounted(async () => {
  void load()
  catalogos.ensure().catch(() => undefined)
})
</script>

<template>
  <div>
    <PageHeader title="Consultas" subtitle="Historia clínica">
      <v-btn v-if="auth.can.clinicaWrite" variant="flat" prepend-icon="mdi-medical-bag" @click="abrirNueva">Nueva consulta</v-btn>
    </PageHeader>

    <v-card border elevation="0" class="mb-3">
      <v-card-text class="d-flex align-center flex-wrap ga-3">
        <v-text-field v-model="filtros.fecha" label="Fecha" type="date" clearable style="max-width: 190px" />
        <div style="min-width: 280px"><PacienteSelect v-model="filtros.pacienteId" label="Paciente" /></div>
        <v-select v-model="filtros.estado" :items="[{ title: 'Abiertas', value: 'ABIERTA' }, { title: 'Cerradas', value: 'CERRADA' }]"
          label="Estado" clearable style="max-width: 180px" />
      </v-card-text>
    </v-card>

    <v-card border elevation="0">
      <v-data-table
        :headers="[
          { title: 'Código', key: 'codigo' }, { title: 'Fecha', key: 'fecha' }, { title: 'Paciente', key: 'paciente' },
          { title: 'Veterinario', key: 'veterinario' }, { title: 'Motivo', key: 'motivo' }, { title: 'Tipo cita / sala', key: 'cita' },
          { title: 'Estado', key: 'estado' }, { title: '', key: 'acciones', sortable: false, align: 'end' },
        ]"
        :items="items" :loading="loading" :items-per-page="-1" hide-default-footer no-data-text="Sin consultas"
        @click:row="(_: unknown, r: { item: Consulta }) => router.push({ name: 'consulta', params: { id: r.item.id } })"
      >
        <template #item.codigo="{ item }"><v-chip size="small" variant="tonal" :text="item.codigo" /></template>
        <template #item.fecha="{ item }">{{ etiquetaFecha(item.fecha) }}</template>
        <template #item.paciente="{ item }">{{ item.paciente?.nombre }} <span class="text-medium-emphasis">({{ item.paciente?.codigo }})</span></template>
        <template #item.veterinario="{ item }">{{ item.veterinario?.nombre }}</template>
        <template #item.motivo="{ item }">{{ item.motivo || '—' }}</template>
        <template #item.cita="{ item }">{{ [item.tipoCita?.nombre, item.sala?.nombre].filter(Boolean).join(' · ') || '—' }}</template>
        <template #item.estado="{ item }"><EstadoChip :estado="item.estado" /></template>
        <template #item.acciones="{ item }">
          <v-btn size="small" variant="text" icon="mdi-open-in-new" title="Abrir consulta" :to="{ name: 'consulta', params: { id: item.id } }" @click.stop />
        </template>
        <template #bottom>
          <div v-if="pag.totalPages > 1" class="pa-4 d-flex justify-center">
            <v-pagination v-model="pag.page" :length="pag.totalPages" :total-visible="7" @update:model-value="load" />
          </div>
        </template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="dialog" max-width="560">
      <v-card>
        <v-card-title class="pt-4">Nueva consulta sin cita</v-card-title>
        <v-card-text>
          <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
          <v-row dense>
            <v-col cols="12"><PacienteSelect v-model="nueva.pacienteId" /></v-col>
            <v-col cols="12" sm="6"><v-select v-model="nueva.veterinarioId" :items="vetOptions" label="Veterinario" /></v-col>
            <v-col cols="12" sm="6"><v-text-field v-model="nueva.fecha" label="Fecha" type="date" /></v-col>
            <v-col cols="12"><v-text-field v-model="nueva.motivo" label="Motivo" /></v-col>
          </v-row>
          <div class="text-caption text-medium-emphasis">Lo habitual es iniciar la consulta desde la cita (Agenda).</div>
        </v-card-text>
        <v-card-actions class="px-4 pb-4">
          <v-spacer />
          <v-btn variant="text" @click="dialog = false">Cancelar</v-btn>
          <v-btn color="primary" variant="flat" :loading="saving" @click="crear">Crear y abrir</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
