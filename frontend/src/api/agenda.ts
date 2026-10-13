import { http } from './client'
import type {
  AgendaResponse, BloqueoAgenda, BloqueoRequest, EstadoListaEspera, ListaEspera, ListaEsperaRequest,
  Sala, TipoCita,
} from '@/types'

export interface AgendaFiltro {
  desde: string
  hasta: string
  veterinarioId?: number | null
  salaId?: number | null
  incluirCanceladas?: boolean
}

export async function getAgenda(f: AgendaFiltro): Promise<AgendaResponse> {
  const { data } = await http.get<AgendaResponse>('/agenda', {
    params: {
      desde: f.desde,
      hasta: f.hasta,
      veterinarioId: f.veterinarioId || undefined,
      salaId: f.salaId || undefined,
      incluirCanceladas: f.incluirCanceladas ? true : undefined,
    },
  })
  return data
}

export async function listTiposCita(): Promise<TipoCita[]> {
  const { data } = await http.get<TipoCita[]>('/agenda/tipos-cita')
  return data
}

export async function listSalas(): Promise<Sala[]> {
  const { data } = await http.get<Sala[]>('/agenda/salas')
  return data
}

export async function createBloqueo(req: BloqueoRequest): Promise<BloqueoAgenda> {
  const { data } = await http.post<BloqueoAgenda>('/agenda/bloqueos', req)
  return data
}

export async function deleteBloqueo(id: number): Promise<void> {
  await http.delete(`/agenda/bloqueos/${id}`)
}

export async function listListaEspera(estado?: EstadoListaEspera): Promise<ListaEspera[]> {
  const { data } = await http.get<ListaEspera[]>('/agenda/lista-espera', { params: { estado } })
  return data
}

export async function createListaEspera(req: ListaEsperaRequest): Promise<ListaEspera> {
  const { data } = await http.post<ListaEspera>('/agenda/lista-espera', req)
  return data
}

export async function updateListaEspera(id: number, req: Partial<ListaEsperaRequest>): Promise<ListaEspera> {
  const { data } = await http.put<ListaEspera>(`/agenda/lista-espera/${id}`, req)
  return data
}

export async function asignarListaEspera(id: number, citaId: number): Promise<ListaEspera> {
  const { data } = await http.post<ListaEspera>(`/agenda/lista-espera/${id}/asignar`, null, { params: { citaId } })
  return data
}

export async function deleteListaEspera(id: number): Promise<void> {
  await http.delete(`/agenda/lista-espera/${id}`)
}
