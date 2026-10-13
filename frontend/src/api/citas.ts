import { http } from './client'
import type { Cita, CitaRequest, EstadoCita, Page } from '@/types'

export async function listEstadosCita(): Promise<EstadoCita[]> {
  const { data } = await http.get<EstadoCita[]>('/citas/estados')
  return data
}

export async function listCitas(fecha?: string, page = 0, size = 20): Promise<Page<Cita>> {
  const { data } = await http.get<Page<Cita>>('/citas', {
    params: { fecha: fecha || undefined, page, size },
  })
  return data
}

export async function getCita(id: number): Promise<Cita> {
  const { data } = await http.get<Cita>(`/citas/${id}`)
  return data
}

export async function createCita(request: CitaRequest): Promise<Cita> {
  const { data } = await http.post<Cita>('/citas', request)
  return data
}

export async function updateCita(id: number, request: Partial<CitaRequest>): Promise<Cita> {
  const { data } = await http.put<Cita>(`/citas/${id}`, request)
  return data
}

export async function cambiarEstadoCita(id: number, estado: string, motivo?: string): Promise<Cita> {
  const { data } = await http.post<Cita>(`/citas/${id}/estado`, { estado, motivo: motivo || undefined })
  return data
}
