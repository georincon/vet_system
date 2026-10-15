export type Rol = 'ADMIN' | 'VETERINARIO' | 'RECEPCION' | 'AUXILIAR'

export const ROLES: Rol[] = ['ADMIN', 'VETERINARIO', 'RECEPCION', 'AUXILIAR']

export const ROL_LABELS: Record<Rol, string> = {
  ADMIN: 'Administrador',
  VETERINARIO: 'Veterinario',
  RECEPCION: 'Recepción',
  AUXILIAR: 'Auxiliar',
}

export interface Usuario {
  id: number
  username: string
  nombre: string
  email?: string
  rol: Rol
  activo: boolean
}

export interface UsuarioRequest {
  username: string
  password: string
  nombre: string
  email?: string
  rol: Rol
  activo?: boolean
}


export interface EstadoCita {
  id: number
  codigo: string
  nombre: string
  activo: boolean
}

export interface CitaRequest {
  pacienteId: number
  veterinarioId: number
  fecha: string
  horaInicio: string
  horaFin?: string | null
  tipoCitaId?: number | null
  salaId?: number | null
  quitarSala?: boolean
  motivo?: string | null
  notas?: string | null
  estadoCitaId?: number | null
}

export interface TipoCita {
  id: number
  nombre: string
  duracionMinutos: number
  color?: string | null
}

export type TipoSala =
  | 'CONSULTORIO' | 'QUIROFANO' | 'HOSPITALIZACION' | 'PELUQUERIA'
  | 'IMAGENES' | 'LABORATORIO' | 'GUARDERIA' | 'ESPERA'

export interface Sala {
  id: number
  nombre: string
  tipo: TipoSala
  capacidad: number
  activo: boolean
}

export interface VeterinarioOpcion {
  id: number
  nombre: string
}

export interface Cita {
  id: number
  codigo: string
  paciente: PacienteResumen
  veterinario: Usuario
  fecha: string
  horaInicio: string
  horaFin?: string | null
  estadoCita: EstadoCita
  tipoCita?: TipoCita | null
  sala?: Sala | null
  motivo?: string | null
  notas?: string | null
  horaLlegada?: string | null
  atencionInicio?: string | null
  activo: boolean
}

export interface ConsultaMedicamento {
  id?: number
  medicamentoId?: number | null
  nombre?: string | null
  dosis?: string | null
  frecuencia?: string | null
  dias?: number | null
  indicacion?: string | null
}

export interface ConsultaVacuna {
  id?: number
  vacunaId?: number | null
  nombre?: string | null
  lote?: string | null
  fechaAplicacion?: string | null
  proximaAplicacion?: string | null
  notas?: string | null
}

export interface ConsultaRequest {
  citaId?: number | null
  pacienteId: number
  veterinarioId: number
  fecha: string
  motivo?: string | null
  sintomatologia?: string | null
  diagnostico?: string | null
  tratamiento?: string | null
  observaciones?: string | null
  pesoKg?: number | null
  temperaturaC?: number | null
  frecuenciaCardiaca?: number | null
  frecuenciaRespiratoria?: number | null
  medicamentos?: ConsultaMedicamento[] | null
  vacunas?: ConsultaVacuna[] | null
}

export interface Consulta {
  id: number
  codigo: string
  citaId?: number | null
  paciente: PacienteResumen
  veterinario: Usuario
  fecha: string
  motivo?: string | null
  sintomatologia?: string | null
  diagnostico?: string | null
  tratamiento?: string | null
  observaciones?: string | null
  pesoKg?: number | null
  temperaturaC?: number | null
  frecuenciaCardiaca?: number | null
  frecuenciaRespiratoria?: number | null
  activo: boolean
  medicamentos?: ConsultaMedicamento[] | null
  vacunas?: ConsultaVacuna[] | null
  estado: EstadoConsulta
  cerrada: boolean
  cerradaEn?: string | null
  firmaHash?: string | null
  tipoCita?: TipoCita | null
  sala?: Sala | null
  tipoConsulta?: TipoConsulta | null
  triaje?: Triaje | null
  proximaRevision?: string | null
  plantillaId?: number | null
  anamnesis?: string | null
  subjetivo?: string | null
  objetivo?: string | null
  evaluacion?: string | null
  plan?: string | null
  pronostico?: string | null
  signosVitales: SignoVital[]
  diagnosticos: Diagnostico[]
  recetas: Receta[]
}

