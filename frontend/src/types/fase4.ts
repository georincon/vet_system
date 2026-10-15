/** Tipos de Fase 4 (inventario, compras, facturación, caja). Contrato: docs/api-fase4.md */

export type TipoItem = 'SERVICIO' | 'PRODUCTO' | 'MEDICAMENTO' | 'ALIMENTO' | 'EXAMEN_LAB' | 'EXAMEN_IMAGEN'
export const TIPOS_ITEM: { title: string; value: TipoItem }[] = [
  { title: 'Servicio', value: 'SERVICIO' },
  { title: 'Producto', value: 'PRODUCTO' },
  { title: 'Medicamento', value: 'MEDICAMENTO' },
  { title: 'Alimento', value: 'ALIMENTO' },
  { title: 'Examen de laboratorio', value: 'EXAMEN_LAB' },
  { title: 'Examen de imágenes', value: 'EXAMEN_IMAGEN' },
]
/** Solo estos tipos controlan stock (regla del backend). */
export const TIPOS_CON_STOCK: TipoItem[] = ['PRODUCTO', 'MEDICAMENTO', 'ALIMENTO']

export interface Categoria { id: number; nombre: string; tipo: string; activo: boolean }
export interface Bodega { id: number; nombre: string; tipo: string; activo: boolean; sedeId?: number | null }

export interface Item {
  id: number
  codigo: string
  nombre: string
  tipo: TipoItem
  categoriaId?: number | null
  categoria?: string | null
  precioVenta: number
  /** Solo ADMIN (null para el resto). */
  costo?: number | null
  impuestoPct: number
  controlaStock: boolean
  activo: boolean
  stockMinimo: number
  stockActual?: number | null
  stockTotal?: number | null
  bajoMinimo: boolean
  unidadMedida?: string | null
  presentacion?: string | null
  marca?: string | null
  principioActivo?: string | null
  concentracion?: string | null
  viaAdministracion?: string | null
  registroIca?: string | null
  esControlado: boolean
  requiereFormula: boolean
  codigoBarras?: string | null
  tieneVencimiento: boolean
  proveedorPreferidoId?: number | null
  requiereVeterinario: boolean
  duracionMinutos?: number | null
  descripcion?: string | null
}

export type ItemRequest = Partial<Omit<Item, 'id' | 'categoria' | 'stockActual' | 'stockTotal' | 'bajoMinimo'>>

export interface Lote {
  id: number
  itemId: number
  itemCodigo: string
  itemNombre: string
  bodegaId: number
  bodega: string
  numeroLote: string
  fechaVencimiento?: string | null
  diasParaVencer?: number | null
  vencido: boolean
  cantidadInicial: number
  cantidadActual: number
  costoUnitario?: number | null
  estado: string
  vigente: boolean
  proveedorId?: number | null
  creadoEn?: string
}

export interface Persona { id: number; nombre: string }

export interface Movimiento {
  id: number
  ocurridoEn: string
  tipo: string
  delta: number
  cantidad: number
  loteId?: number | null
  numeroLote?: string | null
  itemId: number
  itemCodigo: string
  itemNombre: string
  bodega?: string | null
  motivo?: string | null
  referencia?: string | null
  usuario?: Persona | null
  pacienteId?: number | null
  consultaId?: number | null
  costoUnitario?: number | null
  saldoLote?: number | null
}

export interface AlertaStock { itemId: number; codigo: string; nombre: string; stock: number; minimo: number; faltante: number }
export interface AlertaLote {
  loteId: number; itemId: number; itemCodigo: string; itemNombre: string; bodega?: string
  numeroLote: string; fechaVencimiento?: string; diasParaVencer?: number; cantidad: number; estado: string
}
export interface Alertas {
  dias: number
  resumen: { bajoMinimo: number; porVencer: number; vencidos: number }
  bajoMinimo: AlertaStock[]
  porVencer: AlertaLote[]
  vencidos: AlertaLote[]
}

