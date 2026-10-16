import { http } from './client'
import { limpio } from './inventario'
import type { ArchivoInfo, SubirArchivoRequest, TipoArchivo } from '@/types/etapa'
import { obtenerBlob } from '@/utils/descarga'

export async function subirArchivo(r: SubirArchivoRequest, onProgress?: (pct: number) => void): Promise<ArchivoInfo> {
  const fd = new FormData()
  fd.append('archivo', r.archivo)
  fd.append('pacienteId', String(r.pacienteId))
  fd.append('tipo', r.tipo)
  if (r.consultaId) fd.append('consultaId', String(r.consultaId))
  if (r.ordenExamenId) fd.append('ordenExamenId', String(r.ordenExamenId))
  if (r.ordenItemId) fd.append('ordenItemId', String(r.ordenItemId))
  if (r.cirugiaId) fd.append('cirugiaId', String(r.cirugiaId))
  if (r.descripcion?.trim()) fd.append('descripcion', r.descripcion.trim())
  const { data } = await http.post<ArchivoInfo>('/archivos', fd, {
    // El navegador agrega el boundary del multipart.
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (e) => { if (onProgress && e.total) onProgress(Math.round((e.loaded * 100) / e.total)) },
  })
  return data
}

export async function getArchivo(id: number): Promise<ArchivoInfo> {
  return (await http.get<ArchivoInfo>(`/archivos/${id}`)).data
}

export async function listArchivosPaciente(pacienteId: number, tipo?: TipoArchivo | null, incluirEliminados = false): Promise<ArchivoInfo[]> {
  return (await http.get<ArchivoInfo[]>(`/pacientes/${pacienteId}/archivos`, {
    params: limpio({ tipo, incluirEliminados: incluirEliminados || undefined }),
  })).data
}

export async function listArchivosPorVinculo(f: { consultaId?: number; ordenExamenId?: number; ordenItemId?: number; cirugiaId?: number }): Promise<ArchivoInfo[]> {
  return (await http.get<ArchivoInfo[]>('/archivos', { params: limpio(f) })).data
}

export async function eliminarArchivo(id: number, motivo: string): Promise<void> {
  await http.delete(`/archivos/${id}`, { params: { motivo } })
}

export const contenidoArchivo = (id: number): Promise<Blob> => obtenerBlob(`/archivos/${id}/contenido`)
export const miniaturaArchivo = (id: number): Promise<Blob> => obtenerBlob(`/archivos/${id}/miniatura`)
