/** Tipos de la Fase 3 (inmunización y comunicaciones). Contrato: docs/api-fase3.md */

export type TipoInmunizacion = 'VACUNA' | 'ANTIPARASITARIO'
export type ClasificacionVacuna = 'NUCLEO' | 'NO_NUCLEO' | 'NO_RECOMENDADA'
export type TipoAntiparasitario = 'INTERNA' | 'EXTERNA' | 'DIROFILARIA' | 'COMBINADA'

export const CLASIFICACIONES: { title: string; value: ClasificacionVacuna }[] = [
  { title: 'Núcleo', value: 'NUCLEO' },
  { title: 'No núcleo', value: 'NO_NUCLEO' },
  { title: 'No recomendada', value: 'NO_RECOMENDADA' },
]
export const TIPOS_ANTIPARASITARIO: { title: string; value: TipoAntiparasitario }[] = [
  { title: 'Interna', value: 'INTERNA' },
  { title: 'Externa', value: 'EXTERNA' },
  { title: 'Dirofilaria', value: 'DIROFILARIA' },
  { title: 'Combinada', value: 'COMBINADA' },
]

export interface Vacuna {
  id: number
  codigo: string
  nombre: string
  especieId?: number | null
  especie?: string | null
  periodoEficaciaDias: number
  clasificacion?: ClasificacionVacuna | null
  enfermedadesObjetivo?: string | null
  fabricante?: string | null
  viaAdministracion?: string | null
  dosisMl?: number | null
  itemId?: number | null
  obligatoriaLey?: boolean
  activo: boolean
}
export type VacunaRequest = Omit<Vacuna, 'id' | 'especie'>

export interface Antiparasitario {
  id: number
  codigo: string
  nombre: string
  tipo: TipoAntiparasitario
  principioActivo?: string | null
  especieId?: number | null
  especie?: string | null
  intervaloDias: number
  itemId?: number | null
  activo: boolean
}
export type AntiparasitarioRequest = Omit<Antiparasitario, 'id' | 'especie'>

export interface EsquemaDosis {
  id?: number
  orden: number
  nombreDosis: string
  edadMinimaSemanas?: number | null
  intervaloDiasPrevia?: number | null
  esRefuerzo: boolean
  repetirCadaDias?: number | null
}
export interface Esquema {
  id: number
  codigo: string
  nombre: string
  vacunaId: number
  vacuna?: string | null
  especieId?: number | null
  especie?: string | null
  fuente?: string | null
  notas?: string | null
  activo: boolean
  dosis: EsquemaDosis[]
}
export type EsquemaRequest = Omit<Esquema, 'id' | 'vacuna' | 'especie'>

export interface Inmunizacion {
  tipo: TipoInmunizacion
  id: number
  pacienteId: number
  productoId: number
  producto: string
  codigoProducto?: string | null
  clasificacion?: string | null
  dosisNumero?: number | null
  dosisNombre?: string | null
  dosis?: string | null
  fechaAplicacion: string
  proximaFecha?: string | null
  loteId?: number | null
  lote?: string | null
  fechaVencimientoLote?: string | null
  veterinario?: { id: number; nombre: string } | null
  sitioAplicacion?: string | null
  viaAdministracion?: string | null
  pesoKg?: number | null
  laboratorio?: string | null
  observaciones?: string | null
  reaccionAdversa?: string | null
  externa?: boolean
  consultaId?: number | null
  estado: 'APLICADA' | 'ANULADA' | string
  motivoAnulacion?: string | null
  anuladoEn?: string | null
}

export interface InmunizacionRequest {
  tipo: TipoInmunizacion
  vacunaId?: number | null
  antiparasitarioId?: number | null
  fechaAplicacion?: string
  loteId?: number | null
  lote?: string
  fechaVencimientoLote?: string | null
  dosisNumero?: number | null
  veterinarioId?: number | null
  sitioAplicacion?: string
  viaAdministracion?: string
  pesoKg?: number | null
  laboratorio?: string
  dosis?: string
  observaciones?: string
  reaccionAdversa?: string
  externa?: boolean
  proximaFecha?: string | null
}

