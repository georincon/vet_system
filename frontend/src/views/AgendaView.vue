<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPaciente } from '@/api/pacientes'
import { deleteBloqueo, getAgenda } from '@/api/agenda'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import PageHeader from '@/components/PageHeader.vue'
import AgendaGrid, { type GridColumn } from '@/components/agenda/AgendaGrid.vue'
import BloqueoDialog from '@/components/agenda/BloqueoDialog.vue'
import CitaDetalleDialog from '@/components/agenda/CitaDetalleDialog.vue'
import CitaDialog, { type CitaDefaults } from '@/components/agenda/CitaDialog.vue'
import ListaEsperaPanel from '@/components/agenda/ListaEsperaPanel.vue'
import SalaEsperaPanel from '@/components/agenda/SalaEsperaPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { useCatalogosStore } from '@/stores/catalogos'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaDia, etiquetaInstante, etiquetaRango, hoy, inicioSemana, sumarDias } from '@/utils/fecha'
import type { BloqueoAgenda, Cita } from '@/types'

const auth = useAuthStore()
const ui = useUiStore()
const catalogos = useCatalogosStore()

const tab = ref<'calendario' | 'sala' | 'lista'>('calendario')
const vista = ref<'semana' | 'dia'>('semana')
const agrupar = ref<'veterinario' | 'sala'>('veterinario')
const fecha = ref(hoy())
const veterinarioId = ref<number | null>(null)
const salaId = ref<number | null>(null)
const incluirCanceladas = ref(false)

const citas = ref<Cita[]>([])
const bloqueos = ref<BloqueoAgenda[]>([])
const loading = ref(false)

const detalle = ref<Cita | null>(null)
const detalleDialog = ref(false)
const citaDialog = ref(false)
const citaEditando = ref<Cita | null>(null)
const citaDefaults = ref<CitaDefaults>({})
const bloqueoDialog = ref(false)
const bloqueoDefaults = ref<{ fecha?: string; horaInicio?: string; veterinarioId?: number | null; salaId?: number | null }>({})
const bloqueoEliminar = ref<BloqueoAgenda | null>(null)

const rango = computed(() => {
  if (vista.value === 'dia') return { desde: fecha.value, hasta: fecha.value }
  const d = inicioSemana(fecha.value)
  return { desde: d, hasta: sumarDias(d, 6) }
})

const vetOptions = computed(() => catalogos.veterinarios.map((v) => ({ title: v.nombre, value: v.id })))
const salasAgenda = computed(() => catalogos.salas.filter((s) => s.activo && s.tipo !== 'ESPERA'))
const salaOptions = computed(() => salasAgenda.value.map((s) => ({ title: s.nombre, value: s.id })))

const columnas = computed<GridColumn[]>(() => {
  const h = hoy()
  if (vista.value === 'semana') {
    return Array.from({ length: 7 }, (_, i) => {
      const f = sumarDias(rango.value.desde, i)
      return {
        key: f, title: etiquetaDia(f), fecha: f, esHoy: f === h,
        veterinarioId: veterinarioId.value, salaId: salaId.value,
        citas: citas.value.filter((c) => c.fecha === f), bloqueos: bloqueos.value,
      }
    })
  }
  const f = fecha.value
  if (agrupar.value === 'veterinario') {
    return catalogos.veterinarios
      .filter((v) => !veterinarioId.value || v.id === veterinarioId.value)
      .map((v) => ({
        key: `v${v.id}`, title: v.nombre, subtitle: 'Veterinario', fecha: f, esHoy: f === h, veterinarioId: v.id,
        salaId: salaId.value,
        citas: citas.value.filter((c) => c.veterinario.id === v.id),
        bloqueos: bloqueos.value.filter((b) => b.veterinario?.id === v.id),
      }))
  }
  const cols: GridColumn[] = salasAgenda.value
    .filter((s) => !salaId.value || s.id === salaId.value)
    .map((s) => ({
      key: `s${s.id}`, title: s.nombre, subtitle: s.tipo.toLowerCase(), fecha: f, esHoy: f === h, salaId: s.id,
      veterinarioId: veterinarioId.value,
      citas: citas.value.filter((c) => c.sala?.id === s.id),
      bloqueos: bloqueos.value.filter((b) => b.sala?.id === s.id),
    }))
  const sinSala = citas.value.filter((c) => !c.sala)
  if (sinSala.length) {
    cols.push({ key: 'sin-sala', title: 'Sin sala', fecha: f, esHoy: f === h, citas: sinSala, bloqueos: [] })
  }
  return cols
})

