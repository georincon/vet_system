import type { TipoConsulta, Triaje } from '@/types'

export const TIPOS_CONSULTA: { title: string; value: TipoConsulta }[] = [
  { title: 'General', value: 'GENERAL' },
  { title: 'Control', value: 'CONTROL' },
  { title: 'Urgencia', value: 'URGENCIA' },
  { title: 'Vacunación', value: 'VACUNACION' },
  { title: 'Prequirúrgica', value: 'PREQUIRURGICA' },
  { title: 'Postquirúrgica', value: 'POSTQUIRURGICA' },
  { title: 'Telemedicina', value: 'TELEMEDICINA' },
  { title: 'Domicilio', value: 'DOMICILIO' },
  { title: 'Hospitalización', value: 'HOSPITALIZACION' },
]

export const TRIAJES: { title: string; value: Triaje; color: string }[] = [
  { title: 'Rojo (inmediato)', value: 'ROJO', color: 'red' },
  { title: 'Naranja (muy urgente)', value: 'NARANJA', color: 'orange' },
  { title: 'Amarillo (urgente)', value: 'AMARILLO', color: 'amber' },
  { title: 'Verde (estándar)', value: 'VERDE', color: 'green' },
  { title: 'Azul (no urgente)', value: 'AZUL', color: 'blue' },
]
