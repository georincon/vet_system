import { http } from './client'
import { limpio } from './inventario'
import type { Page } from '@/types'
import type {
  Caja, CarteraDetalle, CarteraTutor, Factura, FacturaResumen, Linea, PagoInput, Presupuesto, ReporteCartera,
  ReporteVentas, Turno,
} from '@/types/fase4'

const B = '/facturacion'

// ---- Caja ----
export async function listCajas(): Promise<Caja[]> { return (await http.get<Caja[]>(`${B}/caja`)).data }
/** Turno abierto del usuario; null si no hay (el backend responde 404). */
export async function turnoActual(): Promise<Turno | null> {
  const r = await http.get<Turno>(`${B}/caja/turnos/actual`, { validateStatus: (st) => st === 200 || st === 404 })
  return r.status === 200 ? r.data : null
}
export async function abrirTurno(montoApertura: number, cajaId?: number | null): Promise<Turno> {
  return (await http.post<Turno>(`${B}/caja/turnos/abrir`, { montoApertura, cajaId: cajaId || undefined })).data
}
export async function getTurno(id: number): Promise<Turno> { return (await http.get<Turno>(`${B}/caja/turnos/${id}`)).data }
export async function listTurnos(f: { estado?: string | null; desde?: string; hasta?: string; page?: number; size?: number } = {}): Promise<Page<Turno>> {
  return (await http.get<Page<Turno>>(`${B}/caja/turnos`, { params: limpio({ ...f, page: f.page ?? 0, size: f.size ?? 10 }) })).data
}
export async function movimientoCaja(id: number, body: { tipo: string; monto: number; concepto: string }): Promise<Turno> {
  return (await http.post<Turno>(`${B}/caja/turnos/${id}/movimientos`, body)).data
}
export async function cerrarTurno(id: number, efectivoContado: number, observaciones?: string): Promise<Turno> {
  return (await http.post<Turno>(`${B}/caja/turnos/${id}/cerrar`, { efectivoContado, observaciones: observaciones || undefined })).data
}

// ---- Presupuestos ----
export interface PresupuestoFiltro { tutorId?: number | null; pacienteId?: number | null; consultaId?: number | null; estado?: string | null; page?: number; size?: number }
export async function listPresupuestos(f: PresupuestoFiltro = {}): Promise<Page<Presupuesto>> {
  return (await http.get<Page<Presupuesto>>(`${B}/presupuestos`, { params: limpio({ ...f, page: f.page ?? 0, size: f.size ?? 20 }) })).data
}
export async function getPresupuesto(id: number): Promise<Presupuesto> { return (await http.get<Presupuesto>(`${B}/presupuestos/${id}`)).data }
export interface PresupuestoBody { tutorId?: number | null; pacienteId?: number | null; consultaId?: number | null; vigenteHasta?: string | null; notas?: string | null; items: Linea[] }
export async function savePresupuesto(id: number | null, body: PresupuestoBody): Promise<Presupuesto> {
  return (id ? await http.put<Presupuesto>(`${B}/presupuestos/${id}`, body) : await http.post<Presupuesto>(`${B}/presupuestos`, body)).data
}
export async function estadoPresupuesto(id: number, estado: 'ENVIADO' | 'ACEPTADO' | 'RECHAZADO'): Promise<Presupuesto> {
  return (await http.post<Presupuesto>(`${B}/presupuestos/${id}/estado`, { estado })).data
}
export async function facturarPresupuesto(id: number, body: { condicionPago: string; fechaVencimiento?: string; notas?: string; pagos?: PagoInput[] }): Promise<Factura> {
  return (await http.post<Factura>(`${B}/presupuestos/${id}/facturar`, body)).data
}

// ---- Facturas ----
export interface FacturaFiltro { tutorId?: number | null; estado?: string | null; tipo?: string | null; q?: string; desde?: string; hasta?: string; conSaldo?: boolean | null; page?: number; size?: number }
export async function listFacturas(f: FacturaFiltro = {}): Promise<Page<FacturaResumen>> {
  return (await http.get<Page<FacturaResumen>>(`${B}/facturas`, { params: limpio({ ...f, page: f.page ?? 0, size: f.size ?? 20 }) })).data
}
export async function getFactura(id: number): Promise<Factura> { return (await http.get<Factura>(`${B}/facturas/${id}`)).data }
export interface FacturaBody {
  tutorId?: number | null; pacienteId?: number | null; consultaId?: number | null; condicionPago: string
  fechaVencimiento?: string; notas?: string; items: Linea[]; pagos?: PagoInput[]
}
export async function crearFactura(body: FacturaBody): Promise<Factura> { return (await http.post<Factura>(`${B}/facturas`, body)).data }
export async function ventaPos(body: { tutorId?: number | null; pacienteId?: number | null; notas?: string; items: Linea[]; pagos: PagoInput[] }): Promise<Factura> {
  return (await http.post<Factura>(`${B}/pos/ventas`, body)).data
}
export async function pagarFactura(id: number, pago: PagoInput): Promise<Factura> { return (await http.post<Factura>(`${B}/facturas/${id}/pagos`, pago)).data }
export interface NotaCreditoResp { nota: Factura; factura: Factura; montoADevolver: number; devolucionRegistradaEnCaja: boolean; stockReintegrado: number }
export async function notaCredito(id: number, body: { motivo: string; reintegrarStock: boolean; items?: { facturaItemId: number; cantidad: number }[]; devolucion?: { metodo: string } }): Promise<NotaCreditoResp> {
  return (await http.post<NotaCreditoResp>(`${B}/facturas/${id}/notas-credito`, body)).data
}
export async function reintentarDian(id: number): Promise<Factura> { return (await http.post<Factura>(`${B}/facturas/${id}/dian/reintentar`)).data }

// ---- Cartera y reportes ----
export async function listCartera(soloVencidas = false, page = 0, size = 20): Promise<Page<CarteraTutor>> {
  return (await http.get<Page<CarteraTutor>>(`${B}/cartera`, { params: { soloVencidas: soloVencidas || undefined, page, size } })).data
}
export async function carteraTutor(id: number): Promise<CarteraDetalle> { return (await http.get<CarteraDetalle>(`${B}/cartera/tutores/${id}`)).data }
export async function abonar(tutorId: number, pagos: PagoInput[], facturaIds?: number[]) {
  return (await http.post(`${B}/cartera/tutores/${tutorId}/abonos`, { pagos, facturaIds: facturaIds?.length ? facturaIds : undefined })).data as
    { total: number; saldoTutor: number; aplicado: { numero: string; monto: number; saldoRestante: number }[] }
}
export async function reporteCartera(): Promise<ReporteCartera> { return (await http.get<ReporteCartera>(`${B}/reportes/cartera`)).data }
export async function reporteVentas(desde: string, hasta: string): Promise<ReporteVentas> {
  return (await http.get<ReporteVentas>(`${B}/reportes/ventas`, { params: { desde, hasta } })).data
}