async function load() {
  loading.value = true
  try {
    const res = await getAgenda({
      ...rango.value, veterinarioId: veterinarioId.value, salaId: salaId.value,
      incluirCanceladas: incluirCanceladas.value,
    })
    citas.value = res.citas
    bloqueos.value = res.bloqueos
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo cargar la agenda'))
  } finally {
    loading.value = false
  }
}

function mover(delta: number) {
  fecha.value = sumarDias(fecha.value, delta * (vista.value === 'semana' ? 7 : 1))
}

function nuevaCita(col?: GridColumn, hora?: string) {
  citaEditando.value = null
  citaDefaults.value = {
    fecha: col?.fecha ?? fecha.value, horaInicio: hora ?? '09:00',
    veterinarioId: col?.veterinarioId ?? veterinarioId.value, salaId: col?.salaId ?? salaId.value,
  }
  citaDialog.value = true
}

function onSlot(col: GridColumn, hora: string) {
  if (auth.can.agendaWrite) nuevaCita(col, hora)
}

function abrirCita(c: Cita) {
  detalle.value = c
  detalleDialog.value = true
}

function editar(c: Cita) {
  detalleDialog.value = false
  citaEditando.value = c
  citaDialog.value = true
}

function onDetalleChanged(c?: Cita) {
  if (c) detalle.value = c
  void load()
}

function nuevoBloqueo() {
  bloqueoDefaults.value = { fecha: fecha.value, veterinarioId: veterinarioId.value, salaId: salaId.value }
  bloqueoDialog.value = true
}

function onBloqueo(b: BloqueoAgenda) {
  if (auth.can.agendaAdmin) bloqueoEliminar.value = b
  else ui.showSnackbar(`${b.tipo}${b.motivo ? ': ' + b.motivo : ''} (${etiquetaInstante(b.inicio)})`, 'info')
}

async function eliminarBloqueo() {
  const b = bloqueoEliminar.value
  if (!b) return
  try {
    await deleteBloqueo(b.id)
    ui.showSuccess('Bloqueo eliminado')
    bloqueoEliminar.value = null
    await load()
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo eliminar el bloqueo'))
  }
}

watch([fecha, vista, veterinarioId, salaId, incluirCanceladas], load)
const route = useRoute()
const router = useRouter()
/** Cita precargada desde un control de seguimiento (`citaSugerida`): /agenda?nuevaCita=1&pacienteId&fecha&veterinarioId&motivo */
async function precargarCita() {
  const q = route.query
  if (!q.nuevaCita || !auth.can.agendaWrite) return
  const pacienteId = Number(q.pacienteId) || null
  let paciente = null
  if (pacienteId) {
    try {
      const p = await getPaciente(pacienteId)
      paciente = { id: p.id, codigo: p.codigo, nombre: p.nombre, especie: p.especie.nombre, raza: p.raza?.nombre, sexo: p.sexo, fechaNacimiento: p.fechaNacimiento, activo: p.activo }
    } catch { /* se deja elegir manualmente */ }
  }
  if (typeof q.fecha === 'string' && q.fecha) fecha.value = q.fecha
  citaEditando.value = null
  citaDefaults.value = {
    fecha: typeof q.fecha === 'string' && q.fecha ? q.fecha : fecha.value, horaInicio: '09:00', pacienteId, paciente,
    veterinarioId: Number(q.veterinarioId) || null, motivo: typeof q.motivo === 'string' ? q.motivo : '',
  }
  citaDialog.value = true
  void router.replace({ query: {} })
}
onMounted(async () => {
  try { await catalogos.ensure() } catch (e) { ui.showError(errorText(e, 'No se pudieron cargar los catálogos')) }
  await load()
  await precargarCita()
})
</script>