export type EstadoConsulta = 'ABIERTA' | 'CERRADA'

export type TipoConsulta =
  | 'GENERAL' | 'CONTROL' | 'URGENCIA' | 'VACUNACION' | 'PREQUIRURGICA'
  | 'POSTQUIRURGICA' | 'TELEMEDICINA' | 'DOMICILIO' | 'HOSPITALIZACION'

export type Triaje = 'ROJO' | 'NARANJA' | 'AMARILLO' | 'VERDE' | 'AZUL'

export interface LoginResponse {
  token: string
  expiraEn: number
  usuario: Usuario
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first?: boolean
  last?: boolean
}

export interface ApiErrorBody {
  timestamp?: string
  status?: number
  error?: string
  message?: string
  path?: string
  fieldErrors?: unknown[]
}

export type TipoDocumento = 'CC' | 'CE' | 'NIT' | 'PASAPORTE' | 'OTRO'

export const TIPOS_DOCUMENTO: TipoDocumento[] = ['CC', 'CE', 'NIT', 'PASAPORTE', 'OTRO']

export const TIPO_DOCUMENTO_LABELS: Record<TipoDocumento, string> = {
  CC: 'Cédula de ciudadanía',
  CE: 'Cédula de extranjería',
  NIT: 'NIT',
  PASAPORTE: 'Pasaporte',
  OTRO: 'Otro',
}

export type Sexo = 'M' | 'F'

export const SEXOS: Sexo[] = ['M', 'F']

export const SEXO_LABELS: Record<Sexo, string> = {
  M: 'Macho',
  F: 'Hembra',
}

export interface Especie {
  id: number
  nombre: string
  activo: boolean
}

export interface Raza {
  id: number
  especieId: number
  nombre: string
  activo: boolean
  grupoFci?: number | null
  numeroFci?: number | null
  origen?: string | null
  tamano?: string | null
  reconocimientoFci?: string | null
  potencialmentePeligrosa?: boolean
}

export interface Tutor {
  id: number
  tipoDocumento: TipoDocumento
  numeroDocumento: string
  nombres: string
  apellidos?: string
  telefono?: string
  telefonoAlt?: string
  email?: string
  direccion?: string
  ciudad?: string
  notas?: string
  activo: boolean
  aceptaEmail?: boolean
  aceptaWhatsapp?: boolean
  aceptaSms?: boolean
  autorizaTratamientoDatos?: boolean
}

export interface TutorRequest {
  tipoDocumento: TipoDocumento
  numeroDocumento: string
  nombres: string
  apellidos?: string
  telefono?: string
  telefonoAlt?: string
  email?: string
  direccion?: string
  ciudad?: string
  notas?: string
  activo?: boolean
  aceptaEmail?: boolean
  aceptaWhatsapp?: boolean
  aceptaSms?: boolean
  autorizaTratamientoDatos?: boolean
}

export interface TutorResumen {
  id: number
  nombres: string
  apellidos: string
  telefono?: string
  email?: string
  esPrincipal: boolean
  parentesco?: string
  aceptaEmail?: boolean
}

export interface Paciente {
  id: number
  codigo: string
  nombre: string
  especie: Especie
  raza: Raza | null
  sexo: Sexo
  estadoReproductivo?: string
  color?: string
  fechaNacimiento?: string
  microchip?: string
  pesoActual?: number | null
  esterilizado: boolean
  fallecido: boolean
  alertas?: string
  notas?: string
  activo: boolean
  tutores: TutorResumen[]
}

