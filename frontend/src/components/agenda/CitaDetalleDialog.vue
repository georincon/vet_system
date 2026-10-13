<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { cambiarEstadoCita } from '@/api/citas'
import { listConsultas } from '@/api/consultas'
import EstadoChip from '@/components/EstadoChip.vue'
import IniciarConsultaDialog from '@/components/agenda/IniciarConsultaDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaFecha, etiquetaInstante, hhmm } from '@/utils/fecha'
import type { Cita, Consulta } from '@/types'

const props = defineProps<{ modelValue: boolean; cita: Cita | null }>()
const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  changed: [cita?: Cita]
  editar: [cita: Cita]
}>()

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()

const trabajando = ref(false)
const error = ref('')
const cancelarDialog = ref(false)
const motivoCancelacion = ref('')
const iniciarDialog = ref(false)
const consultaId = ref<number | null>(null)

const codigo = computed(() => props.cita?.estadoCita?.codigo ?? '')
const abierta = computed(() => ['PROGRAMADA', 'CONFIRMADA', 'EN_SALA'].includes(codigo.value))

interface Accion { label: string; icon: string; estado: string; color?: string }
const ACCIONES: Record<string, Accion[]> = {
  PROGRAMADA: [
    { label: 'Confirmar', icon: 'mdi-check', estado: 'CONFIRMADA', color: 'primary' },
    { label: 'Check-in a sala de espera', icon: 'mdi-seat', estado: 'EN_SALA', color: 'secondary' },
  ],
  CONFIRMADA: [{ label: 'Check-in a sala de espera', icon: 'mdi-seat', estado: 'EN_SALA', color: 'secondary' }],
  EN_SALA: [],
  EN_CURSO: [],
}
const acciones = computed(() => ACCIONES[codigo.value] ?? [])

watch(
  () => [props.modelValue, props.cita?.id, codigo.value],
  async () => {
    error.value = ''
    consultaId.value = null
    if (!props.modelValue || !props.cita || !['EN_CURSO', 'ATENDIDA'].includes(codigo.value) || !auth.can.clinicaRead) return
    try {
      const page = await listConsultas(props.cita.fecha, props.cita.paciente.id, 0, 20)
      consultaId.value = page.items.find((c) => c.citaId === props.cita?.id)?.id ?? null
    } catch { /* sin acceso o sin consulta: el botón no se muestra */ }
  },
  { immediate: true },
)

async function cambiar(estado: string, motivo?: string) {
  if (!props.cita) return
  trabajando.value = true
  error.value = ''
  try {
    const cita = await cambiarEstadoCita(props.cita.id, estado, motivo)
    ui.showSuccess(`Cita ${cita.codigo}: ${cita.estadoCita.nombre}`)
    emit('changed', cita)
    cancelarDialog.value = false
    if (estado === 'CANCELADA' || estado === 'NO_ASISTIO') emit('update:modelValue', false)
  } catch (e) {
    const status = (e as { status?: number }).status
    error.value = status === 409
      ? `${errorText(e)}. Es posible que otra persona haya modificado la cita; se actualizó la agenda.`
      : errorText(e, 'No se pudo cambiar el estado')
    if (status === 409) emit('changed')
  } finally {
    trabajando.value = false
  }
}

function onIniciada(c: Consulta) {
  emit('changed')
  emit('update:modelValue', false)
  void router.push({ name: 'consulta', params: { id: c.id } })
}

