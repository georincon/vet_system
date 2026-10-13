import { http } from './client'
import type { AuditoriaEntry, DashboardResumen, Page } from '@/types'

export async function getResumen(): Promise<DashboardResumen> {
  const { data } = await http.get<DashboardResumen>('/dashboard/resumen')
  return data
}

export interface AuditoriaFiltro {
  entidad?: string
  usuarioId?: number | null
  desde?: string
  hasta?: string
  page?: number
  size?: number
}

export async function listAuditoria(f: AuditoriaFiltro): Promise<Page<AuditoriaEntry>> {
  const { data } = await http.get<Page<AuditoriaEntry>>('/auditoria', {
    params: {
      entidad: f.entidad || undefined,
      usuarioId: f.usuarioId || undefined,
      desde: f.desde || undefined,
      hasta: f.hasta || undefined,
      page: f.page ?? 0,
      size: f.size ?? 20,
    },
  })
  return data
}
