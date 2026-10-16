import { http } from './client'
import type {
  Alergia, AlergiaRequest, HistoriaItem, Paciente, PacienteRequest, Page, PesoRegistro, TipoHistoria,
} from '@/types'

export async function listPacientes(q?: string, page = 0, size = 20): Promise<Page<Paciente>> {
  const response = await http.get<Page<Paciente>>('/pacientes', { params: { q, page, size } })
  return response.data
}

export async function getPaciente(id: number): Promise<Paciente> {
  const response = await http.get<Paciente>(`/pacientes/${id}`)
  return response.data
}

export async function createPaciente(data: PacienteRequest): Promise<Paciente> {
  const response = await http.post<Paciente>('/pacientes', data)
  return response.data
}

export async function updatePaciente(id: number, data: PacienteRequest): Promise<Paciente> {
  const response = await http.put<Paciente>(`/pacientes/${id}`, data)
  return response.data
}

export async function listAlergias(id: number): Promise<Alergia[]> {
  const { data } = await http.get<Alergia[]>(`/pacientes/${id}/alergias`)
  return data
}

export async function createAlergia(id: number, body: AlergiaRequest): Promise<Alergia> {
  const { data } = await http.post<Alergia>(`/pacientes/${id}/alergias`, body)
  return data
}

export async function deleteAlergia(id: number, alergiaId: number): Promise<void> {
  await http.delete(`/pacientes/${id}/alergias/${alergiaId}`)
}

export async function listPesos(id: number): Promise<PesoRegistro[]> {
  const { data } = await http.get<PesoRegistro[]>(`/pacientes/${id}/pesos`)
  return data
}

export async function createPeso(id: number, pesoKg: number, fecha?: string): Promise<PesoRegistro> {
  const { data } = await http.post<PesoRegistro>(`/pacientes/${id}/pesos`, { pesoKg, fecha: fecha || undefined })
  return data
}

export async function getHistoria(id: number, tipo?: TipoHistoria | string, page = 0, size = 10): Promise<Page<HistoriaItem>> {
  const { data } = await http.get<Page<HistoriaItem>>(`/pacientes/${id}/historia`, {
    params: { tipo: tipo || undefined, page, size },
  })
  return data
}