export interface Controlado {
  id: number; ocurridoEn: string; itemId: number; itemCodigo: string; itemNombre: string
  loteId?: number | null; numeroLote?: string | null; tipo: string; cantidad: number; saldo: number
  pacienteId?: number | null; paciente?: string | null; veterinario?: Persona | null; observaciones?: string | null
}

export const MOTIVOS_SALIDA: { title: string; value: string }[] = [
  { title: 'Uso clínico', value: 'USO_CLINICO' },
  { title: 'Consumo interno', value: 'CONSUMO_INTERNO' },
  { title: 'Muestra', value: 'MUESTRA' },
  { title: 'Merma', value: 'MERMA' },
  { title: 'Daño', value: 'DANIO' },
  { title: 'Desecho', value: 'DESECHO' },
]

// ---- Compras ----
export interface Proveedor {
  id: number; tipoDocumento: string; numeroDocumento: string; razonSocial: string
  contacto?: string | null; telefono?: string | null; email?: string | null; direccion?: string | null
  ciudad?: string | null; plazoPagoDias?: number | null; notas?: string | null; activo: boolean
}
export type ProveedorRequest = Partial<Omit<Proveedor, 'id'>>

export type EstadoOrden = 'BORRADOR' | 'ENVIADA' | 'PARCIAL' | 'RECIBIDA' | 'CANCELADA'
export interface OrdenItem {
  id: number; itemId: number; codigo: string; nombre: string; cantidad: number
  cantidadRecibida: number; pendiente: number; costoUnitario: number; impuestoPct: number
}
export interface Orden {
  id: number; codigo: string; proveedorId: number; proveedor: string
  bodegaDestinoId?: number | null; bodegaDestino?: string | null
  fecha: string; fechaEsperada?: string | null; estado: EstadoOrden
  subtotal: number; impuestos: number; total: number; notas?: string | null; items: OrdenItem[]
}
export interface OrdenRequest {
  proveedorId: number
  bodegaDestinoId?: number | null
  fechaEsperada?: string | null
  notas?: string | null
  items: { itemId: number; cantidad: number; costoUnitario: number; impuestoPct?: number }[]
}
export interface RecepcionRequest {
  bodegaId?: number | null
  numeroFacturaProveedor?: string
  fecha?: string
  notas?: string
  items: {
    ordenCompraItemId?: number; itemId?: number; cantidad: number; numeroLote: string
    fechaVencimiento?: string | null; costoUnitario?: number; impuestoPct?: number
  }[]
}

// ---- Facturación ----
export interface TutorFact { id: number; nombre: string; documento?: string | null }

export interface Linea {
  id?: number
  itemId?: number | null
  itemCodigo?: string | null
  descripcion?: string | null
  cantidad: number
  precioUnitario?: number | null
  impuestoPct?: number | null
  descuento?: number | null
  base?: number
  impuesto?: number
  total?: number
  consultaId?: number | null
  pacienteId?: number | null
  veterinarioId?: number | null
  loteId?: number | null
  cantidadAcreditada?: number
  cantidadStockCubierta?: number
}

export type EstadoPresupuesto = 'BORRADOR' | 'ENVIADO' | 'ACEPTADO' | 'RECHAZADO' | 'FACTURADO' | 'VENCIDO'
export interface Presupuesto {
  id: number; codigo: string; tutor: TutorFact; pacienteId?: number | null; paciente?: string | null
  consultaId?: number | null; fecha?: string; estado: EstadoPresupuesto; origen: string
  vigenteHasta?: string | null; subtotal: number; descuento: number; impuestos: number; total: number
  notas?: string | null; facturaId?: number | null; items: Linea[]
}

