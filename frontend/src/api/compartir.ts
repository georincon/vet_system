import { http } from './client'
import { limpio } from './inventario'
import type { CompartirRequest, Envio, Page, Previsualizacion } from '@/types/etapa'

const B = '/compartir'

export async function previsualizar(body: CompartirRequest): Promise<Previsualizacion> {
  return (await http.post<Previsualizacion>(`${B}/previsualizar`, body)).data
}
export async function enviar(body: CompartirRequest): Promise<Envio> {
  return (await http.post<Envio>(B, body)).data
}
export async function historialEnvios(f: { pacienteId?: number | null; page?: number; size?: number } = {}): Promise<Page<Envio>> {
  return (await http.get<Page<Envio>>(`${B}/historial`, { params: limpio({ ...f, page: f.page ?? 0, size: f.size ?? 20 }) })).data
}
export async function getEnvio(id: number): Promise<Envio> {
  return (await http.get<Envio>(`${B}/${id}`)).data
}
export async function reenviar(id: number): Promise<Envio> {
  return (await http.post<Envio>(`${B}/${id}/reenviar`)).data
}
export async function revocar(id: number, motivo?: string): Promise<Envio> {
  return (await http.post<Envio>(`${B}/${id}/revocar`, { motivo: motivo?.trim() || undefined })).data
}
