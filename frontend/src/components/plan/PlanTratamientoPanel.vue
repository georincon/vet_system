<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { actualizarControl, actualizarPlan, cancelarControl, crearControl, crearPlan, getPlan } from '@/api/formula'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaFecha, hoy } from '@/utils/fecha'
import { TIPOS_ITEM_PLAN, type ControlSeguimiento, type PlanItem, type PlanTratamiento } from '@/types/etapa'

const props = defineProps<{ consultaId: number; cerrada: boolean; canWrite: boolean }>()
const emit = defineEmits<{ changed: []; seguimiento: [] }>()
const ui = useUiStore()

const plan = ref<PlanTratamiento | null>(null)
const loading = ref(true)
const dialog = ref(false)
const saving = ref(false)
const error = ref('')
const form = ref({ objetivo: '', recomendaciones: '', advertencias: '', estado: 'ACTIVO' as PlanTratamiento['estado'] })
const items = ref<PlanItem[]>([])

const puedeEditarPlan = computed(() => props.canWrite && !props.cerrada)
const fases = computed(() => {
  const m = new Map<string, PlanItem[]>()
  for (const i of plan.value?.items ?? []) {
    const k = i.fase?.trim() || 'Sin fase'
    m.set(k, [...(m.get(k) ?? []), i])
  }
  return [...m.entries()]
})

async function load() {
  loading.value = true
  try { plan.value = await getPlan(props.consultaId) }
  catch (e) { ui.showError(errorText(e, 'No se pudo cargar el plan de tratamiento')) }
  finally { loading.value = false }
}

const itemVacio = (n: number): PlanItem => ({ orden: n, fase: `Fase ${n}`, tipo: 'MEDICACION', descripcion: '', fechaInicio: null, fechaFin: null })
function abrir() {
  error.value = ''
  const p = plan.value
  form.value = { objetivo: p?.objetivo ?? '', recomendaciones: p?.recomendaciones ?? '', advertencias: p?.advertencias ?? '', estado: p?.estado ?? 'ACTIVO' }
  items.value = p?.items.length ? p.items.map((i) => ({ ...i })) : [itemVacio(1)]
  dialog.value = true
}
async function guardar() {
  const validos = items.value.filter((i) => i.descripcion.trim())
  for (const i of validos) {
    if (i.fechaInicio && i.fechaFin && i.fechaFin < i.fechaInicio) { error.value = 'La fecha de fin no puede ser anterior a la de inicio'; return }
  }
  saving.value = true
  error.value = ''
  try {
    const body = {
      objetivo: form.value.objetivo.trim(), recomendaciones: form.value.recomendaciones.trim(), advertencias: form.value.advertencias.trim(),
      estado: form.value.estado,
      items: validos.map((i, idx) => ({
        orden: idx + 1, fase: i.fase?.trim() || undefined, tipo: i.tipo ?? 'OTRO', descripcion: i.descripcion.trim(),
        fechaInicio: i.fechaInicio || undefined, fechaFin: i.fechaFin || undefined,
      })),
    }
    plan.value = plan.value ? await actualizarPlan(props.consultaId, body) : await crearPlan(props.consultaId, body)
    dialog.value = false
    ui.showSuccess('Plan de tratamiento guardado')
    emit('changed')
  } catch (e) { error.value = errorText(e, 'No se pudo guardar el plan') } finally { saving.value = false }
}

// ---- controles
const ctrlDialog = ref(false)
const ctrl = ref({ fechaSugerida: '', motivo: '', generarRecordatorio: true })
const ctrlError = ref('')
const ctrlResultado = ref<ControlSeguimiento | null>(null)
const cancelar = ref<ControlSeguimiento | null>(null)
function abrirControl() {
  ctrl.value = { fechaSugerida: '', motivo: '', generarRecordatorio: true }
  ctrlError.value = ''; ctrlResultado.value = null
  ctrlDialog.value = true
}
async function guardarControl() {
  if (!ctrl.value.fechaSugerida || !ctrl.value.motivo.trim()) { ctrlError.value = 'Indique la fecha y el motivo del control'; return }
  saving.value = true
  ctrlError.value = ''
  try {
    ctrlResultado.value = await crearControl(props.consultaId, {
      fechaSugerida: ctrl.value.fechaSugerida, motivo: ctrl.value.motivo.trim(), generarRecordatorio: ctrl.value.generarRecordatorio,
    })
    ui.showSuccess('Control registrado')
    await load()
    emit('changed')
    if (props.cerrada) emit('seguimiento')
  } catch (e) { ctrlError.value = errorText(e, 'No se pudo registrar el control') } finally { saving.value = false }
}
async function marcarRealizado(c: ControlSeguimiento) {
  try { await actualizarControl(props.consultaId, c.id, { estado: 'REALIZADO' }); await load() }
  catch (e) { ui.showError(errorText(e, 'No se pudo actualizar el control')) }
}
async function cancelarCtrl() {
  if (!cancelar.value) return
  try { await cancelarControl(props.consultaId, cancelar.value.id); cancelar.value = null; await load() }
  catch (e) { ui.showError(errorText(e, 'No se pudo cancelar el control')); cancelar.value = null }
}
function rutaCita(c: ControlSeguimiento) {
  const s = c.citaSugerida
  if (!s) return null
  return { path: '/agenda', query: { nuevaCita: '1', pacienteId: String(s.pacienteId), fecha: s.fecha, veterinarioId: s.veterinarioId ? String(s.veterinarioId) : undefined, motivo: s.motivo ?? undefined } }
}

