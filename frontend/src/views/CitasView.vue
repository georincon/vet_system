<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { listCitas } from '@/api/citas'
import EstadoChip from '@/components/EstadoChip.vue'
import PageHeader from '@/components/PageHeader.vue'
import CitaDetalleDialog from '@/components/agenda/CitaDetalleDialog.vue'
import CitaDialog from '@/components/agenda/CitaDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaFecha, hhmm } from '@/utils/fecha'
import type { Cita } from '@/types'

const ui = useUiStore()
const auth = useAuthStore()

const items = ref<Cita[]>([])
const loading = ref(false)
const params = reactive({ fecha: '', page: 0, size: 20, totalPages: 0 })

const citaDialog = ref(false)
const editando = ref<Cita | null>(null)
const detalle = ref<Cita | null>(null)
const detalleDialog = ref(false)

async function load() {
  loading.value = true
  try {
    const page = await listCitas(params.fecha || undefined, params.page, params.size)
    items.value = page.items
    params.totalPages = page.totalPages
  } catch (e) {
    ui.showError(errorText(e, 'No se pudieron cargar las citas'))
  } finally {
    loading.value = false
  }
}

function nueva() { editando.value = null; citaDialog.value = true }
function ver(c: Cita) { detalle.value = c; detalleDialog.value = true }
function editar(c: Cita) { detalleDialog.value = false; editando.value = c; citaDialog.value = true }
function onChanged(c?: Cita) { if (c) detalle.value = c; void load() }

watch(() => params.fecha, () => { params.page = 0; void load() })
onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="Citas" subtitle="Listado de citas">
      <v-btn variant="flat" prepend-icon="mdi-calendar-week" :to="{ name: 'agenda' }">Ver calendario</v-btn>
      <v-btn v-if="auth.can.agendaWrite" variant="flat" prepend-icon="mdi-calendar-plus" @click="nueva">Nueva cita</v-btn>
    </PageHeader>

    <v-card border elevation="0" class="mb-3">
      <v-card-text class="d-flex align-center ga-3">
        <v-text-field v-model="params.fecha" label="Filtrar por fecha" type="date" clearable style="max-width: 240px" />
        <v-btn variant="tonal" prepend-icon="mdi-refresh" @click="load">Actualizar</v-btn>
      </v-card-text>
    </v-card>

    <v-card border elevation="0">
      <v-data-table
        :headers="[
          { title: 'Código', key: 'codigo' }, { title: 'Fecha', key: 'fecha' }, { title: 'Hora', key: 'horaInicio' },
          { title: 'Paciente', key: 'paciente' }, { title: 'Veterinario', key: 'veterinario' }, { title: 'Tipo / sala', key: 'tipo' },
          { title: 'Estado', key: 'estadoCita' }, { title: 'Motivo', key: 'motivo' }, { title: '', key: 'acciones', sortable: false, align: 'end' },
        ]"
        :items="items" :loading="loading" :items-per-page="-1" hide-default-footer no-data-text="Sin citas"
      >
        <template #item.codigo="{ item }"><v-chip size="small" variant="tonal" :text="item.codigo" /></template>
        <template #item.fecha="{ item }">{{ etiquetaFecha(item.fecha) }}</template>
        <template #item.horaInicio="{ item }">{{ hhmm(item.horaInicio) }}{{ item.horaFin ? ' - ' + hhmm(item.horaFin) : '' }}</template>
        <template #item.paciente="{ item }">{{ item.paciente?.nombre }} ({{ item.paciente?.codigo }})</template>
        <template #item.veterinario="{ item }">{{ item.veterinario?.nombre }}</template>
        <template #item.tipo="{ item }">
          {{ item.tipoCita?.nombre ?? '—' }}
          <div class="text-caption text-medium-emphasis">{{ item.sala?.nombre ?? 'Sin sala' }}</div>
        </template>
        <template #item.estadoCita="{ item }"><EstadoChip :estado="item.estadoCita?.codigo" :label="item.estadoCita?.nombre" /></template>
        <template #item.motivo="{ item }">{{ item.motivo || '—' }}</template>
        <template #item.acciones="{ item }"><v-btn size="small" variant="text" icon="mdi-eye-outline" title="Ver / gestionar" @click="ver(item)" /></template>
        <template #bottom>
          <div v-if="params.totalPages > 1" class="pa-4 d-flex justify-center">
            <v-pagination v-model="params.page" :length="params.totalPages" :total-visible="7" @update:model-value="load" />
          </div>
        </template>
      </v-data-table>
    </v-card>

    <CitaDetalleDialog v-model="detalleDialog" :cita="detalle" @changed="onChanged" @editar="editar" />
    <CitaDialog v-model="citaDialog" :cita="editando" @saved="load" />
  </div>
</template>
