import { http } from '@/api/client'

/** Descarga un recurso autenticado (Bearer) como Blob. `ruta` es relativa a /api (p. ej. `/archivos/7/contenido`). */
export async function obtenerBlob(ruta: string, params?: Record<string, unknown>): Promise<Blob> {
  const { data } = await http.get<Blob>(ruta, { params, responseType: 'blob' })
  return data
}

/** Guarda un Blob en disco con el nombre dado. */
export function guardarBlob(blob: Blob, nombre: string): void {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = nombre
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  a.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 10_000)
}

/** Descarga con Bearer y guarda el archivo. */
export async function descargar(ruta: string, nombre: string, params?: Record<string, unknown>): Promise<void> {
  guardarBlob(await obtenerBlob(ruta, params), nombre)
}

/**
 * Abre un PDF autenticado en una pestaña nueva (blob con Bearer). Devuelve false si el navegador bloqueó la ventana,
 * en cuyo caso se guarda como descarga.
 */
export async function abrirPdf(ruta: string, nombre: string, params?: Record<string, unknown>): Promise<boolean> {
  const blob = await obtenerBlob(ruta, params)
  const url = URL.createObjectURL(new Blob([blob], { type: 'application/pdf' }))
  const w = window.open(url, '_blank', 'noopener')
  window.setTimeout(() => URL.revokeObjectURL(url), 60_000)
  if (!w) { guardarBlob(blob, nombre); return false }
  return true
}

export function tamanoLegible(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
