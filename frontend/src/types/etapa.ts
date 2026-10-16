/**
 * Tipos de la Etapa A/B/C (documentación clínica avanzada, fórmula/plan y compartir con el tutor).
 * Contratos: docs/api-etapa-a-clinica.md y docs/api-etapa-b-compartir.md
 */
import type { Page } from '@/types'

export type { Page }

export interface PersonaRef { id: number; nombre: string }

// ======================================================================= archivos

export type TipoArchivo =
  | 'IMAGEN' | 'RADIOGRAFIA' | 'ECOGRAFIA' | 'DICOM' | 'PDF_LABORATORIO' | 'INFORME' | 'CONSENTIMIENTO' | 'OTRO'

export const TIPOS_ARCHIVO: { title: string; value: TipoArchivo }[] = [
  { title: 'Imagen', value: 'IMAGEN' },
  { title: 'Radiografía', value: 'RADIOGRAFIA' },
  { title: 'Ecografía', value: 'ECOGRAFIA' },
  { title: 'DICOM', value: 'DICOM' },
  { title: 'PDF de laboratorio', value: 'PDF_LABORATORIO' },
  { title: 'Informe', value: 'INFORME' },
  { title: 'Consentimiento', value: 'CONSENTIMIENTO' },
  { title: 'Otro', value: 'OTRO' },
]

export interface ArchivoInfo {
  id: number
  pacienteId: number
  consultaId?: number | null
  ordenExamenId?: number | null
  ordenItemId?: number | null
  cirugiaId?: number | null
  tipo: TipoArchivo
  nombreOriginal: string
  contentType: string
  tamanoBytes: number
  hashSha256?: string | null
  descripcion?: string | null
  tieneMiniatura: boolean
  anchoPx?: number | null
  altoPx?: number | null
  subidoPorId?: number | null
  subidoPorNombre?: string | null
  subidoEn: string
  activo: boolean
  eliminadoEn?: string | null
  motivoEliminacion?: string | null
  urlContenido: string
  urlMiniatura?: string | null
}

export interface ArchivoVinculo {
  consultaId?: number | null
  ordenExamenId?: number | null
  ordenItemId?: number | null
  cirugiaId?: number | null
}

export interface SubirArchivoRequest extends ArchivoVinculo {
  pacienteId: number
  tipo: TipoArchivo
  descripcion?: string
  archivo: File
}

/** Extensiones y tamaños que acepta el backend (validación previa en el cliente; el backend siempre revalida). */
export const EXT_ARCHIVO_PERMITIDAS = ['pdf', 'jpg', 'jpeg', 'png', 'webp', 'dcm', 'txt']
export const MAX_BYTES_ARCHIVO = 50 * 1024 * 1024
export const MAX_BYTES_DICOM = 256 * 1024 * 1024

// ======================================================================= laboratorio: catálogo

export type TipoExamen = 'LAB' | 'IMAGEN' | 'PATOLOGIA' | 'FUNCIONAL'
export const TIPOS_EXAMEN: { title: string; value: TipoExamen }[] = [
  { title: 'Laboratorio', value: 'LAB' },
  { title: 'Imagenología', value: 'IMAGEN' },
  { title: 'Patología / citología', value: 'PATOLOGIA' },
  { title: 'Funcional (ECG)', value: 'FUNCIONAL' },
]
export type TipoValor = 'NUMERICO' | 'TEXTO' | 'CUALITATIVO'
export type EstadoCodificacion = 'POR_VALIDAR' | 'VALIDADO'

export interface RangoRequest {
  especieId: number
  sexo?: 'M' | 'F' | null
  edadMinMeses?: number | null
  edadMaxMeses?: number | null
  valorMin?: number | null
  valorMax?: number | null
  criticoMin?: number | null
  criticoMax?: number | null
  fuente?: string | null
}
export interface Rango extends RangoRequest { id?: number; especie?: string | null }

