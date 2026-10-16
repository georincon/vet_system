import type { Bandera, ParametroExamen, ResultadoExamen } from '@/types/etapa'

export interface BanderaMeta { texto: string; descripcion: string; color: string; icon: string; grave: boolean }

/** Banderas del informe de laboratorio. Color + icono + letra: nunca solo color (accesibilidad). */
export const BANDERA_META: Record<Bandera, BanderaMeta> = {
  N: { texto: 'N', descripcion: 'Normal', color: 'success', icon: 'mdi-check-circle-outline', grave: false },
  L: { texto: 'L', descripcion: 'Bajo', color: 'warning', icon: 'mdi-arrow-down-bold', grave: false },
  H: { texto: 'H', descripcion: 'Alto', color: 'warning', icon: 'mdi-arrow-up-bold', grave: false },
  LL: { texto: 'LL', descripcion: 'Crítico bajo', color: 'error', icon: 'mdi-arrow-collapse-down', grave: true },
  HH: { texto: 'HH', descripcion: 'Crítico alto', color: 'error', icon: 'mdi-arrow-collapse-up', grave: true },
  A: { texto: 'A', descripcion: 'Anormal', color: 'error', icon: 'mdi-alert-circle-outline', grave: true },
}

/** Rango ya resuelto por el backend (snapshot del resultado) o conocido por el catálogo. */
export interface RangoAplicado {
  refMin?: number | null
  refMax?: number | null
  refCriticoMin?: number | null
  refCriticoMax?: number | null
}

/** Convierte "12,5", "<3" o "> 40" en número (coma o punto decimal, prefijo < / > ignorado). */
export function numeroDe(texto: string | number | null | undefined): number | null {
  if (texto === null || texto === undefined) return null
  if (typeof texto === 'number') return Number.isFinite(texto) ? texto : null
  const limpio = texto.trim().replace(/^[<>]=?\s*/, '').replace(',', '.')
  if (limpio === '') return null
  const n = Number(limpio)
  return Number.isFinite(n) ? n : null
}

/**
 * Bandera en vivo mientras se digita, con el mismo criterio del backend (docs etapa A, 2.3):
 * LL/HH por debajo/encima del valor crítico, L/H fuera del rango, N normal; sin rango no hay bandera.
 * Es solo una ayuda visual: el backend recalcula y es la fuente de verdad.
 */
export function calcularBandera(valor: number | null, r: RangoAplicado): { bandera: Bandera | null; critico: boolean } {
  if (valor === null) return { bandera: null, critico: false }
  if (r.refCriticoMin != null && valor < r.refCriticoMin) return { bandera: 'LL', critico: true }
  if (r.refCriticoMax != null && valor > r.refCriticoMax) return { bandera: 'HH', critico: true }
  if (r.refMin != null && valor < r.refMin) return { bandera: 'L', critico: false }
  if (r.refMax != null && valor > r.refMax) return { bandera: 'H', critico: false }
  if (r.refMin == null && r.refMax == null) return { bandera: null, critico: false }
  return { bandera: 'N', critico: false }
}

/** Bandera de un parámetro cualitativo: A si el texto difiere del valor normal. */
export function banderaCualitativa(valor: string | null | undefined, normal: string | null | undefined): Bandera | null {
  if (!valor || !normal) return null
  return valor.trim().toUpperCase() === normal.trim().toUpperCase() ? 'N' : 'A'
}

/** "37 – 55", "≥ 37", "≤ 55" o "—". */
export function textoRango(r: RangoAplicado | undefined | null, normalTexto?: string | null): string {
  if (!r || (r.refMin == null && r.refMax == null)) return normalTexto ? `Normal: ${normalTexto}` : '—'
  if (r.refMin != null && r.refMax != null) return `${r.refMin} – ${r.refMax}`
  if (r.refMin != null) return `≥ ${r.refMin}`
  return `≤ ${r.refMax}`
}

export function valorMostrado(r: ResultadoExamen): string {
  if (r.valorNumerico != null) return `${r.valorTexto && /^[<>]/.test(r.valorTexto) ? r.valorTexto.match(/^[<>]=?/)?.[0] ?? '' : ''}${r.valorNumerico}`
  return r.valorTexto ?? '—'
}

export function opcionesDe(p: ParametroExamen): string[] {
  return (p.opciones ?? '').split('|').map((o) => o.trim()).filter(Boolean)
}

export const PRIORIDAD_META: Record<string, { color: string; icon: string; texto: string }> = {
  RUTINA: { color: 'grey', icon: 'mdi-minus', texto: 'Rutina' },
  URGENTE: { color: 'warning', icon: 'mdi-alert-outline', texto: 'Urgente' },
  STAT: { color: 'error', icon: 'mdi-flash-alert', texto: 'STAT' },
}