function abrirConsulta() {
  if (consultaId.value) {
    emit('update:modelValue', false)
    void router.push({ name: 'consulta', params: { id: consultaId.value } })
  }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="560" @update:model-value="emit('update:modelValue', $event)">
    <v-card v-if="cita">
      <v-card-title class="pt-4 d-flex align-center ga-2 flex-wrap">
        <span>Cita {{ cita.codigo }}</span>
        <EstadoChip :estado="codigo" :label="cita.estadoCita.nombre" />
      </v-card-title>
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-list density="compact" class="py-0">
          <v-list-item prepend-icon="mdi-paw" :title="`${cita.paciente.nombre} (${cita.paciente.codigo})`"
            :subtitle="[cita.paciente.especie, cita.paciente.raza].filter(Boolean).join(' · ')" />
          <v-list-item prepend-icon="mdi-doctor" :title="cita.veterinario.nombre" subtitle="Veterinario" />
          <v-list-item prepend-icon="mdi-clock-outline"
            :title="`${etiquetaFecha(cita.fecha)} · ${hhmm(cita.horaInicio)} – ${hhmm(cita.horaFin)}`" />
          <v-list-item v-if="cita.tipoCita" prepend-icon="mdi-tag-outline"
            :title="cita.tipoCita.nombre" :subtitle="`${cita.tipoCita.duracionMinutos} min`" />
          <v-list-item v-if="cita.sala" prepend-icon="mdi-door" :title="cita.sala.nombre" subtitle="Sala" />
          <v-list-item v-if="cita.motivo" prepend-icon="mdi-text" :title="cita.motivo" subtitle="Motivo" />
          <v-list-item v-if="cita.notas" prepend-icon="mdi-note-text-outline" :title="cita.notas" subtitle="Notas" />
          <v-list-item v-if="cita.horaLlegada" prepend-icon="mdi-seat"
            :title="etiquetaInstante(cita.horaLlegada)" subtitle="Llegada (check-in)" />
        </v-list>
      </v-card-text>
      <v-card-actions class="px-4 pb-4 flex-wrap ga-2">
        <template v-if="auth.can.agendaWrite">
          <v-btn
            v-for="a in acciones"
            :key="a.estado"
            :color="a.color"
            variant="tonal"
            size="small"
            :prepend-icon="a.icon"
            :loading="trabajando"
            @click="cambiar(a.estado)"
          >{{ a.label }}</v-btn>
        </template>
        <v-btn
          v-if="codigo === 'EN_SALA' && auth.can.iniciarConsulta"
          color="primary"
          variant="flat"
          size="small"
          prepend-icon="mdi-stethoscope"
          @click="iniciarDialog = true"
        >Iniciar consulta</v-btn>
        <v-btn
          v-if="consultaId"
          color="primary"
          variant="flat"
          size="small"
          prepend-icon="mdi-open-in-new"
          @click="abrirConsulta"
        >Abrir consulta</v-btn>
        <template v-if="auth.can.agendaWrite && abierta">
          <v-btn size="small" variant="tonal" color="warning" prepend-icon="mdi-account-off"
            :loading="trabajando" @click="cambiar('NO_ASISTIO')">No asistió</v-btn>
          <v-btn size="small" variant="tonal" color="error" prepend-icon="mdi-close-circle-outline"
            @click="motivoCancelacion = ''; cancelarDialog = true">Cancelar cita</v-btn>
        </template>
        <v-spacer />
        <v-btn v-if="auth.can.agendaWrite && abierta" size="small" variant="text" prepend-icon="mdi-pencil"
          @click="emit('editar', cita)">Editar</v-btn>
        <v-btn size="small" variant="text" @click="emit('update:modelValue', false)">Cerrar</v-btn>
      </v-card-actions>
    </v-card>

    <v-dialog v-model="cancelarDialog" max-width="440">
      <v-card>
        <v-card-title class="pt-4">Cancelar cita</v-card-title>
        <v-card-text>
          <v-textarea v-model="motivoCancelacion" label="Motivo de cancelación (opcional)" rows="2" auto-grow />
        </v-card-text>
        <v-card-actions class="px-4 pb-4">
          <v-spacer />
          <v-btn variant="text" @click="cancelarDialog = false">Volver</v-btn>
          <v-btn color="error" variant="flat" :loading="trabajando" @click="cambiar('CANCELADA', motivoCancelacion)">
            Cancelar cita
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-dialog>

  <IniciarConsultaDialog v-model="iniciarDialog" :cita="cita" @iniciada="onIniciada" />
</template>
