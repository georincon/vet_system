import { http } from './client'
import { limpio } from './inventario'
import type {
  CorreccionRequest, ExamenCatalogo, ExamenRequest, ImportacionRequest, ImportacionResultado, InformeImagenRequest,
  InformePatologiaRequest, ItemOrden, OrdenDetalle, OrdenFiltro, OrdenRequest, OrdenResumen, Page, ResultadoEntrada,
  Tendencia, TipoExamen,
} from '@/types/etapa'

const B = '/laboratorio'

// ---- catálogo
export async function listExamenes(f: { tipo?: TipoExamen | null; q?: string; soloActivos?: boolean } = {}): Promise<ExamenCatalogo[]> {
  return (await http.get<ExamenCatalogo[]>(`${B}/examenes`, { params: limpio({ tipo: f.tipo, q: f.q, soloActivos: f.soloActivos ?? true }) })).data
}
export async function getExamen(id: number): Promise<ExamenCatalogo> {
  return (await http.get<ExamenCatalogo>(`${B}/examenes/${id}`)).data
}
export async function saveExamen(id: number | null, body: ExamenRequest): Promise<ExamenCatalogo> {
  return (id ? await http.put<ExamenCatalogo>(`${B}/examenes/${id}`, body) : await http.post<ExamenCatalogo>(`${B}/examenes`, body)).data
}

// ---- órdenes
export async function crearOrden(body: OrdenRequest): Promise<OrdenDetalle> {
  return (await http.post<OrdenDetalle>(`${B}/ordenes`, body)).data
}
export async function listOrdenes(f: OrdenFiltro = {}): Promise<Page<OrdenResumen>> {
  return (await http.get<Page<OrdenResumen>>(`${B}/ordenes`, { params: limpio({ ...f, page: f.page ?? 0, size: f.size ?? 20 }) })).data
}
export async function getOrden(id: number): Promise<OrdenDetalle> {
  return (await http.get<OrdenDetalle>(`${B}/ordenes/${id}`)).data
}
export async function registrarMuestra(id: number, observaciones?: string): Promise<OrdenDetalle> {
  return (await http.post<OrdenDetalle>(`${B}/ordenes/${id}/muestra`, { observaciones: observaciones?.trim() || undefined })).data
}
export async function cambiarEstadoOrden(id: number, estado: 'EN_PROCESO' | 'CANCELADA', motivo?: string): Promise<OrdenDetalle> {
  return (await http.post<OrdenDetalle>(`${B}/ordenes/${id}/estado`, { estado, motivo: motivo?.trim() || undefined })).data
}
export async function capturarResultados(id: number, itemId: number, resultados: ResultadoEntrada[]): Promise<ItemOrden> {
  return (await http.put<ItemOrden>(`${B}/ordenes/${id}/items/${itemId}/resultados`, { resultados })).data
}
export async function validarItem(id: number, itemId: number, interpretacion?: string): Promise<ItemOrden> {
  return (await http.post<ItemOrden>(`${B}/ordenes/${id}/items/${itemId}/validar`, { interpretacion: interpretacion?.trim() || undefined })).data
}
export async function corregirItem(id: number, itemId: number, body: CorreccionRequest): Promise<ItemOrden> {
  return (await http.post<ItemOrden>(`${B}/ordenes/${id}/items/${itemId}/correccion`, body)).data
}
export async function guardarInformeImagen(id: number, itemId: number, body: InformeImagenRequest): Promise<ItemOrden> {
  return (await http.put<ItemOrden>(`${B}/ordenes/${id}/items/${itemId}/informe-imagen`, body)).data
}
export async function guardarInformePatologia(id: number, itemId: number, body: InformePatologiaRequest): Promise<ItemOrden> {
  return (await http.put<ItemOrden>(`${B}/ordenes/${id}/items/${itemId}/informe-patologia`, body)).data
}
/** Ruta (relativa a /api) del PDF del informe; úsese con `abrirPdf`/`descargar` para enviar el Bearer. */
export const rutaInformePdf = (id: number): string => `${B}/ordenes/${id}/informe.pdf`

// ---- importación
export async function importarJson(body: ImportacionRequest): Promise<ImportacionResultado> {
  return (await http.post<ImportacionResultado>(`${B}/importar`, body)).data
}
export async function importarArchivo(f: {
  archivo: File; formato: string; ordenId?: number | null; ordenCodigo?: string; dryRun: boolean
}): Promise<ImportacionResultado> {
  const fd = new FormData()
  fd.append('archivo', f.archivo)
  fd.append('formato', f.formato)
  if (f.ordenId) fd.append('ordenId', String(f.ordenId))
  if (f.ordenCodigo?.trim()) fd.append('ordenCodigo', f.ordenCodigo.trim())
  fd.append('dryRun', String(f.dryRun))
  fd.append('origen', 'ARCHIVO')
  return (await http.post<ImportacionResultado>(`${B}/importar`, fd, { headers: { 'Content-Type': 'multipart/form-data' } })).data
}
export const rutaPlantillaCsv = `${B}/plantilla-csv`

// ---- historial y tendencia del paciente
export async function listLaboratorioPaciente(pacienteId: number, tipo?: TipoExamen | null, page = 0, size = 20): Promise<Page<OrdenResumen>> {
  return (await http.get<Page<OrdenResumen>>(`/pacientes/${pacienteId}/laboratorio`, { params: limpio({ tipo, page, size }) })).data
}
export async function getTendencia(pacienteId: number, parametroId: number): Promise<Tendencia> {
  return (await http.get<Tendencia>(`/pacientes/${pacienteId}/laboratorio/tendencia`, { params: { parametroId } })).data
}
