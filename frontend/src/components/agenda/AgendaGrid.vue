<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { BloqueoAgenda, Cita } from '@/types'
import { aMinutos, deMinutos, hhmm } from '@/utils/fecha'

export interface GridColumn {
  key: string
  title: string
  subtitle?: string
  fecha: string
  veterinarioId?: number | null
  salaId?: number | null
  esHoy?: boolean
  citas: Cita[]
  bloqueos: BloqueoAgenda[]
}

const props = withDefaults(
  defineProps<{ columns: GridColumn[]; startHour?: number; endHour?: number; readonly?: boolean }>(),
  { startHour: 7, endHour: 20, readonly: false },
)

const emit = defineEmits<{
  slot: [column: GridColumn, hora: string]
  cita: [cita: Cita]
  bloqueo: [bloqueo: BloqueoAgenda]
}>()

const SLOT_MIN = 30
const SLOT_PX = 30
const PX_MIN = SLOT_PX / SLOT_MIN

const inicioMin = computed(() => props.startHour * 60)
const finMin = computed(() => props.endHour * 60)
const alto = computed(() => (finMin.value - inicioMin.value) * PX_MIN)
const horas = computed(() =>
  Array.from({ length: props.endHour - props.startHour }, (_, i) => props.startHour + i),
)

const ahora = ref(new Date())
let timer: number | undefined
onMounted(() => { timer = window.setInterval(() => (ahora.value = new Date()), 60000) })
onBeforeUnmount(() => window.clearInterval(timer))
const ahoraTop = computed(() => {
  const m = ahora.value.getHours() * 60 + ahora.value.getMinutes()
  return m >= inicioMin.value && m <= finMin.value ? (m - inicioMin.value) * PX_MIN : null
})

interface Pos { top: number; height: number; left: string; width: string }

function posicion(ini: number, fin: number, lane = 0, lanes = 1): Pos {
  const a = Math.max(ini, inicioMin.value)
  const b = Math.min(fin, finMin.value)
  return {
    top: (a - inicioMin.value) * PX_MIN,
    height: Math.max((b - a) * PX_MIN, 18),
    left: `calc(${(lane / lanes) * 100}% + 2px)`,
    width: `calc(${100 / lanes}% - 4px)`,
  }
}

interface CitaPos { cita: Cita; pos: Pos }

/** Reparte en carriles las citas que se solapan dentro de una misma columna. */
function citasPosicionadas(citas: Cita[]): CitaPos[] {
  const ordenadas = [...citas]
    .filter((c) => c.horaInicio)
    .sort((a, b) => aMinutos(a.horaInicio) - aMinutos(b.horaInicio))
  const res: CitaPos[] = []
  let grupo: { cita: Cita; lane: number; ini: number; fin: number }[] = []
  let finGrupo = -1
  const cerrar = () => {
    const lanes = Math.max(1, ...grupo.map((g) => g.lane + 1))
    grupo.forEach((g) => res.push({ cita: g.cita, pos: posicion(g.ini, g.fin, g.lane, lanes) }))
    grupo = []
  }
  for (const c of ordenadas) {
    const ini = aMinutos(c.horaInicio)
    const fin = c.horaFin ? aMinutos(c.horaFin) : ini + 30
    if (grupo.length && ini >= finGrupo) cerrar()
    const ocupados = new Set(grupo.filter((g) => g.fin > ini).map((g) => g.lane))
    let lane = 0
    while (ocupados.has(lane)) lane++
    grupo.push({ cita: c, lane, ini, fin })
    finGrupo = Math.max(grupo.length === 1 ? 0 : finGrupo, fin)
  }
  if (grupo.length) cerrar()
  return res
}

/** Bloqueo recortado al día de la columna. */
function bloqueoPos(b: BloqueoAgenda, fecha: string): Pos | null {
  const dIni = b.inicio.slice(0, 10)
  const dFin = b.fin.slice(0, 10)
  if (fecha < dIni || fecha > dFin) return null
  const ini = fecha === dIni ? aMinutos(b.inicio.slice(11, 16)) : 0
  const fin = fecha === dFin ? aMinutos(b.fin.slice(11, 16)) : 24 * 60
  if (fin <= inicioMin.value || ini >= finMin.value) return null
  return posicion(ini, fin)
}