export interface ParametroExamen {
  id?: number | null
  codigo: string
  nombre: string
  unidad?: string | null
  unidadUcum?: string | null
  tipoValor: TipoValor
  orden?: number | null
  codigoLoinc?: string | null
  codificacionEstado?: EstadoCodificacion | null
  valorNormalTexto?: string | null
  opciones?: string | null
  decimales?: number | null
  activo?: boolean
  rangos?: Rango[]
}

export interface ExamenCatalogo {
  id: number
  codigo: string
  nombre: string
  tipo: TipoExamen
  categoria?: string | null
  descripcion?: string | null
  muestra?: string | null
  requiereAyuno: boolean
  tiempoEntregaHoras?: number | null
  esExterno: boolean
  activo: boolean
  itemId?: number | null
  codigoLoinc?: string | null
  loincEstado?: EstadoCodificacion | null
  esPanel: boolean
  parametros?: ParametroExamen[]
}

export interface ExamenRequest {
  codigo: string
  nombre: string
  tipo: TipoExamen
  categoria?: string | null
  descripcion?: string | null
  muestra?: string | null
  requiereAyuno?: boolean
  tiempoEntregaHoras?: number | null
  esExterno?: boolean
  itemId?: number | null
  codigoLoinc?: string | null
  loincEstado?: EstadoCodificacion | null
  activo?: boolean
  parametros?: ParametroExamen[]
}

// ======================================================================= laboratorio: órdenes

export type EstadoOrden = 'SOLICITADA' | 'MUESTRA_TOMADA' | 'EN_PROCESO' | 'COMPLETADA' | 'CANCELADA'
export const ESTADOS_ORDEN: { title: string; value: EstadoOrden }[] = [
  { title: 'Solicitada', value: 'SOLICITADA' },
  { title: 'Muestra tomada', value: 'MUESTRA_TOMADA' },
  { title: 'En proceso', value: 'EN_PROCESO' },
  { title: 'Completada', value: 'COMPLETADA' },
  { title: 'Cancelada', value: 'CANCELADA' },
]
export type PrioridadOrden = 'RUTINA' | 'URGENTE' | 'STAT'
export const PRIORIDADES: { title: string; value: PrioridadOrden }[] = [
  { title: 'Rutina', value: 'RUTINA' },
  { title: 'Urgente', value: 'URGENTE' },
  { title: 'STAT (inmediata)', value: 'STAT' },
]
export type EstadoItemOrden = 'SOLICITADO' | 'RESULTADO' | 'VALIDADO' | 'CANCELADO'
export type Bandera = 'N' | 'L' | 'H' | 'LL' | 'HH' | 'A'
export type EstadoResultado = 'BORRADOR' | 'VALIDADO' | 'CORREGIDO'
export type OrigenResultado = 'MANUAL' | 'ARCHIVO' | 'INTEGRACION'

export interface OrdenRequest {
  pacienteId: number
  consultaId?: number | null
  prioridad?: PrioridadOrden
  indicacionClinica?: string
  laboratorioExterno?: string
  examenIds?: number[]
  examenCodigos?: string[]
}

export interface OrdenResumen {
  id: number
  codigo: string
  pacienteId: number
  pacienteNombre: string
  pacienteCodigo: string
  especie?: string | null
  consultaId?: number | null
  prioridad: PrioridadOrden
  estado: EstadoOrden
  solicitadaEn: string
  solicitadoPor?: PersonaRef | null
  laboratorioExterno?: string | null
  itemsTotal: number
  itemsValidados: number
  hayCriticos: boolean
  examenes: string[]
}

export interface EventoOrden {
  id: number
  tipo: string
  estadoAnterior?: string | null
  estadoNuevo?: string | null
  detalle?: string | null
  usuario?: PersonaRef | null
  origen?: string | null
  creadoEn: string
}

