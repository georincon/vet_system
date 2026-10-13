/** Utilidades de fecha en hora local (la clínica opera en America/Bogota, sin zona). */

const pad = (n: number) => String(n).padStart(2, '0')

export function isoFecha(d: Date): string {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

export function hoy(): string {
  return isoFecha(new Date())
}

export function parseFecha(iso: string): Date {
  const [y, m, d] = iso.slice(0, 10).split('-').map(Number)
  return new Date(y, m - 1, d)
}

export function sumarDias(iso: string, dias: number): string {
  const d = parseFecha(iso)
  d.setDate(d.getDate() + dias)
  return isoFecha(d)
}

/** Lunes de la semana de la fecha dada. */
export function inicioSemana(iso: string): string {
  const d = parseFecha(iso)
  const dow = (d.getDay() + 6) % 7
  return sumarDias(iso, -dow)
}

const DIAS = ['Dom', 'Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb']
const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']

export function etiquetaDia(iso: string): string {
  const d = parseFecha(iso)
  return `${DIAS[d.getDay()]} ${d.getDate()}`
}

export function etiquetaFecha(iso?: string | null): string {
  if (!iso) return '—'
  const d = parseFecha(iso)
  return `${d.getDate()} ${MESES[d.getMonth()]} ${d.getFullYear()}`
}

export function etiquetaRango(desde: string, hasta: string): string {
  return desde === hasta ? etiquetaFecha(desde) : `${etiquetaFecha(desde)} – ${etiquetaFecha(hasta)}`
}

export function hhmm(hora?: string | null): string {
  return hora ? hora.slice(0, 5) : '—'
}

export function aMinutos(hora: string): number {
  const [h, m] = hora.slice(0, 5).split(':').map(Number)
  return h * 60 + m
}

export function deMinutos(min: number): string {
  const m = Math.max(0, Math.min(min, 24 * 60 - 1))
  return `${pad(Math.floor(m / 60))}:${pad(m % 60)}`
}

/** Instante ISO UTC (o local sin zona) a "dd mmm yyyy HH:mm" en hora local del navegador. */
export function etiquetaInstante(iso?: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return `${d.getDate()} ${MESES[d.getMonth()]} ${d.getFullYear()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

export function minutosDesde(iso?: string | null): number | null {
  if (!iso) return null
  const t = new Date(iso).getTime()
  return Number.isNaN(t) ? null : Math.max(0, Math.round((Date.now() - t) / 60000))
}