export interface PacienteResumen {
  id: number
  codigo: string
  nombre: string
  especie: string
  raza?: string
  sexo: Sexo
  fechaNacimiento?: string
  activo: boolean
}

export interface PacienteRequest {
  nombre: string
  especieId: number
  razaId?: number | null
  sexo: Sexo
  estadoReproductivo?: string
  color?: string
  fechaNacimiento?: string
  microchip?: string
  pesoActual?: number | null
  esterilizado?: boolean
  fallecido?: boolean
  alertas?: string
  notas?: string
  tutorIds: number[]
  tutorPrincipalId?: number | null
}

// ---- Consulta clínica (SOAP) ----

export interface SignoVitalRequest {
  temperatura?: number | null
  frecuenciaCardiaca?: number | null
  frecuenciaRespiratoria?: number | null
  pesoKg?: number | null
  condicionCorporal?: string | null
  puntajeCondicionCorporal?: number | null
  mucosas?: string | null
  tiempoLlenadoCapilar?: number | null
  escalaDolor?: number | null
  hidratacionPct?: number | null
  presionSistolica?: number | null
  spo2?: number | null
  glucosaMgDl?: number | null
  notas?: string | null
}

export interface SignoVital extends SignoVitalRequest {
  id: number
  registradoEn?: string | null
  registradoPor?: string | null
}

export type TipoDiagnostico = 'PRESUNTIVO' | 'DIFERENCIAL' | 'DEFINITIVO'

export interface DiagnosticoRequest {
  catalogoId?: number | null
  descripcion?: string | null
  codigoCie?: string | null
  tipo?: TipoDiagnostico
  orden?: number | null
  lateralidad?: string | null
  resuelto?: boolean
}

export interface Diagnostico {
  id: number
  catalogoId?: number | null
  codigoCatalogo?: string | null
  descripcion: string
  codigoCie?: string | null
  tipo: TipoDiagnostico
  orden?: number | null
  lateralidad?: string | null
  resuelto: boolean
}

export interface DiagnosticoCatalogo {
  id: number
  codigo: string
  nombre: string
  categoria?: string | null
}

export interface RecetaItem {
  id?: number
  itemId?: number | null
  medicamento: string
  dosis?: string | null
  via?: string | null
  frecuencia?: string | null
  duracion?: string | null
  cantidad?: number | null
  indicaciones?: string | null
  orden?: number | null
}

export type EstadoReceta = 'ACTIVA' | 'CUMPLIDA' | 'CANCELADA'

export interface RecetaRequest {
  indicacionesGenerales?: string | null
  validaHasta?: string | null
  esControlada?: boolean
  estado?: EstadoReceta
  items?: RecetaItem[]
}

export interface Receta {
  id: number
  fecha?: string
  validaHasta?: string | null
  estado: EstadoReceta
  esControlada: boolean
  indicacionesGenerales?: string | null
  items: RecetaItem[]
}

export type TipoAddendum = 'ACLARACION' | 'CORRECCION' | 'RESULTADO_TARDIO' | 'SEGUIMIENTO'

export interface Addendum {
  id: number
  tipo: TipoAddendum
  texto: string
  autor?: { id: number; nombre: string } | null
  creadoEn: string
}

export interface PlantillaSoap {
  id: number
  nombre: string
  especieId?: number | null
  tipoConsulta?: TipoConsulta | null
  subjetivo?: string | null
  objetivo?: string | null
  evaluacion?: string | null
  plan?: string | null
}

export interface ConsultaUpdate {
  motivo?: string
  anamnesis?: string
  subjetivo?: string
  objetivo?: string
  evaluacion?: string
  plan?: string
  pronostico?: string
  tipoConsulta?: TipoConsulta | null
  triaje?: Triaje | null
  proximaRevision?: string | null
  plantillaId?: number | null
}

// ---- Paciente: alergias, pesos, historia ----

export type SeveridadAlergia = 'BAJA' | 'MEDIA' | 'ALTA'
export type TipoAlergia = 'MEDICAMENTO' | 'ALIMENTO' | 'AMBIENTAL' | 'VACUNA' | 'OTRA'