export type MetodoPago = 'EFECTIVO' | 'TARJETA' | 'TRANSFERENCIA' | 'NEQUI' | 'DAVIPLATA' | 'PSE' | 'DATAFONO' | 'CHEQUE' | 'OTRO'
export const METODOS_PAGO: { title: string; value: MetodoPago; icon: string }[] = [
  { title: 'Efectivo', value: 'EFECTIVO', icon: 'mdi-cash' },
  { title: 'Tarjeta', value: 'TARJETA', icon: 'mdi-credit-card-outline' },
  { title: 'Transferencia', value: 'TRANSFERENCIA', icon: 'mdi-bank-transfer' },
  { title: 'Nequi', value: 'NEQUI', icon: 'mdi-cellphone' },
  { title: 'Daviplata', value: 'DAVIPLATA', icon: 'mdi-cellphone' },
  { title: 'PSE', value: 'PSE', icon: 'mdi-web' },
  { title: 'Datáfono', value: 'DATAFONO', icon: 'mdi-credit-card-chip-outline' },
  { title: 'Cheque', value: 'CHEQUE', icon: 'mdi-checkbook' },
  { title: 'Otro', value: 'OTRO', icon: 'mdi-dots-horizontal' },
]
export interface PagoInput { metodo: MetodoPago; monto: number; referencia?: string }
export interface Pago {
  id: number; fecha: string; metodo: MetodoPago; monto: number; referencia?: string | null
  estado: string; turnoId?: number | null; usuario?: Persona | null
}

export type EstadoFactura = 'EMITIDA' | 'PARCIAL' | 'PAGADA' | 'ANULADA'
export interface FacturaResumen {
  id: number; numero: string; tipoDocumento: string; estado: EstadoFactura; estadoDian: string
  tutor: TutorFact; fecha: string; total: number; saldo: number; condicionPago: string; fechaVencimiento?: string | null
}
export interface Factura extends FacturaResumen {
  pacienteId?: number | null; paciente?: string | null; consultaId?: number | null; presupuestoId?: number | null
  facturaOrigenId?: number | null; numeroOrigen?: string | null
  subtotal: number; descuento: number; impuestos: number; pagado: number
  notas?: string | null; motivoAnulacion?: string | null; cufe?: string | null; dianMensaje?: string | null
  resolucion?: { id: number; prefijo: string; numeroResolucion: string; ambiente: string } | null
  items: Linea[]; pagos: Pago[]; notasCredito: FacturaResumen[]
}

export interface MovimientoCaja { id: number; tipo: string; monto: number; concepto?: string | null; pagoId?: number | null; ocurridoEn: string; usuarioId?: number }
export interface Turno {
  id: number; cajaId: number; caja: string; usuarioId: number; usuario: string
  abiertoEn: string; cerradoEn?: string | null; estado: 'ABIERTO' | 'CERRADO'
  montoApertura: number; efectivoEsperado?: number | null; efectivoContado?: number | null
  diferencia?: number | null; observaciones?: string | null
  resumen: {
    montoApertura: number; ingresosEfectivo: number
    ingresosPorMetodo: { metodo: MetodoPago; monto: number; pagos: number }[]
    totalCobrado: number; ingresosManuales: number; egresos: number; retiros: number; efectivoEsperado: number
  }
  movimientos: MovimientoCaja[]
}
export interface Caja { id: number; nombre: string; activo: boolean; turnoAbiertoId?: number | null; turnoAbiertoPor?: string | null }

export interface CarteraTutor {
  tutorId: number; tutor: string; documento?: string | null; facturas: number; saldo: number
  saldoVencido: number; masAntigua?: string | null; diasMoraMax?: number | null
}
export interface CarteraDetalle {
  tutor: { id: number; nombre?: string; nombres?: string; apellidos?: string }
  saldo: number; saldoVencido: number
  facturas: { factura: FacturaResumen; vencimiento?: string | null; diasMora?: number | null }[]
}
export interface ReporteCartera {
  corte: string; total: number; facturas: number
  edades: { vigente: number; dias1a30: number; dias31a60: number; dias61a90: number; masDe90: number }
  tutores: CarteraTutor[]
}
export interface ReporteVentas {
  desde: string; hasta: string
  totales: { documentos: number; bruto: number; descuentos: number; impuestos: number; facturado: number; notasCredito: number; neto: number }
  porDia: { fecha: string; facturas: number; facturado: number; notasCredito: number; neto: number }[]
  porMetodoPago: { metodo: string; pagos: number; monto: number }[]
  porItem: { itemId?: number | null; codigo?: string | null; descripcion: string; cantidad: number; total: number }[]
}
