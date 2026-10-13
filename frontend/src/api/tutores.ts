import { http } from './client'
import type { PacienteResumen, Page, Tutor, TutorRequest } from '@/types'

export async function listTutores(q?: string, page = 0, size = 20): Promise<Page<Tutor>> {
  const response = await http.get<Page<Tutor>>('/tutores', { params: { q, page, size } })
  return response.data
}

export async function getTutor(id: number): Promise<Tutor> {
  const response = await http.get<Tutor>(`/tutores/${id}`)
  return response.data
}

export async function createTutor(data: TutorRequest): Promise<Tutor> {
  const response = await http.post<Tutor>('/tutores', data)
  return response.data
}

export async function updateTutor(id: number, data: TutorRequest): Promise<Tutor> {
  const response = await http.put<Tutor>(`/tutores/${id}`, data)
  return response.data
}

export async function listPacientesDeTutor(id: number): Promise<PacienteResumen[]> {
  const response = await http.get<PacienteResumen[]>(`/tutores/${id}/pacientes`)
  return response.data
}