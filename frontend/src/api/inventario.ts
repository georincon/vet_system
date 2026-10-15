import { http } from './client'
import type { Page } from '@/types'
import type {
  Alertas, Bodega, Categoria, Controlado, Item, ItemRequest, Lote, Movimiento, Orden, OrdenRequest, Proveedor,
  ProveedorRequest, RecepcionRequest,
} from '@/types/fase4'

const B = '/inventario'
/** Quita parámetros vacíos para no enviar `?q=`. */
export function limpio<T extends Record<string, unknown>>(p: T): Partial<T> {
  return Object.fromEntries(Object.entries(p).filter(([, v]) => v !== undefined && v !== null && v !== '')) as Partial<T>
}

export interface ItemsFiltro {
  q?: string; tipo?: string | null; categoriaId?: number | null; controlaStock?: boolean | null
  esControlado?: boolean | null; activo?: boolean | null; bajoMinimo?: boolean | null; page?: number; size?: number
}
export async function listItems(f: ItemsFiltro = {}): Promise<Page<Item>> {
  const { data } = await http.get<Page<Item>>(`${B}/items`, { params: limpio({ ...f, page: f.page ?? 0, size: f.size ?? 20 }) })
  return data
}
export async function saveItem(id: number | null, body: ItemRequest): Promise<Item> {
  const { data } = id ? await http.put<Item>(`${B}/items/${id}`, body) : await http.post<Item>(`${B}/items`, body)
  return data
}
export async function listCategorias(): Promise<Categoria[]> { return (await http.get<Categoria[]>(`${B}/categorias`)).data }
export async function listBodegas(): Promise<Bodega[]> { return (await http.get<Bodega[]>(`${B}/bodegas`)).data }

export interface LotesFiltro {
  itemId?: number | null; vacunaId?: number | null; antiparasitarioId?: number | null; bodegaId?: number | null
  soloVigentes?: boolean | null; conStock?: boolean | null; q?: string
}
export async function listLotes(f: LotesFiltro = {}): Promise<Lote[]> {
  const { data } = await http.get<Lote[]>(`${B}/lotes`, { params: limpio({ ...f }) })
  return data
}
export async function crearLote(body: Record<string, unknown>): Promise<Lote> { return (await http.post<Lote>(`${B}/lotes`, body)).data }
export async function estadoLote(id: number, estado: 'DISPONIBLE' | 'CUARENTENA'): Promise<Lote> {
  return (await http.post<Lote>(`${B}/lotes/${id}/estado`, { estado })).data
}
export async function retirarLote(id: number, motivo: string): Promise<Lote> {
  return (await http.post<Lote>(`${B}/lotes/${id}/retirar`, { motivo })).data
}

export interface MovFiltro { itemId?: number | null; loteId?: number | null; tipo?: string | null; desde?: string; hasta?: string; referencia?: string; page?: number; size?: number }
export async function listMovimientos(f: MovFiltro = {}): Promise<Page<Movimiento>> {
  const { data } = await http.get<Page<Movimiento>>(`${B}/movimientos`, { params: limpio({ ...f, page: f.page ?? 0, size: f.size ?? 20 }) })
  return data
}
export async function registrarSalida(body: Record<string, unknown>, uso = false) {
  const { data } = await http.post(`${B}/movimientos/${uso ? 'uso' : 'salida'}`, body)
  return data as { itemNombre: string; cantidad: number; stockRestante: number; lotes: { numeroLote: string; cantidad: number }[] }
}
export async function ajustarLote(body: { loteId: number; cantidadReal?: number; delta?: number; motivo: string }) {
  const { data } = await http.post(`${B}/movimientos/ajuste`, body)
  return data as { numeroLote: string; anterior: number; actual: number; delta: number }
}
export async function getAlertas(dias = 30): Promise<Alertas> { return (await http.get<Alertas>(`${B}/alertas`, { params: { dias } })).data }
export async function listControlados(f: { itemId?: number | null; tipo?: string | null; desde?: string; hasta?: string; page?: number; size?: number } = {}): Promise<Page<Controlado>> {
  const { data } = await http.get<Page<Controlado>>(`${B}/controlados`, { params: limpio({ ...f, page: f.page ?? 0, size: f.size ?? 20 }) })
  return data
}

// ---- Compras ----
export async function listProveedores(q?: string, page = 0, size = 20, activo?: boolean): Promise<Page<Proveedor>> {
  return (await http.get<Page<Proveedor>>(`${B}/proveedores`, { params: limpio({ q, page, size, activo }) })).data
}
export async function saveProveedor(id: number | null, body: ProveedorRequest): Promise<Proveedor> {
  return (id ? await http.put<Proveedor>(`${B}/proveedores/${id}`, body) : await http.post<Proveedor>(`${B}/proveedores`, body)).data
}
export async function listOrdenes(f: { proveedorId?: number | null; estado?: string | null; page?: number; size?: number } = {}): Promise<Page<Orden>> {
  return (await http.get<Page<Orden>>(`${B}/ordenes-compra`, { params: limpio({ ...f, page: f.page ?? 0, size: f.size ?? 20 }) })).data
}
export async function getOrden(id: number): Promise<Orden> { return (await http.get<Orden>(`${B}/ordenes-compra/${id}`)).data }
export async function saveOrden(id: number | null, body: OrdenRequest): Promise<Orden> {
  return (id ? await http.put<Orden>(`${B}/ordenes-compra/${id}`, body) : await http.post<Orden>(`${B}/ordenes-compra`, body)).data
}
export async function enviarOrden(id: number): Promise<Orden> { return (await http.post<Orden>(`${B}/ordenes-compra/${id}/enviar`)).data }
export async function cancelarOrden(id: number): Promise<Orden> { return (await http.post<Orden>(`${B}/ordenes-compra/${id}/cancelar`)).data }
export async function recibirOrden(id: number, body: RecepcionRequest) {
  return (await http.post(`${B}/ordenes-compra/${id}/recepciones`, body)).data
}
