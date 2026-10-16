import { http } from './client'
import { limpio } from './inventario'
import type {
  ConsentimientoRequest, Consentimiento, EstadoProcedimiento, MonitoreoAnestesico, MonitoreoRequest, Page, Procedimiento,
  ProcedimientoCampos, ProcedimientoRequest, ProcedimientoResumen, TipoProcedimiento,
} from '@/types/etapa'

const B = '/procedimientos'

export interface ProcedimientoFiltro {
  pacienteId?: number | null; consultaId?: number | null; tipo?: TipoProcedimiento | null
  estado?: EstadoProcedimiento | null; page?: number; size?: number
}
export async function listProcedimientos(f: ProcedimientoFiltro = {}): Promise<Page<ProcedimientoResumen>> {
  return (await http.get<Page<ProcedimientoResumen>>(B, { params: limpio({ ...f, page: f.page ?? 0, size: f.size ?? 20 }) })).data
}
export async function listProcedimientosPaciente(pacienteId: number): Promise<ProcedimientoResumen[]> {
  return (await http.get<ProcedimientoResumen[]>(`/pacientes/${pacienteId}/procedimientos`)).data
}
export async function getProcedimiento(id: number): Promise<Procedimiento> {
  return (await http.get<Procedimiento>(`${B}/${id}`)).data
}
export async function crearProcedimiento(body: ProcedimientoRequest): Promise<Procedimiento> {
  return (await http.post<Procedimiento>(B, body)).data
}
export async function actualizarProcedimiento(id: number, body: ProcedimientoCampos): Promise<Procedimiento> {
  return (await http.put<Procedimiento>(`${B}/${id}`, body)).data
}
export async function cambiarEstadoProcedimiento(id: number, estado: EstadoProcedimiento, motivo?: string): Promise<Procedimiento> {
  return (await http.post<Procedimiento>(`${B}/${id}/estado`, { estado, motivo: motivo?.trim() || undefined })).data
}
export async function listMonitoreo(id: number): Promise<MonitoreoAnestesico[]> {
  return (await http.get<MonitoreoAnestesico[]>(`${B}/${id}/monitoreo-anestesico`)).data
}
export async function registrarMonitoreo(id: number, body: MonitoreoRequest): Promise<MonitoreoAnestesico> {
  return (await http.post<MonitoreoAnestesico>(`${B}/${id}/monitoreo-anestesico`, body)).data
}
export async function registrarConsentimiento(id: number, body: ConsentimientoRequest): Promise<Consentimiento> {
  return (await http.post<Consentimiento>(`${B}/${id}/consentimiento`, body)).data
}
export const rutaReporteProcedimientoPdf = (id: number): string => `${B}/${id}/reporte.pdf`