export type EstadoEsquema = 'COMPLETA' | 'VENCIDA' | 'TOCA_AHORA' | 'PROXIMA' | 'AL_DIA' | 'FUTURA'
export interface EsquemaSugeridoItem {
  esquemaId: number
  esquema: string
  vacunaId: number
  vacuna: string
  clasificacion?: string | null
  obligatoriaLey?: boolean
  dosisAplicadas: number
  ultimaAplicacion?: string | null
  proximaDosisNumero?: number | null
  proximaDosisNombre?: string | null
  fechaSugerida?: string | null
  estado: EstadoEsquema
}
export interface EsquemaSugerido {
  pacienteId: number
  paciente: string
  especie: string
  fechaNacimiento?: string | null
  edadSemanas?: number | null
  items: EsquemaSugeridoItem[]
}

export interface PendienteTutor {
  id: number
  nombre: string
  telefono?: string | null
  email?: string | null
  aceptaEmail?: boolean
  aceptaWhatsapp?: boolean
}
export interface Pendiente {
  tipo: TipoInmunizacion
  aplicacionId: number
  pacienteId: number
  paciente: string
  especie: string
  productoId: number
  producto: string
  ultimaAplicacion?: string | null
  proximaFecha: string
  diasRestantes: number
  estado: 'VENCIDA' | 'POR_VENCER'
  tutor?: PendienteTutor | null
}

// ---- Comunicaciones ----
export type CanalMensaje = 'EMAIL' | 'WHATSAPP' | 'SMS' | 'PUSH'
export const CANALES: CanalMensaje[] = ['EMAIL', 'WHATSAPP', 'SMS', 'PUSH']
export type EstadoRecordatorio =
  'PENDIENTE' | 'ENVIADO' | 'ENVIADO_SIMULADO' | 'FALLIDO' | 'CANCELADO' | 'OMITIDO_OPTOUT'
export const ESTADOS_RECORDATORIO: EstadoRecordatorio[] =
  ['PENDIENTE', 'ENVIADO', 'ENVIADO_SIMULADO', 'FALLIDO', 'CANCELADO', 'OMITIDO_OPTOUT']

export interface Recordatorio {
  id: number
  reglaId?: number | null
  regla?: string | null
  tutorId: number
  tutor?: string | null
  pacienteId?: number | null
  paciente?: string | null
  tipo?: string | null
  referenciaTabla?: string | null
  referenciaId?: number | null
  canal: CanalMensaje
  programadoPara: string
  estado: EstadoRecordatorio
  intentos: number
  ultimoError?: string | null
  enviadoEn?: string | null
  creadoEn?: string | null
}

export interface EnvioLog {
  id: number
  recordatorioId?: number | null
  tutorId?: number | null
  canal: CanalMensaje
  destinatario?: string | null
  asunto?: string | null
  cuerpo?: string | null
  estado: string
  proveedor?: string | null
  proveedorMensajeId?: string | null
  error?: string | null
  enviadoEn?: string | null
}

export interface Plantilla {
  id: number
  codigo: string
  canal: CanalMensaje
  idioma: string
  asunto?: string | null
  cuerpo: string
  activo: boolean
  variables?: string[]
}
export type PlantillaRequest = Omit<Plantilla, 'id' | 'variables'>

export interface Regla {
  id: number
  codigo: string
  evento: string
  diasOffset: number
  canal: CanalMensaje
  plantillaId: number
  plantillaCodigo?: string | null
  canalDisponible?: boolean
  activo: boolean
}
export type ReglaRequest = Omit<Regla, 'id' | 'plantillaCodigo' | 'canalDisponible'>

export const EVENTOS_REGLA = ['VACUNA_PROXIMA', 'DESPARASITACION_PROXIMA', 'CITA']

export interface ResultadoEjecucion {
  fecha: string
  generados: number
  yaExistian: number
  omitidosSinConsentimiento: number
  omitidosSinContacto: number
  reglasSinCanal: number
  enviados: number
  simulados: number
  fallidos: number
  reintentosPendientes: number
  cancelados: number
  omitidosOptout: number
}
