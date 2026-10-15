export const ESTADO_ENVIO: Record<string, { color: string; text: string }> = {
  PENDIENTE: { color: 'warning', text: 'Pendiente' },
  ENVIADO: { color: 'success', text: 'Enviado' },
  ENVIADO_SIMULADO: { color: 'info', text: 'Enviado (simulado)' },
  FALLIDO: { color: 'error', text: 'Fallido' },
  CANCELADO: { color: 'grey', text: 'Cancelado' },
  OMITIDO_OPTOUT: { color: 'grey', text: 'Omitido (baja)' },
}
