import { http } from './client'
import type {
  ControlRequest, ControlSeguimiento, PerfilProfesional, PlanRequest, PlanTratamiento, VerificacionDocumento,
} from '@/types/etapa'

// ---- PDF (se descargan con `abrirPdf`/`descargar` para enviar el Bearer)
export const rutaFormulaConsultaPdf = (consultaId: number): string => `/consultas/${consultaId}/formula.pdf`
export const rutaFormulaRecetaPdf = (recetaId: number): string => `/recetas/${recetaId}/formula.pdf`
export const rutaResumenConsultaPdf = (consultaId: number): string => `/consultas/${consultaId}/resumen.pdf`

// ---- plan de tratamiento (404 = aún no hay plan)
export async function getPlan(consultaId: number): Promise<PlanTratamiento | null> {
  const r = await http.get<PlanTratamiento>(`/consultas/${consultaId}/plan-tratamiento`, { validateStatus: (s) => s === 200 || s === 404 })
  return r.status === 200 ? r.data : null
}
export async function crearPlan(consultaId: number, body: PlanRequest): Promise<PlanTratamiento> {
  return (await http.post<PlanTratamiento>(`/consultas/${consultaId}/plan-tratamiento`, body)).data
}
export async function actualizarPlan(consultaId: number, body: PlanRequest): Promise<PlanTratamiento> {
  return (await http.put<PlanTratamiento>(`/consultas/${consultaId}/plan-tratamiento`, body)).data
}
export async function eliminarPlan(consultaId: number): Promise<void> {
  await http.delete(`/consultas/${consultaId}/plan-tratamiento`)
}

// ---- controles de seguimiento
export async function listControles(consultaId: number): Promise<ControlSeguimiento[]> {
  return (await http.get<ControlSeguimiento[]>(`/consultas/${consultaId}/plan-tratamiento/controles`)).data
}
export async function crearControl(consultaId: number, body: ControlRequest): Promise<ControlSeguimiento> {
  return (await http.post<ControlSeguimiento>(`/consultas/${consultaId}/plan-tratamiento/controles`, body)).data
}
export async function actualizarControl(consultaId: number, id: number, body: ControlRequest): Promise<ControlSeguimiento> {
  return (await http.put<ControlSeguimiento>(`/consultas/${consultaId}/plan-tratamiento/controles/${id}`, body)).data
}
export async function cancelarControl(consultaId: number, id: number): Promise<void> {
  await http.delete(`/consultas/${consultaId}/plan-tratamiento/controles/${id}`)
}

// ---- perfil profesional
export async function getPerfilProfesional(): Promise<PerfilProfesional> {
  return (await http.get<PerfilProfesional>('/perfil/profesional')).data
}
export async function updatePerfilProfesional(body: { numeroProfesional?: string | null; especialidad?: string | null }): Promise<PerfilProfesional> {
  return (await http.put<PerfilProfesional>('/perfil/profesional', body)).data
}
export async function updatePerfilProfesionalUsuario(usuarioId: number, body: { numeroProfesional?: string | null; especialidad?: string | null }): Promise<PerfilProfesional> {
  return (await http.put<PerfilProfesional>(`/usuarios/${usuarioId}/perfil-profesional`, body)).data
}

// ---- verificación pública (sin login; 404 = código desconocido)
export async function verificarDocumento(codigo: string): Promise<VerificacionDocumento> {
  const r = await http.get<VerificacionDocumento>(`/verificar/${encodeURIComponent(codigo.trim())}`, {
    validateStatus: (s) => s === 200 || s === 404,
  })
  return r.status === 200 ? r.data : { valido: false }
}