<template>
  <div>
    <PageHeader title="Agenda" subtitle="Calendario de citas, sala de espera y lista de espera">
      <v-btn variant="flat" prepend-icon="mdi-table" :to="{ name: 'citas' }">Vista de tabla</v-btn>
      <v-btn v-if="auth.can.agendaAdmin" variant="flat" prepend-icon="mdi-cancel" @click="nuevoBloqueo">Bloquear</v-btn>
      <v-btn v-if="auth.can.agendaWrite" variant="flat" prepend-icon="mdi-calendar-plus" @click="nuevaCita()">Nueva cita</v-btn>
    </PageHeader>

    <v-tabs v-model="tab" color="primary" class="mb-4">
      <v-tab value="calendario" prepend-icon="mdi-calendar-week">Calendario</v-tab>
      <v-tab value="sala" prepend-icon="mdi-seat">Sala de espera</v-tab>
      <v-tab value="lista" prepend-icon="mdi-format-list-numbered">Lista de espera</v-tab>
    </v-tabs>

    <template v-if="tab === 'calendario'">
      <v-card border elevation="0" class="mb-3">
        <v-card-text class="d-flex align-center flex-wrap ga-3">
          <v-btn-toggle v-model="vista" mandatory density="comfortable" color="primary" variant="outlined" divided>
            <v-btn value="semana">Semana</v-btn>
            <v-btn value="dia">Día</v-btn>
          </v-btn-toggle>
          <v-btn-toggle v-if="vista === 'dia'" v-model="agrupar" mandatory density="comfortable" color="primary" variant="outlined" divided>
            <v-btn value="veterinario" prepend-icon="mdi-doctor">Por veterinario</v-btn>
            <v-btn value="sala" prepend-icon="mdi-door">Por sala</v-btn>
          </v-btn-toggle>
          <div class="d-flex align-center ga-1">
            <v-btn icon="mdi-chevron-left" variant="text" aria-label="Anterior" @click="mover(-1)" />
            <v-btn variant="tonal" @click="fecha = hoy()">Hoy</v-btn>
            <v-btn icon="mdi-chevron-right" variant="text" aria-label="Siguiente" @click="mover(1)" />
          </div>
          <v-text-field v-model="fecha" type="date" label="Ir a" style="max-width: 170px" />
          <v-select v-model="veterinarioId" :items="vetOptions" label="Veterinario" clearable style="max-width: 210px" />
          <v-select v-model="salaId" :items="salaOptions" label="Sala" clearable style="max-width: 190px" />
          <v-switch v-model="incluirCanceladas" label="Ver canceladas" color="primary" hide-details density="compact" />
          <v-progress-circular v-if="loading" indeterminate size="22" width="3" />
        </v-card-text>
      </v-card>

      <div class="text-subtitle-1 font-weight-bold mb-2">
        {{ etiquetaRango(rango.desde, rango.hasta) }}
        <span class="text-medium-emphasis text-body-2 ml-2">{{ citas.length }} cita(s)</span>
      </div>
      <AgendaGrid
        v-if="columnas.length"
        :columns="columnas"
        :readonly="!auth.can.agendaWrite"
        @slot="onSlot"
        @cita="abrirCita"
        @bloqueo="onBloqueo"
      />
      <v-alert v-else type="info" variant="tonal" text="No hay columnas para mostrar con los filtros actuales." />
      <div class="vet-info-bar mt-4 text-body-2">
        Haga clic en un hueco libre para crear una cita y en una cita para ver su detalle, cambiar su estado o
        iniciar la consulta. Las franjas rayadas son bloqueos de agenda.
      </div>
    </template>

    <SalaEsperaPanel v-else-if="tab === 'sala'" @changed="load" @abrir="abrirCita" />
    <ListaEsperaPanel v-else @changed="load" />

    <CitaDetalleDialog v-model="detalleDialog" :cita="detalle" @changed="onDetalleChanged" @editar="editar" />
    <CitaDialog v-model="citaDialog" :cita="citaEditando" :defaults="citaDefaults" @saved="load" />
    <BloqueoDialog v-model="bloqueoDialog" :defaults="bloqueoDefaults" @saved="load" />
    <ConfirmDialog
      :model-value="!!bloqueoEliminar"
      title="Eliminar bloqueo"
      :message="bloqueoEliminar ? `${bloqueoEliminar.tipo}${bloqueoEliminar.motivo ? ' - ' + bloqueoEliminar.motivo : ''} (${etiquetaInstante(bloqueoEliminar.inicio)} a ${etiquetaInstante(bloqueoEliminar.fin)}). ¿Desea eliminarlo?` : ''"
      confirm-text="Eliminar"
      @update:model-value="!$event && (bloqueoEliminar = null)"
      @confirm="eliminarBloqueo"
    />
  </div>
</template>