const columnasCalculadas = computed(() =>
  props.columns.map((col) => ({
    col,
    citas: citasPosicionadas(col.citas),
    bloqueos: col.bloqueos
      .map((b) => ({ b, pos: bloqueoPos(b, col.fecha) }))
      .filter((x): x is { b: BloqueoAgenda; pos: Pos } => x.pos !== null),
  })),
)

function colorCita(c: Cita): string {
  return c.tipoCita?.color || '#006837'
}

function estiloCita(c: Cita, pos: Pos): Record<string, string> {
  return {
    top: `${pos.top}px`, height: `${pos.height}px`, left: pos.left, width: pos.width,
    '--cita-color': colorCita(c),
  }
}

function onSlot(ev: MouseEvent, col: GridColumn) {
  if (props.readonly) return
  const el = ev.currentTarget as HTMLElement
  const y = ev.clientY - el.getBoundingClientRect().top
  const min = inicioMin.value + Math.floor(y / PX_MIN / SLOT_MIN) * SLOT_MIN
  emit('slot', col, deMinutos(min))
}

const ESTADO_ICON: Record<string, string> = {
  CONFIRMADA: 'mdi-check', EN_SALA: 'mdi-seat', EN_CURSO: 'mdi-stethoscope',
  ATENDIDA: 'mdi-check-all', CANCELADA: 'mdi-close', NO_ASISTIO: 'mdi-account-off',
}
</script>

