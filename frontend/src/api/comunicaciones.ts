import { http } from './client'
import type { Page } from '@/types'
import type {
  CanalMensaje, EnvioLog, Plantilla, PlantillaRequest, Recordatorio, Regla, ReglaRequest, ResultadoEjecucion,
} from '@/types/fase3'

const BASE = '/comunicaciones'

export interface RecordatorioFiltro {
  estado?: string | null
  tipo?: string | null
  canal?: string | null
  desde?: string
  hasta?: string
  page?: number
  size?: number
}
export async function listRecordatorios(f: RecordatorioFiltro): Promise<Page<Recordatorio>> {
  const { data } = await http.get<Page<Recordatorio>>(`${BASE}/recordatorios`, {
    params: {
      estado: f.estado || undefined, tipo: f.tipo || undefined, canal: f.canal || undefined,
      desde: f.desde || undefined, hasta: f.hasta || undefined, page: f.page ?? 0, size: f.size ?? 20,
    },
  })
  return data
}
export async function cancelarRecordatorio(id: number): Promise<Recordatorio> {
  const { data } = await http.post<Recordatorio>(`${BASE}/recordatorios/${id}/cancelar`)
  return data
}
export async function bitacoraRecordatorio(id: number): Promise<EnvioLog[]> {
  const { data } = await http.get<EnvioLog[]>(`${BASE}/recordatorios/${id}/bitacora`)
  return data
}
export async function listEnvios(f: { estado?: string | null; page?: number; size?: number }): Promise<Page<EnvioLog>> {
  const { data } = await http.get<Page<EnvioLog>>(`${BASE}/envios`, {
    params: { estado: f.estado || undefined, page: f.page ?? 0, size: f.size ?? 20 },
  })
  return data
}

export async function listPlantillasMensaje(p: { canal?: CanalMensaje | null; activo?: boolean } = {}): Promise<Plantilla[]> {
  const { data } = await http.get<Plantilla[]>(`${BASE}/plantillas`, { params: { canal: p.canal || undefined, activo: p.activo } })
  return data
}
export async function savePlantillaMensaje(id: number | null, body: Partial<PlantillaRequest>): Promise<Plantilla> {
  const { data } = id ? await http.put<Plantilla>(`${BASE}/plantillas/${id}`, body) : await http.post<Plantilla>(`${BASE}/plantillas`, body)
  return data
}

export async function listReglas(p: { evento?: string | null; activo?: boolean } = {}): Promise<Regla[]> {
  const { data } = await http.get<Regla[]>(`${BASE}/reglas`, { params: { evento: p.evento || undefined, activo: p.activo } })
  return data
}
export async function saveRegla(id: number | null, body: Partial<ReglaRequest>): Promise<Regla> {
  const { data } = id ? await http.put<Regla>(`${BASE}/reglas/${id}`, body) : await http.post<Regla>(`${BASE}/reglas`, body)
  return data
}

export async function ejecutarJob(): Promise<ResultadoEjecucion> {
  const { data } = await http.post<ResultadoEjecucion>(`${BASE}/ejecutar`)
  return data
}