export interface ResultadoExamen {
  id: number
  parametroId?: number | null
  parametroCodigo: string
  parametroNombre: string
  codigoLoinc?: string | null
  valorNumerico?: number | null
  valorTexto?: string | null
  unidad?: string | null
  refMin?: number | null
  refMax?: number | null
  refCriticoMin?: number | null
  refCriticoMax?: number | null
  bandera?: Bandera | null
  critico: boolean
  estado: EstadoResultado
  origen: OrigenResultado
  observacion?: string | null
  registradoEn?: string | null
  registradoPor?: PersonaRef | null
  validacionId?: number | null
  reemplazaA?: number | null
  reemplazadoPor?: number | null
  motivoCorreccion?: string | null
}

export interface InformeImagen {
  id: number
  version: number
  estado: string
  region: string
  tecnica?: string | null
  hallazgos?: string | null
  impresionDiagnostica?: string | null
  recomendaciones?: string | null
  equipo?: string | null
  fechaEstudio?: string | null
  informadoPor?: PersonaRef | null
  reemplazaA?: number | null
  motivoCorreccion?: string | null
  creadoEn?: string
}
export interface InformeImagenRequest {
  region: string
  tecnica?: string | null
  hallazgos?: string | null
  impresionDiagnostica?: string | null
  recomendaciones?: string | null
  equipo?: string | null
  fechaEstudio?: string | null
  informadoPorId?: number | null
}

export interface InformePatologia {
  id: number
  version: number
  estado: string
  tipoMuestra: string
  sitioAnatomico?: string | null
  fechaToma?: string | null
  descripcionMacroscopica?: string | null
  descripcionMicroscopica?: string | null
  diagnosticoMorfologico?: string | null
  margenes?: string | null
  comentarios?: string | null
  laboratorioExterno?: string | null
  numeroCasoExterno?: string | null
  patologo?: string | null
  fechaInforme?: string | null
  informadoPor?: PersonaRef | null
  reemplazaA?: number | null
  motivoCorreccion?: string | null
  creadoEn?: string
}
export interface InformePatologiaRequest {
  tipoMuestra: string
  sitioAnatomico?: string | null
  fechaToma?: string | null
  descripcionMacroscopica?: string | null
  descripcionMicroscopica?: string | null
  diagnosticoMorfologico?: string | null
  margenes?: string | null
  comentarios?: string | null
  laboratorioExterno?: string | null
  numeroCasoExterno?: string | null
  patologo?: string | null
  fechaInforme?: string | null
  informadoPorId?: number | null
}

export interface ItemOrden {
  id: number
  examenId: number
  examenCodigo: string
  examenNombre: string
  tipo: TipoExamen
  estado: EstadoItemOrden
  interpretacion?: string | null
  validadoPor?: PersonaRef | null
  validadoEn?: string | null
  version: number
  corregido: boolean
  capturadoPor?: PersonaRef | null
  capturadoEn?: string | null
  parametrosEsperados: number
  resultados: ResultadoExamen[]
  resultadosCorregidos: ResultadoExamen[]
  informeImagen?: InformeImagen | null
  informePatologia?: InformePatologia | null
  archivos: ArchivoInfo[]
  motivoCancelacion?: string | null
}

export interface OrdenDetalle {
  id: number
  codigo: string
  pacienteId: number
  pacienteNombre: string
  pacienteCodigo: string
  especie?: string | null
  consultaId?: number | null
  prioridad: PrioridadOrden
  estado: EstadoOrden
  indicacionClinica?: string | null
  laboratorioExterno?: string | null
  solicitadaEn: string
  solicitadoPor?: PersonaRef | null
  muestraTomadaEn?: string | null
  muestraTomadaPor?: PersonaRef | null
  muestraObservaciones?: string | null
  completadaEn?: string | null
  canceladaEn?: string | null
  motivoCancelacion?: string | null
  hayCriticos: boolean
  items: ItemOrden[]
  eventos: EventoOrden[]
  archivos: ArchivoInfo[]
}

