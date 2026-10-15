import { http } from './client'
import type { Page } from '@/types'
import type {
  Antiparasitario, AntiparasitarioRequest, Esquema, EsquemaRequest, EsquemaSugerido, Inmunizacion,
  InmunizacionRequest, Pendiente, TipoInmunizacion, Vacuna, VacunaRequest,
} from '@/types/fase3'

const BASE = '/inmunizacion'

export async function listVacunas(p: { especieId?: number | null; activo?: boolean; q?: string } = {}): Promise<Vacuna[]> {
  const { data } = await http.get<Vacuna[]>(`${BASE}/vacunas`, {
    params: { especieId: p.especieId || undefined, activo: p.activo, q: p.q || undefined },
  })
  return data
}
export async function saveVacuna(id: number | null, body: Partial<VacunaRequest>): Promise<Vacuna> {
  const { data } = id ? await http.put<Vacuna>(`${BASE}/vacunas/${id}`, body) : await http.post<Vacuna>(`${BASE}/vacunas`, body)
  return data
}

export async function listAntiparasitarios(p: { especieId?: number | null; activo?: boolean } = {}): Promise<Antiparasitario[]> {
  const { data } = await http.get<Antiparasitario[]>(`${BASE}/antiparasitarios`, {
    params: { especieId: p.especieId || undefined, activo: p.activo },
  })
  return data
}
export async function saveAntiparasitario(id: number | null, body: Partial<AntiparasitarioRequest>): Promise<Antiparasitario> {
  const { data } = id
    ? await http.put<Antiparasitario>(`${BASE}/antiparasitarios/${id}`, body)
    : await http.post<Antiparasitario>(`${BASE}/antiparasitarios`, body)
  return data
}

export async function listEsquemas(p: { especieId?: number | null; vacunaId?: number | null; activo?: boolean } = {}): Promise<Esquema[]> {
  const { data } = await http.get<Esquema[]>(`${BASE}/esquemas`, {
    params: { especieId: p.especieId || undefined, vacunaId: p.vacunaId || undefined, activo: p.activo },
  })
  return data
}
export async function saveEsquema(id: number | null, body: Partial<EsquemaRequest>): Promise<Esquema> {
  const { data } = id ? await http.put<Esquema>(`${BASE}/esquemas/${id}`, body) : await http.post<Esquema>(`${BASE}/esquemas`, body)
  return data
}

export interface PendientesFiltro {
  desde?: string
  hasta?: string
  dias?: number
  especieId?: number | null
  tipo?: TipoInmunizacion | null
  estado?: 'VENCIDA' | 'POR_VENCER' | null
  page?: number
  size?: number
}
export async function listPendientes(f: PendientesFiltro): Promise<Page<Pendiente>> {
  const { data } = await http.get<Page<Pendiente>>(`${BASE}/pendientes`, {
    params: {
      desde: f.desde || undefined,
      hasta: f.hasta || undefined,
      dias: f.dias || undefined,
      especieId: f.especieId || undefined,
      tipo: f.tipo || undefined,
      estado: f.estado || undefined,
      page: f.page ?? 0,
      size: f.size ?? 20,
    },
  })
  return data
}

export async function listInmunizaciones(pacienteId: number, incluirAnuladas = true): Promise<Inmunizacion[]> {
  const { data } = await http.get<Inmunizacion[]>(`/pacientes/${pacienteId}/inmunizaciones`, { params: { incluirAnuladas } })
  return data
}
export async function aplicarInmunizacion(pacienteId: number, body: InmunizacionRequest): Promise<Inmunizacion> {
  const { data } = await http.post<Inmunizacion>(`/pacientes/${pacienteId}/inmunizaciones`, body)
  return data
}
export async function anularInmunizacion(pacienteId: number, id: number, tipo: TipoInmunizacion, motivo: string): Promise<Inmunizacion> {
  const { data } = await http.post<Inmunizacion>(`/pacientes/${pacienteId}/inmunizaciones/${id}/anular`, { motivo }, { params: { tipo } })
  return data
}
export async function getEsquemaSugerido(pacienteId: number): Promise<EsquemaSugerido> {
  const { data } = await http.get<EsquemaSugerido>(`/pacientes/${pacienteId}/esquema-sugerido`)
  return data
}

/** Descarga el carnet con el Bearer (un enlace directo no envía el token) y devuelve un blob URL. */
export async function descargarCarnet(pacienteId: number): Promise<{ url: string; nombre: string }> {
  const resp = await http.get<Blob>(`/pacientes/${pacienteId}/carnet.pdf`, { responseType: 'blob' })
  const cd = String(resp.headers['content-disposition'] ?? '')
  const m = /filename="?([^";]+)"?/i.exec(cd)
  const blob = new Blob([resp.data], { type: 'application/pdf' })
  return { url: URL.createObjectURL(blob), nombre: m?.[1] ?? `carnet-${pacienteId}.pdf` }
}
