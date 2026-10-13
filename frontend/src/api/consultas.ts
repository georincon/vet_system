import { http } from './client'
import type {
  Addendum, Consulta, ConsultaRequest, ConsultaUpdate, Diagnostico, DiagnosticoCatalogo, DiagnosticoRequest, Page,
  PlantillaSoap, Receta, RecetaRequest, SignoVital, SignoVitalRequest, TipoAddendum, TipoConsulta,
} from '@/types'

export async function listConsultas(
  fecha?: string,
  pacienteId?: number,
  page = 0,
  size = 20,
  estado?: string,
): Promise<Page<Consulta>> {
  const { data } = await http.get<Page<Consulta>>('/consultas', {
    params: { fecha: fecha || undefined, pacienteId: pacienteId || undefined, estado: estado || undefined, page, size },
  })
  return data
}

export async function getConsulta(id: number): Promise<Consulta> {
  const { data } = await http.get<Consulta>(`/consultas/${id}`)
  return data
}

export async function createConsulta(request: ConsultaRequest): Promise<Consulta> {
  const { data } = await http.post<Consulta>('/consultas', request)
  return data
}

export async function updateConsulta(id: number, request: Partial<ConsultaRequest> | ConsultaUpdate): Promise<Consulta> {
  const { data } = await http.put<Consulta>(`/consultas/${id}`, request)
  return data
}

export async function iniciarConsulta(
  citaId: number,
  body?: { plantillaId?: number | null; tipoConsulta?: TipoConsulta | null },
): Promise<Consulta> {
  const { data } = await http.post<Consulta>(`/citas/${citaId}/iniciar-consulta`, body ?? {})
  return data
}

export async function cerrarConsulta(id: number): Promise<Consulta> {
  const { data } = await http.post<Consulta>(`/consultas/${id}/cerrar`)
  return data
}

export async function listAddendums(id: number): Promise<Addendum[]> {
  const { data } = await http.get<Addendum[]>(`/consultas/${id}/addendums`)
  return data
}

export async function createAddendum(id: number, tipo: TipoAddendum, texto: string): Promise<Addendum> {
  const { data } = await http.post<Addendum>(`/consultas/${id}/addendums`, { tipo, texto })
  return data
}

export async function createSigno(id: number, body: SignoVitalRequest): Promise<SignoVital> {
  const { data } = await http.post<SignoVital>(`/consultas/${id}/signos-vitales`, body)
  return data
}
export async function updateSigno(id: number, signoId: number, body: SignoVitalRequest): Promise<SignoVital> {
  const { data } = await http.put<SignoVital>(`/consultas/${id}/signos-vitales/${signoId}`, body)
  return data
}
export async function deleteSigno(id: number, signoId: number): Promise<void> {
  await http.delete(`/consultas/${id}/signos-vitales/${signoId}`)
}

export async function createDiagnostico(id: number, body: DiagnosticoRequest): Promise<Diagnostico> {
  const { data } = await http.post<Diagnostico>(`/consultas/${id}/diagnosticos`, body)
  return data
}
export async function updateDiagnostico(id: number, dxId: number, body: DiagnosticoRequest): Promise<Diagnostico> {
  const { data } = await http.put<Diagnostico>(`/consultas/${id}/diagnosticos/${dxId}`, body)
  return data
}
export async function deleteDiagnostico(id: number, dxId: number): Promise<void> {
  await http.delete(`/consultas/${id}/diagnosticos/${dxId}`)
}

export async function createReceta(id: number, body: RecetaRequest): Promise<Receta> {
  const { data } = await http.post<Receta>(`/consultas/${id}/recetas`, body)
  return data
}
export async function updateReceta(id: number, recetaId: number, body: RecetaRequest): Promise<Receta> {
  const { data } = await http.put<Receta>(`/consultas/${id}/recetas/${recetaId}`, body)
  return data
}
export async function deleteReceta(id: number, recetaId: number): Promise<void> {
  await http.delete(`/consultas/${id}/recetas/${recetaId}`)
}

export async function buscarDiagnosticos(q: string): Promise<DiagnosticoCatalogo[]> {
  const { data } = await http.get<DiagnosticoCatalogo[]>('/clinica/diagnosticos', { params: { q } })
  return data
}

export async function listPlantillas(especieId?: number | null): Promise<PlantillaSoap[]> {
  const { data } = await http.get<PlantillaSoap[]>('/clinica/plantillas', {
    params: { especieId: especieId || undefined },
  })
  return data
}