export interface ResultadoEntrada {
  parametroId?: number | null
  parametroCodigo?: string | null
  valorNumerico?: number | null
  valorTexto?: string | null
  unidad?: string | null
  observacion?: string | null
}

export interface CorreccionRequest {
  motivo: string
  interpretacion?: string | null
  resultados?: ResultadoEntrada[]
  informeImagen?: InformeImagenRequest
  informePatologia?: InformePatologiaRequest
}

export interface OrdenFiltro {
  estado?: string | null
  prioridad?: string | null
  pacienteId?: number | null
  consultaId?: number | null
  tipo?: TipoExamen | null
  desde?: string
  hasta?: string
  q?: string
  page?: number
  size?: number
}

// ---- importación

export type FormatoImportacion = 'HL7' | 'CSV' | 'JSON'
export interface LineaJson {
  examenCodigo?: string
  parametroCodigo?: string
  loinc?: string
  valor: string
  unidad?: string
  observacion?: string
}
export interface ImportacionRequest {
  formato: FormatoImportacion
  contenido?: string
  resultados?: LineaJson[]
  ordenId?: number | null
  ordenCodigo?: string
  dryRun?: boolean
  origen?: 'ARCHIVO' | 'INTEGRACION'
}
export interface LineaImportacion {
  linea: number
  ordenCodigo?: string | null
  examenCodigo?: string | null
  parametroCodigo?: string | null
  loinc?: string | null
  valor?: string | null
  unidad?: string | null
  estado: string
  itemId?: number | null
  parametroId?: number | null
  parametro?: string | null
  bandera?: Bandera | null
  critico?: boolean | null
  mensaje?: string | null
}
export interface ImportacionResultado {
  formato: FormatoImportacion
  origen: string
  dryRun: boolean
  total: number
  importados: number
  noCoinciden: number
  rechazados: number
  ordenIds: number[]
  importacionId?: number | null
  lineas: LineaImportacion[]
  advertencias: string[]
}

// ---- tendencia

export interface PuntoTendencia {
  resultadoId: number
  fecha: string
  ordenId: number
  ordenCodigo: string
  valorNumerico?: number | null
  valorTexto?: string | null
  unidad?: string | null
  bandera?: Bandera | null
  critico: boolean
  refMin?: number | null
  refMax?: number | null
}
export interface ResumenTendencia {
  puntos: number
  ultimo?: number | null
  anterior?: number | null
  variacionAbsoluta?: number | null
  variacionPorcentual?: number | null
  direccion: 'SUBE' | 'BAJA' | 'ESTABLE' | 'SIN_DATOS'
  minimo?: number | null
  maximo?: number | null
}
export interface Tendencia {
  pacienteId: number
  parametro: { id: number; codigo: string; nombre: string; unidad?: string | null; tipoValor: TipoValor }
  puntos: PuntoTendencia[]
  resumen: ResumenTendencia
}

// ======================================================================= procedimientos / cirugía

export type TipoProcedimiento = 'PROCEDIMIENTO' | 'CIRUGIA'
export type EstadoProcedimiento = 'PROGRAMADA' | 'EN_CURSO' | 'REALIZADA' | 'CANCELADA' | 'APLAZADA'
export type TipoAnestesia = 'NINGUNA' | 'LOCAL' | 'SEDACION' | 'GENERAL' | 'EPIDURAL' | 'OTRA'
export const TIPOS_ANESTESIA: { title: string; value: TipoAnestesia }[] = [
  { title: 'Ninguna', value: 'NINGUNA' },
  { title: 'Local', value: 'LOCAL' },
  { title: 'Sedación', value: 'SEDACION' },
  { title: 'General', value: 'GENERAL' },
  { title: 'Epidural', value: 'EPIDURAL' },
  { title: 'Otra', value: 'OTRA' },
]
export type MetodoFirma = 'TABLET' | 'PAPEL_ESCANEADO' | 'OTP' | 'ENLACE'
export const METODOS_FIRMA: { title: string; value: MetodoFirma }[] = [
  { title: 'Firma en tableta', value: 'TABLET' },
  { title: 'Papel escaneado', value: 'PAPEL_ESCANEADO' },
  { title: 'Código OTP', value: 'OTP' },
  { title: 'Enlace', value: 'ENLACE' },
]