export interface AlergiaRequest {
  descripcion: string
  severidad?: SeveridadAlergia
  tipo?: TipoAlergia
  reaccion?: string | null
  activo?: boolean
}

export interface Alergia {
  id: number
  descripcion: string
  severidad?: SeveridadAlergia | null
  tipo?: TipoAlergia | null
  reaccion?: string | null
  registradaEn?: string
  activo: boolean
}

export interface PesoRegistro {
  id: number
  pesoKg: number
  fecha: string
  registradoPor?: string | null
}

export type TipoHistoria = 'CONSULTA' | 'VACUNA' | 'DESPARASITACION' | 'PESO' | 'ALERGIA'

export interface HistoriaItem {
  tipo: TipoHistoria
  fecha: string
  referenciaId: number
  titulo: string
  detalle?: string | null
  estado?: string | null
}

// ---- Agenda ----

export interface BloqueoAgenda {
  id: number
  veterinario?: VeterinarioOpcion | null
  sala?: Sala | null
  inicio: string
  fin: string
  tipo: TipoBloqueo
  motivo?: string | null
}

export type TipoBloqueo =
  | 'VACACIONES' | 'CAPACITACION' | 'ALMUERZO' | 'MANTENIMIENTO' | 'CIRUGIA' | 'PERSONAL' | 'OTRO'

export interface BloqueoRequest {
  veterinarioId?: number | null
  salaId?: number | null
  inicio: string
  fin: string
  tipo: TipoBloqueo
  motivo?: string | null
}

export interface AgendaResponse {
  desde: string
  hasta: string
  citas: Cita[]
  bloqueos: BloqueoAgenda[]
}

export type FranjaPreferida = 'MANANA' | 'TARDE' | 'CUALQUIERA'
export type EstadoListaEspera = 'ACTIVA' | 'ASIGNADA' | 'CANCELADA' | 'VENCIDA'

export interface ListaEsperaRequest {
  pacienteId: number
  tutorId?: number | null
  tipoCitaId?: number | null
  veterinarioId?: number | null
  fechaDeseadaDesde?: string | null
  fechaDeseadaHasta?: string | null
  franjaPreferida?: FranjaPreferida
  prioridad?: number
  estado?: EstadoListaEspera
  notas?: string | null
}

export interface ListaEspera {
  id: number
  paciente: PacienteResumen
  tutor?: { id: number; nombre: string; telefono?: string | null } | null
  tipoCita?: TipoCita | null
  veterinario?: VeterinarioOpcion | null
  fechaDeseadaDesde?: string | null
  fechaDeseadaHasta?: string | null
  franjaPreferida?: FranjaPreferida | null
  prioridad: number
  estado: EstadoListaEspera
  citaAsignadaId?: number | null
  notas?: string | null
  creadoEn?: string
}

// ---- Dashboard y auditoría ----

export interface ProximaCita {
  id: number
  codigo: string
  fecha: string
  horaInicio: string
  horaFin?: string | null
  paciente: { id: number; nombre: string }
  veterinario: { id: number; nombre: string }
  estado: string
  motivo?: string | null
}

export interface DashboardResumen {
  citasHoy: number
  citasPendientes: number
  pacientesActivos: number
  tutoresActivos: number
  consultasAbiertas: number
  consultasMes: number
  vacunasPorVencer: number
  /** Null para VETERINARIO y AUXILIAR. */
  ventasHoy?: number | null
  ventasMes?: number | null
  stockBajo?: number | null
  lotesPorVencer?: number | null
  proximasCitas: ProximaCita[]
}

export interface AuditoriaEntry {
  id: number
  usuarioId?: number | null
  usuario?: string | null
  accion: 'INSERT' | 'UPDATE' | 'DELETE'
  entidad: string
  entidadId?: number | null
  detalle?: unknown
  ip?: string | null
  ocurridoEn: string
}
