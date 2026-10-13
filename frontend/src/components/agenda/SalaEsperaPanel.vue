<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getAgenda } from '@/api/agenda'
import { cambiarEstadoCita } from '@/api/citas'
import { listConsultas } from '@/api/consultas'
import EstadoChip from '@/components/EstadoChip.vue'
import IniciarConsultaDialog from '@/components/agenda/IniciarConsultaDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { hhmm, hoy, minutosDesde } from '@/utils/fecha'
import type { Cita, Consulta } from '@/types'

const emit = defineEmits<{ changed: []; abrir: [cita: Cita] }>()
const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()

const citas = ref<Cita[]>([])
const consultasHoy = ref<Consulta[]>([])
const loading = ref(false)
const iniciar = ref<Cita | null>(null)
const iniciarDialog = ref(false)
let timer: number | undefined

const enSala = computed(() =>
  citas.value.filter((c) => c.estadoCita.codigo === 'EN_SALA')
    .sort((a, b) => (a.horaLlegada ?? '').localeCompare(b.horaLlegada ?? '')),
)
const porLlegar = computed(() => citas.value.filter((c) => ['PROGRAMADA', 'CONFIRMADA'].includes(c.estadoCita.codigo)))
const enConsulta = computed(() => citas.value.filter((c) => c.estadoCita.codigo === 'EN_CURSO'))

function consultaDe(c: Cita): Consulta | undefined {
  return consultasHoy.value.find((x) => x.citaId === c.id)
}

async function load() {
  loading.value = true
  try {
    const fecha = hoy()
    const agenda = await getAgenda({ desde: fecha, hasta: fecha })
    citas.value = agenda.citas
    if (auth.can.clinicaRead) consultasHoy.value = (await listConsultas(fecha, undefined, 0, 100)).items
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo cargar la sala de espera'))
  } finally {
    loading.value = false
  }
}

async function checkIn(c: Cita) {
  try {
    await cambiarEstadoCita(c.id, 'EN_SALA')
    ui.showSuccess(`${c.paciente.nombre} está en la sala de espera`)
    await load()
    emit('changed')
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo registrar la llegada'))
    await load()
  }
}

function onIniciada(c: Consulta) {
  emit('changed')
  void router.push({ name: 'consulta', params: { id: c.id } })
}

function espera(c: Cita): string {
  const m = minutosDesde(c.horaLlegada)
  return m === null ? '—' : m < 60 ? `${m} min` : `${Math.floor(m / 60)} h ${m % 60} min`
}

onMounted(() => { void load(); timer = window.setInterval(load, 60000) })
onBeforeUnmount(() => window.clearInterval(timer))
defineExpose({ load })
</script>

<template>
  <div>
    <div class="d-flex align-center mb-2">
      <h2 class="vet-section-title flex-grow-1 mb-0">En sala de espera ({{ enSala.length }})</h2>
      <v-btn variant="text" icon="mdi-refresh" :loading="loading" title="Actualizar" @click="load" />
    </div>
    <v-card border elevation="0" class="mb-6">
      <v-list v-if="enSala.length" lines="two">
        <v-list-item v-for="c in enSala" :key="c.id" :title="`${c.paciente.nombre} · ${c.paciente.especie}`"
          :subtitle="`${c.veterinario.nombre} · cita ${hhmm(c.horaInicio)}${c.motivo ? ' · ' + c.motivo : ''}`"
          prepend-icon="mdi-seat">
          <template #append>
            <v-chip size="small" color="warning" variant="tonal" prepend-icon="mdi-timer-sand" class="mr-2">
              Espera {{ espera(c) }}
            </v-chip>
            <v-btn v-if="auth.can.iniciarConsulta" size="small" color="primary" variant="flat"
              prepend-icon="mdi-stethoscope" @click="iniciar = c; iniciarDialog = true">Iniciar consulta</v-btn>
            <v-btn size="small" variant="text" icon="mdi-eye-outline" title="Ver cita" @click="emit('abrir', c)" />
          </template>
        </v-list-item>
      </v-list>
      <v-card-text v-else class="text-medium-emphasis">No hay pacientes esperando.</v-card-text>
    </v-card>

    <h2 class="vet-section-title">En consulta ({{ enConsulta.length }})</h2>
    <v-card border elevation="0" class="mb-6">
      <v-list v-if="enConsulta.length" lines="two">
        <v-list-item v-for="c in enConsulta" :key="c.id" :title="`${c.paciente.nombre} · ${c.paciente.especie}`"
          :subtitle="`${c.veterinario.nombre} · ${hhmm(c.horaInicio)}`" prepend-icon="mdi-stethoscope">
          <template #append>
            <v-btn v-if="consultaDe(c)" size="small" color="primary" variant="tonal" prepend-icon="mdi-open-in-new"
              :to="{ name: 'consulta', params: { id: consultaDe(c)!.id } }">Abrir consulta</v-btn>
          </template>
        </v-list-item>
      </v-list>
      <v-card-text v-else class="text-medium-emphasis">Nadie en consulta.</v-card-text>
    </v-card>

    <h2 class="vet-section-title">Por llegar hoy ({{ porLlegar.length }})</h2>
    <v-card border elevation="0">
      <v-list v-if="porLlegar.length" lines="two">
        <v-list-item v-for="c in porLlegar" :key="c.id" :title="`${hhmm(c.horaInicio)} · ${c.paciente.nombre}`"
          :subtitle="`${c.veterinario.nombre}${c.motivo ? ' · ' + c.motivo : ''}`" prepend-icon="mdi-calendar-clock">
          <template #append>
            <EstadoChip :estado="c.estadoCita.codigo" class="mr-2" />
            <v-btn v-if="auth.can.agendaWrite" size="small" color="secondary" variant="tonal" prepend-icon="mdi-seat"
              @click="checkIn(c)">Check-in</v-btn>
            <v-btn size="small" variant="text" icon="mdi-eye-outline" title="Ver cita" @click="emit('abrir', c)" />
          </template>
        </v-list-item>
      </v-list>
      <v-card-text v-else class="text-medium-emphasis">No hay citas pendientes de llegada.</v-card-text>
    </v-card>

    <IniciarConsultaDialog v-model="iniciarDialog" :cita="iniciar" @iniciada="onIniciada" />
  </div>
</template>