export interface InsumoProcedimiento {
  id?: number
  itemId?: number | null
  descripcion: string
  cantidad: number | null
  lote?: string | null
  notas?: string | null
}

export interface ProcedimientoCampos {
  itemId?: number | null
  procedimiento?: string | null
  veterinarioId?: number | null
  ayudanteId?: number | null
  anestesiologoId?: number | null
  programadaPara?: string | null
  inicioEn?: string | null
  finEn?: string | null
  asa?: number | null
  urgencia?: boolean
  ayunoHoras?: number | null
  pesoKg?: number | null
  anestesiaTipo?: TipoAnestesia | null
  premedicacion?: string | null
  induccion?: string | null
  mantenimiento?: string | null
  analgesia?: string | null
  diagnosticoPreoperatorio?: string | null
  descripcionTecnica?: string | null
  hallazgos?: string | null
  complicaciones?: string | null
  materialSutura?: string | null
  muestrasHistopatologia?: boolean
  diagnosticoPostoperatorio?: string | null
  resultado?: string | null
  indicacionesPostoperatorias?: string | null
  controles?: string | null
  proximoControl?: string | null
  insumos?: InsumoProcedimiento[]
}

export interface ProcedimientoRequest extends ProcedimientoCampos {
  pacienteId: number
  consultaId?: number | null
  tipo?: TipoProcedimiento
  estado?: EstadoProcedimiento
}

export interface ProcedimientoResumen {
  id: number
  codigo: string
  tipo: TipoProcedimiento
  procedimiento: string
  estado: EstadoProcedimiento
  pacienteId: number
  pacienteNombre: string
  consultaId?: number | null
  cirujano?: PersonaRef | null
  programadaPara?: string | null
  inicioEn?: string | null
  finEn?: string | null
  urgencia: boolean
  consentimientoEstado?: string | null
}

export interface Consentimiento {
  id: number
  tipo: string
  estado: 'FIRMADO' | 'RECHAZADO' | string
  tutor?: PersonaRef | null
  textoFirmado?: string | null
  firmanteNombre?: string | null
  firmanteDocumento?: string | null
  metodoFirma?: MetodoFirma | null
  firmadoEn?: string | null
  archivoId?: number | null
  registradoPor?: PersonaRef | null
}

export interface MonitoreoAnestesico {
  id: number
  registradoEn: string
  frecuenciaCardiaca?: number | null
  frecuenciaRespiratoria?: number | null
  spo2?: number | null
  etco2?: number | null
  presionSistolica?: number | null
  temperatura?: number | null
  agenteInhalatorioPct?: number | null
  fluidoMlH?: number | null
  notas?: string | null
  registradoPor?: PersonaRef | null
}
export interface MonitoreoRequest {
  registradoEn?: string | null
  frecuenciaCardiaca?: number | null
  frecuenciaRespiratoria?: number | null
  spo2?: number | null
  etco2?: number | null
  presionSistolica?: number | null
  temperatura?: number | null
  agenteInhalatorioPct?: number | null
  fluidoMlH?: number | null
  notas?: string | null
}

export interface ConsentimientoRequest {
  tutorId?: number | null
  texto?: string | null
  aceptado: boolean
  firmanteNombre: string
  firmanteDocumento?: string | null
  metodoFirma?: MetodoFirma | null
  fecha?: string | null
  archivoId?: number | null
}