<template>
  <div class="agenda-grid" role="grid" aria-label="Agenda">
    <div class="agenda-head">
      <div class="agenda-gutter" />
      <div
        v-for="col in columns"
        :key="col.key"
        class="agenda-colhead"
        :class="{ 'agenda-colhead--hoy': col.esHoy }"
      >
        <div class="agenda-colhead__title">{{ col.title }}</div>
        <div v-if="col.subtitle" class="agenda-colhead__sub">{{ col.subtitle }}</div>
      </div>
    </div>
    <div class="agenda-body">
      <div class="agenda-gutter" :style="{ height: alto + 'px' }">
        <div v-for="h in horas" :key="h" class="agenda-hora" :style="{ height: SLOT_PX * (60 / SLOT_MIN) + 'px' }">
          {{ String(h).padStart(2, '0') }}:00
        </div>
      </div>
      <div
        v-for="item in columnasCalculadas"
        :key="item.col.key"
        class="agenda-col"
        :class="{ 'agenda-col--hoy': item.col.esHoy, 'agenda-col--ro': readonly }"
        :style="{ height: alto + 'px', '--slot-px': SLOT_PX + 'px' }"
        @click="onSlot($event, item.col)"
      >
        <div
          v-for="x in item.bloqueos"
          :key="'b' + x.b.id"
          class="agenda-bloqueo"
          :style="{ top: x.pos.top + 'px', height: x.pos.height + 'px' }"
          :title="`${x.b.tipo}${x.b.motivo ? ': ' + x.b.motivo : ''}`"
          @click.stop="emit('bloqueo', x.b)"
        >
          <v-icon icon="mdi-cancel" size="x-small" /> {{ x.b.tipo }}<span v-if="x.b.motivo"> · {{ x.b.motivo }}</span>
        </div>
        <button
          v-for="x in item.citas"
          :key="'c' + x.cita.id"
          type="button"
          class="agenda-cita"
          :class="'agenda-cita--' + x.cita.estadoCita?.codigo"
          :style="estiloCita(x.cita, x.pos)"
          :title="`${hhmm(x.cita.horaInicio)} ${x.cita.paciente?.nombre} - ${x.cita.estadoCita?.nombre}`"
          @click.stop="emit('cita', x.cita)"
        >
          <span class="agenda-cita__hora">
            <v-icon v-if="ESTADO_ICON[x.cita.estadoCita?.codigo]" :icon="ESTADO_ICON[x.cita.estadoCita.codigo]" size="x-small" />
            {{ hhmm(x.cita.horaInicio) }}
          </span>
          <strong>{{ x.cita.paciente?.nombre }}</strong>
          <span v-if="x.pos.height > 44" class="agenda-cita__meta">
            {{ x.cita.tipoCita?.nombre || x.cita.motivo || '' }}
          </span>
        </button>
        <div v-if="item.col.esHoy && ahoraTop !== null" class="agenda-ahora" :style="{ top: ahoraTop + 'px' }" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.agenda-grid {
  border: 1px solid var(--vet-border);
  border-radius: 8px;
  overflow: auto;
  max-height: 70vh;
  background: #fff;
}
.agenda-head {
  display: flex;
  position: sticky;
  top: 0;
  z-index: 3;
  background: var(--vet-gray);
  border-bottom: 1px solid var(--vet-border);
  min-width: max-content;
  width: 100%;
}
.agenda-body { display: flex; min-width: max-content; width: 100%; }
.agenda-gutter { width: 56px; flex: 0 0 56px; position: sticky; left: 0; z-index: 2; background: #fff; }
.agenda-head .agenda-gutter { background: var(--vet-gray); }
.agenda-hora {
  font-size: 11px; color: var(--vet-text-light); text-align: right; padding-right: 6px;
  box-sizing: border-box; transform: translateY(-7px);
}
.agenda-colhead {
  flex: 1 1 0; min-width: 150px; padding: 8px 6px; text-align: center;
  border-left: 1px solid var(--vet-border);
}
.agenda-colhead__title { font-size: 12px; font-weight: 700; text-transform: uppercase; letter-spacing: .5px; }
.agenda-colhead__sub { font-size: 11px; color: var(--vet-text-light); }
.agenda-colhead--hoy { background: var(--vet-green-light); color: var(--vet-green); }
.agenda-col {
  flex: 1 1 0; min-width: 150px; position: relative; cursor: pointer;
  border-left: 1px solid var(--vet-border);
  background-image:
    linear-gradient(to bottom, var(--vet-border) 1px, transparent 1px),
    linear-gradient(to bottom, var(--vet-border-light) 1px, transparent 1px);
  background-size: 100% calc(var(--slot-px) * 2), 100% var(--slot-px);
}
.agenda-col:hover { background-color: var(--vet-gray-light); }
.agenda-col--hoy { background-color: #fbfdfb; }
.agenda-col--ro { cursor: default; }
.agenda-cita {
  position: absolute; z-index: 1; box-sizing: border-box; overflow: hidden; text-align: left;
  display: flex; flex-direction: column; gap: 0; padding: 2px 6px;
  font-size: 12px; line-height: 1.25; color: var(--vet-text); cursor: pointer;
  border: 1px solid color-mix(in srgb, var(--cita-color) 45%, #fff);
  border-left: 4px solid var(--cita-color);
  background: color-mix(in srgb, var(--cita-color) 14%, #fff);
  border-radius: 4px;
}
.agenda-cita:hover { box-shadow: 0 2px 8px rgba(0,0,0,.18); z-index: 2; }
.agenda-cita:focus-visible { outline: 2px solid var(--vet-blue); }
.agenda-cita__hora, .agenda-cita__meta { font-size: 11px; color: var(--vet-text-light); }
.agenda-cita--CANCELADA, .agenda-cita--NO_ASISTIO, .agenda-cita--REAGENDADA { opacity: .55; text-decoration: line-through; }
.agenda-cita--ATENDIDA { background: #f1f1f1; border-left-color: var(--vet-text-light); }
.agenda-cita--EN_SALA { box-shadow: 0 0 0 2px var(--vet-gold) inset; }
.agenda-bloqueo {
  position: absolute; left: 0; right: 0; z-index: 0; overflow: hidden; padding: 2px 6px;
  font-size: 11px; color: var(--vet-text-light);
  background: repeating-linear-gradient(135deg, #eee, #eee 6px, #f8f8f8 6px, #f8f8f8 12px);
  border-top: 1px solid var(--vet-border); border-bottom: 1px solid var(--vet-border);
}
.agenda-ahora { position: absolute; left: 0; right: 0; height: 2px; background: var(--vet-danger); z-index: 2; pointer-events: none; }
</style>
