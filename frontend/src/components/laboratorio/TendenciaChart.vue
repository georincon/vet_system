<script setup lang="ts">
import { computed } from 'vue'
import { BANDERA_META } from '@/utils/lab'
import BanderaChip from './BanderaChip.vue'
import { etiquetaInstante } from '@/utils/fecha'
import type { PuntoTendencia } from '@/types/etapa'

const props = defineProps<{ puntos: PuntoTendencia[]; unidad?: string | null }>()

const W = 640
const H = 260
const M = { l: 48, r: 16, t: 16, b: 40 }

interface Pt { x: number; y: number; p: PuntoTendencia; v: number }

const numericos = computed(() => props.puntos.filter((p) => p.valorNumerico != null))

const escala = computed(() => {
  const vals = numericos.value.map((p) => p.valorNumerico as number)
  const ref: number[] = []
  const ult = numericos.value[numericos.value.length - 1]
  if (ult?.refMin != null) ref.push(ult.refMin)
  if (ult?.refMax != null) ref.push(ult.refMax)
  const todos = [...vals, ...ref]
  if (!todos.length) return { min: 0, max: 1 }
  let min = Math.min(...todos)
  let max = Math.max(...todos)
  if (min === max) { min -= 1; max += 1 }
  const pad = (max - min) * 0.1
  return { min: min - pad, max: max + pad }
})

const sy = (v: number) => M.t + (H - M.t - M.b) * (1 - (v - escala.value.min) / (escala.value.max - escala.value.min))

const pts = computed<Pt[]>(() => {
  const n = numericos.value
  const t0 = n.length ? new Date(n[0].fecha).getTime() : 0
  const t1 = n.length ? new Date(n[n.length - 1].fecha).getTime() : 0
  return n.map((p, i) => {
    const t = new Date(p.fecha).getTime()
    const frac = n.length === 1 ? 0.5 : t1 === t0 ? i / (n.length - 1) : (t - t0) / (t1 - t0)
    return { x: M.l + (W - M.l - M.r) * frac, y: sy(p.valorNumerico as number), p, v: p.valorNumerico as number }
  })
})

const linea = computed(() => pts.value.map((q) => `${q.x},${q.y}`).join(' '))

const banda = computed(() => {
  const ult = numericos.value[numericos.value.length - 1]
  if (!ult || (ult.refMin == null && ult.refMax == null)) return null
  const top = sy(ult.refMax ?? escala.value.max)
  const bot = sy(ult.refMin ?? escala.value.min)
  return { y: Math.min(top, bot), h: Math.abs(bot - top), min: ult.refMin, max: ult.refMax }
})

const ticks = computed(() => {
  const { min, max } = escala.value
  return Array.from({ length: 5 }, (_, i) => {
    const v = min + ((max - min) * i) / 4
    return { y: sy(v), t: Number(v.toFixed(2)) }
  })
})

const fechasEje = computed(() => {
  const n = pts.value
  if (!n.length) return []
  const idx = n.length <= 4 ? n.map((_, i) => i) : [0, Math.floor(n.length / 2), n.length - 1]
  return idx.map((i) => ({ x: n[i].x, t: etiquetaInstante(n[i].p.fecha).slice(0, 11) }))
})

function color(p: PuntoTendencia): string {
  const b = p.bandera ? BANDERA_META[p.bandera] : null
  if (!b) return 'var(--vet-blue)'
  return b.color === 'error' ? 'var(--vet-danger)' : b.color === 'warning' ? 'var(--vet-warning)' : 'var(--vet-green)'
}
const grave = (p: PuntoTendencia) => !!p.bandera && BANDERA_META[p.bandera].grave
function titulo(p: PuntoTendencia): string {
  const b = p.bandera ? BANDERA_META[p.bandera].descripcion : 'sin bandera'
  return `${etiquetaInstante(p.fecha)}: ${p.valorNumerico} ${props.unidad ?? p.unidad ?? ''} (${b}) orden ${p.ordenCodigo}`
}
</script>

<template>
  <div>
    <div v-if="!numericos.length" class="text-medium-emphasis py-6 text-center">Sin resultados validados para este parámetro.</div>
    <template v-else>
      <div class="vet-grafico">
        <svg class="vet-svg" :viewBox="`0 0 ${W} ${H}`" role="img"
          :aria-label="`Tendencia con ${numericos.length} resultado(s). Consulte la tabla para los valores.`">
          <rect v-if="banda" class="vet-banda-ref" :x="M.l" :y="banda.y" :width="W - M.l - M.r" :height="banda.h">
            <title>Rango de referencia {{ banda.min ?? '' }} – {{ banda.max ?? '' }}</title>
          </rect>
          <g v-for="t in ticks" :key="t.y">
            <line class="vet-eje" :x1="M.l" :x2="W - M.r" :y1="t.y" :y2="t.y" />
            <text class="vet-eje-texto" :x="M.l - 6" :y="t.y + 3" text-anchor="end">{{ t.t }}</text>
          </g>
          <text v-for="f in fechasEje" :key="f.x" class="vet-eje-texto" :x="f.x" :y="H - 12" text-anchor="middle">{{ f.t }}</text>
          <polyline v-if="pts.length > 1" class="vet-linea-tendencia" :points="linea" />
          <g v-for="q in pts" :key="q.p.resultadoId">
            <rect v-if="grave(q.p)" class="vet-punto" :x="q.x - 6" :y="q.y - 6" width="12" height="12" :fill="color(q.p)"><title>{{ titulo(q.p) }}</title></rect>
            <circle v-else class="vet-punto" :cx="q.x" :cy="q.y" r="6" :fill="color(q.p)"><title>{{ titulo(q.p) }}</title></circle>
          </g>
        </svg>
      </div>
      <p class="text-caption text-medium-emphasis mt-1">Banda verde: rango de referencia. Círculo: dentro de límites o fuera de rango leve; cuadrado: valor crítico o anormal.</p>
      <v-table density="compact" class="mt-2">
        <caption class="vet-sr-only">Valores de la tendencia</caption>
        <thead><tr><th>Fecha</th><th>Orden</th><th>Valor</th><th>Bandera</th></tr></thead>
        <tbody>
          <tr v-for="p in puntos" :key="p.resultadoId">
            <td>{{ etiquetaInstante(p.fecha) }}</td>
            <td><router-link :to="{ name: 'orden-lab', params: { id: p.ordenId } }">{{ p.ordenCodigo }}</router-link></td>
            <td>{{ p.valorNumerico ?? p.valorTexto ?? '—' }} {{ p.unidad ?? unidad ?? '' }}</td>
            <td><BanderaChip :bandera="p.bandera" :critico="p.critico" /></td>
          </tr>
        </tbody>
      </v-table>
    </template>
  </div>
</template>