export interface Procedimiento {
  id: number
  codigo: string
  tipo: TipoProcedimiento
  estado: EstadoProcedimiento
  pacienteId: number
  pacienteNombre: string
  pacienteCodigo: string
  consultaId?: number | null
  itemId?: number | null
  procedimiento: string
  cirujano?: PersonaRef | null
  ayudante?: PersonaRef | null
  anestesiologo?: PersonaRef | null
  programadaPara?: string | null
  inicioEn?: string | null
  finEn?: string | null
  asa?: number | null
  urgencia: boolean
  ayunoHoras?: number | null
  pesoKg?: number | null
  anestesiaTipo?: TipoAnestesia | null
  premedicacion?: string | null
  induccion?: string | null
  mantenimiento?: string | null
  analgesia?: string | null
  diagnosticoPreoperatorio?: string | null
  descripcionTecnica?: string | null
  hallazgos?: string | null
  complicaciones?: string | null
  materialSutura?: string | null
  muestrasHistopatologia: boolean
  diagnosticoPostoperatorio?: string | null
  resultado?: string | null
  indicacionesPostoperatorias?: string | null
  controles?: string | null
  proximoControl?: string | null
  motivoCancelacion?: string | null
  creadoEn?: string
  consentimiento?: Consentimiento | null
  insumos: InsumoProcedimiento[]
  monitoreo: MonitoreoAnestesico[]
  archivos: ArchivoInfo[]
}

// ======================================================================= fórmula, plan, controles, perfil

export type TipoItemPlan = 'MEDICACION' | 'PROCEDIMIENTO' | 'DIETA' | 'CUIDADO' | 'EXAMEN' | 'CONTROL' | 'OTRO'
export const TIPOS_ITEM_PLAN: { title: string; value: TipoItemPlan }[] = [
  { title: 'Medicación', value: 'MEDICACION' },
  { title: 'Procedimiento', value: 'PROCEDIMIENTO' },
  { title: 'Dieta', value: 'DIETA' },
  { title: 'Cuidado', value: 'CUIDADO' },
  { title: 'Examen', value: 'EXAMEN' },
  { title: 'Control', value: 'CONTROL' },
  { title: 'Otro', value: 'OTRO' },
]
export type EstadoPlan = 'ACTIVO' | 'COMPLETADO' | 'CANCELADO'
export type EstadoControl = 'PENDIENTE' | 'AGENDADO' | 'REALIZADO' | 'CANCELADO'

export interface PlanItem {
  id?: number
  orden?: number
  fase?: string | null
  tipo?: TipoItemPlan | null
  descripcion: string
  fechaInicio?: string | null
  fechaFin?: string | null
}

export interface PlanRequest {
  objetivo?: string | null
  recomendaciones?: string | null
  advertencias?: string | null
  estado?: EstadoPlan
  items?: PlanItem[]
}

export interface CitaSugerida {
  pacienteId: number
  veterinarioId?: number | null
  fecha: string
  motivo?: string | null
}
export interface RecordatorioControl {
  solicitado: boolean
  generado: boolean
  recordatorioId?: number | null
  detalle?: string | null
}
export interface ControlSeguimiento {
  id: number
  consultaId: number
  planId?: number | null
  pacienteId: number
  fechaSugerida: string
  motivo: string
  estado: EstadoControl
  citaId?: number | null
  creadoEn?: string
  citaSugerida?: CitaSugerida | null
  recordatorio?: RecordatorioControl | null
}
export interface ControlRequest {
  fechaSugerida?: string | null
  motivo?: string | null
  estado?: EstadoControl
  citaId?: number | null
  generarRecordatorio?: boolean
}

export interface PlanTratamiento {
  id: number
  consultaId: number
  pacienteId: number
  consultaCerrada: boolean
  objetivo?: string | null
  recomendaciones?: string | null
  advertencias?: string | null
  estado: EstadoPlan
  items: PlanItem[]
  controles: ControlSeguimiento[]
  creadoEn?: string
  actualizadoEn?: string
}