watch(() => props.consultaId, load)
onMounted(load)
defineExpose({ load })
</script>

<template>
  <div>
    <div class="d-flex align-center mb-2 ga-2 flex-wrap">
      <strong class="flex-grow-1">Plan de tratamiento y controles</strong>
      <v-btn v-if="puedeEditarPlan" size="small" color="primary" variant="tonal" :prepend-icon="plan ? 'mdi-pencil' : 'mdi-plus'" @click="abrir">
        {{ plan ? 'Editar plan' : 'Crear plan' }}</v-btn>
    </div>
    <v-progress-linear v-if="loading" indeterminate color="primary" />
    <v-alert v-if="cerrada" type="info" variant="tonal" density="compact" class="mb-3" icon="mdi-lock-outline">
      Consulta cerrada: el plan es de solo lectura. Los controles de seguimiento sí pueden registrarse y quedan como addendum de SEGUIMIENTO.
    </v-alert>

    <template v-if="plan">
      <div class="d-flex flex-wrap ga-2 mb-2"><EstadoChip :estado="plan.estado" /></div>
      <p v-if="plan.objetivo" class="mb-1"><strong>Objetivo:</strong> {{ plan.objetivo }}</p>
      <p v-if="plan.recomendaciones" class="mb-1"><strong>Recomendaciones:</strong> {{ plan.recomendaciones }}</p>
      <p v-if="plan.advertencias" class="mb-2"><v-icon icon="mdi-alert-outline" size="small" color="warning" /> <strong>Advertencias:</strong> {{ plan.advertencias }}</p>
      <v-card v-for="[fase, its] in fases" :key="fase" border elevation="0" class="mb-2">
        <v-card-text class="py-2">
          <div class="font-weight-bold mb-1">{{ fase }}</div>
          <div v-for="i in its" :key="i.id" class="d-flex flex-wrap ga-2 align-center text-body-2">
            <v-chip size="x-small" variant="tonal" :text="TIPOS_ITEM_PLAN.find((t) => t.value === i.tipo)?.title ?? i.tipo ?? ''" />
            <span>{{ i.descripcion }}</span>
            <span v-if="i.fechaInicio" class="text-caption text-medium-emphasis">{{ etiquetaFecha(i.fechaInicio) }}<template v-if="i.fechaFin"> – {{ etiquetaFecha(i.fechaFin) }}</template></span>
          </div>
        </v-card-text>
      </v-card>
    </template>
    <div v-else-if="!loading" class="text-medium-emphasis text-body-2 mb-3">Sin plan de tratamiento.</div>

    <div class="d-flex align-center mt-3 mb-1">
      <strong class="flex-grow-1">Controles de seguimiento</strong>
      <v-btn v-if="canWrite" size="small" variant="tonal" prepend-icon="mdi-calendar-plus" @click="abrirControl">Nuevo control</v-btn>
    </div>
    <v-table v-if="plan?.controles.length" density="compact">
      <thead><tr><th>Fecha</th><th>Motivo</th><th>Estado</th><th>Recordatorio</th><th></th></tr></thead>
      <tbody>
        <tr v-for="c in plan.controles" :key="c.id">
          <td>{{ etiquetaFecha(c.fechaSugerida) }}</td><td>{{ c.motivo }}</td><td><EstadoChip :estado="c.estado" /></td>
          <td class="text-caption">{{ c.recordatorio?.solicitado ? (c.recordatorio.generado ? 'Programado' : c.recordatorio.detalle ?? 'No generado') : '—' }}</td>
          <td class="text-no-wrap">
            <v-btn v-if="c.estado === 'PENDIENTE' && rutaCita(c)" size="x-small" variant="tonal" color="primary" prepend-icon="mdi-calendar-plus" :to="rutaCita(c)!">Agendar cita</v-btn>
            <template v-if="canWrite && (c.estado === 'PENDIENTE' || c.estado === 'AGENDADO')">
              <v-btn size="x-small" variant="text" icon="mdi-check" title="Marcar realizado" aria-label="Marcar realizado" @click="marcarRealizado(c)" />
              <v-btn size="x-small" variant="text" icon="mdi-cancel" color="error" title="Cancelar control" aria-label="Cancelar control" @click="cancelar = c" />
            </template>
          </td>
        </tr>
      </tbody>
    </v-table>
    <div v-else-if="!loading" class="text-medium-emphasis text-body-2">Sin controles.</div>

    <v-dialog v-model="dialog" max-width="900" scrollable>
      <v-card>
        <v-card-title class="pt-4">{{ plan ? 'Editar plan de tratamiento' : 'Nuevo plan de tratamiento' }}</v-card-title>
        <v-card-text>
          <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
          <v-text-field v-model="form.objetivo" label="Objetivo" class="mb-2" />
          <v-textarea v-model="form.recomendaciones" label="Recomendaciones" rows="2" auto-grow class="mb-2" />
          <v-textarea v-model="form.advertencias" label="Advertencias (signos de alarma)" rows="2" auto-grow class="mb-2" />
          <v-select v-if="plan" v-model="form.estado" label="Estado" :items="['ACTIVO', 'COMPLETADO', 'CANCELADO']" class="mb-2" />
          <h3 class="vet-section-title">Fases e ítems</h3>
          <div v-for="(it, idx) in items" :key="idx" class="border rounded pa-3 mb-3">
            <v-row dense>
              <v-col cols="6" md="3"><v-text-field v-model="it.fase" label="Fase" /></v-col>
              <v-col cols="6" md="3"><v-select v-model="it.tipo" :items="TIPOS_ITEM_PLAN" label="Tipo" /></v-col>
              <v-col cols="6" md="3"><v-text-field v-model="it.fechaInicio" type="date" label="Inicio" /></v-col>
              <v-col cols="6" md="3"><v-text-field v-model="it.fechaFin" type="date" label="Fin" :min="it.fechaInicio ?? undefined" /></v-col>
              <v-col cols="10"><v-text-field v-model="it.descripcion" label="Descripción" /></v-col>
              <v-col cols="2" class="d-flex align-center"><v-btn icon="mdi-delete-outline" variant="text" color="error" title="Quitar ítem" aria-label="Quitar ítem" @click="items.splice(idx, 1)" /></v-col>
            </v-row>
          </div>
          <v-btn size="small" variant="tonal" prepend-icon="mdi-plus" @click="items.push(itemVacio(items.length + 1))">Agregar ítem</v-btn>
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer /><v-btn variant="text" @click="dialog = false">Cancelar</v-btn>
          <v-btn color="primary" variant="flat" :loading="saving" @click="guardar">Guardar plan</v-btn></v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="ctrlDialog" max-width="520">
      <v-card>
        <v-card-title class="pt-4">Control de seguimiento</v-card-title>
        <v-card-text>
          <v-alert v-if="ctrlError" type="error" variant="tonal" density="compact" class="mb-3" :text="ctrlError" />
          <template v-if="!ctrlResultado">
            <v-alert v-if="cerrada" type="info" variant="tonal" density="compact" class="mb-3" text="La consulta está cerrada: se registrará un addendum de SEGUIMIENTO." />
            <v-text-field v-model="ctrl.fechaSugerida" type="date" label="Fecha sugerida" :min="hoy()" class="mb-3" />
            <v-text-field v-model="ctrl.motivo" label="Motivo" maxlength="300" class="mb-2" />
            <v-checkbox v-model="ctrl.generarRecordatorio" label="Generar recordatorio al tutor (correo, un día antes)" hide-details />
          </template>
          <template v-else>
            <v-alert type="success" variant="tonal" density="compact" class="mb-2" text="Control registrado." />
            <v-alert v-if="ctrlResultado.recordatorio?.solicitado" :type="ctrlResultado.recordatorio.generado ? 'success' : 'warning'" variant="tonal" density="compact" class="mb-2"
              :text="ctrlResultado.recordatorio.generado ? 'Recordatorio programado.' : `Recordatorio no generado: ${ctrlResultado.recordatorio.detalle ?? 'sin detalle'}`" />
            <v-btn v-if="rutaCita(ctrlResultado)" color="primary" variant="tonal" prepend-icon="mdi-calendar-plus" :to="rutaCita(ctrlResultado)!">Crear la cita ahora (precargada en la Agenda)</v-btn>
          </template>
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer />
          <v-btn variant="text" @click="ctrlDialog = false">{{ ctrlResultado ? 'Cerrar' : 'Cancelar' }}</v-btn>
          <v-btn v-if="!ctrlResultado" color="primary" variant="flat" :loading="saving" @click="guardarControl">Registrar control</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
    <ConfirmDialog :model-value="!!cancelar" title="Cancelar control" message="El control quedará CANCELADO (no se borra). ¿Continuar?" confirm-text="Cancelar control"
      @update:model-value="!$event && (cancelar = null)" @confirm="cancelarCtrl" />
  </div>
</template>