export interface PerfilProfesional {
  usuarioId: number
  nombre: string
  numeroProfesional?: string | null
  especialidad?: string | null
}

export interface VerificacionDocumento {
  valido: boolean
  tipoDocumento?: string
  numero?: string
  fecha?: string
  clinica?: string
  veterinario?: string
  tarjetaProfesional?: string | null
  estado?: 'VIGENTE' | 'CUMPLIDA' | 'VENCIDA' | 'ANULADA' | 'BORRADOR' | string
}

// ======================================================================= compartir

export type CanalCompartir = 'EMAIL' | 'WHATSAPP'
export type TipoDocumentoCompartir =
  | 'FORMULA' | 'LABORATORIO' | 'IMAGEN' | 'PATOLOGIA' | 'PROCEDIMIENTO' | 'CARNET' | 'RESUMEN_CONSULTA'

export const TIPO_DOCUMENTO_COMPARTIR_LABEL: Record<TipoDocumentoCompartir, string> = {
  FORMULA: 'Fórmula médica',
  LABORATORIO: 'Resultados de laboratorio',
  IMAGEN: 'Informe de imagen',
  PATOLOGIA: 'Informe de patología',
  PROCEDIMIENTO: 'Reporte de procedimiento',
  CARNET: 'Carné de vacunación',
  RESUMEN_CONSULTA: 'Resumen de consulta',
}

export interface DocumentoRef { tipo: TipoDocumentoCompartir; id: number; itemId?: number | null }

/** Documento ofrecido por quien monta el botón "Enviar al tutor". */
export interface DocumentoCompartible extends DocumentoRef {
  titulo: string
  /** Si es false el documento se muestra deshabilitado con `motivo` (p. ej. borrador). */
  enviable?: boolean
  motivo?: string
}

export interface CompartirRequest {
  pacienteId: number
  tutorId?: number | null
  canal: CanalCompartir
  documentos: DocumentoRef[]
  mensaje?: string
  veterinarioResponsableId?: number | null
}

export type EstadoEnvio = 'ENVIADO' | 'ENVIADO_SIMULADO' | 'FALLIDO'

export interface DocumentoEnvio {
  id: number
  tipo: TipoDocumentoCompartir
  documentoId: number
  itemId?: number | null
  titulo: string
  nombreArchivo: string
  tamanoBytes: number
  hashSha256?: string
}
export interface EnlaceSeguro {
  id: number
  documentoEnvioId: number
  tokenPrefijo: string
  /** Solo en la respuesta de envío/reenvío. */
  url?: string | null
  expiraEn: string
  maxDescargas: number
  descargas: number
  revocado: boolean
  ultimoAcceso?: string | null
}
export interface Envio {
  id: number
  pacienteId: number
  tutorId: number
  canal: CanalCompartir
  destinatario: string
  asunto?: string | null
  mensaje: string
  estado: EstadoEnvio
  intentos: number
  ultimoError?: string | null
  proveedor: string
  creadoPor?: number | null
  creadoPorNombre?: string | null
  responsableId?: number | null
  creadoEn: string
  enviadoEn?: string | null
  revocado: boolean
  revocadoEn?: string | null
  documentos: DocumentoEnvio[]
  enlaces: EnlaceSeguro[]
  urlWaMe?: string | null
}

export interface DocumentoPrevio {
  tipo: TipoDocumentoCompartir
  id: number
  titulo?: string | null
  enviable: boolean
  problema?: string | null
}
export interface Previsualizacion {
  tutorId: number
  tutor: string
  canal: CanalCompartir
  destinatario?: string | null
  consentimientoCanal: boolean
  autorizaTratamientoDatos: boolean
  bajaComunicaciones: boolean
  contactoValido: boolean
  faltantes: string[]
  puedeEnviar: boolean
  asunto?: string | null
  mensaje: string
  documentos: DocumentoPrevio[]
}
