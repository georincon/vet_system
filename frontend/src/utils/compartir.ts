import { computed, ref } from 'vue'
import { historialEnvios } from '@/api/compartir'
import { useAuthStore } from '@/stores/auth'
import { http } from '@/api/client'
import type { DocumentoCompartible, OrdenDetalle, Procedimiento, TipoDocumentoCompartir, TipoExamen } from '@/types/etapa'

/** Tipos que el AUXILIAR puede enviar cuando la clínica lo habilita (VET_COMPARTIR_AUXILIAR). */
export const TIPOS_AUXILIAR: TipoDocumentoCompartir[] = ['FORMULA', 'LABORATORIO', 'IMAGEN', 'PATOLOGIA', 'CARNET']

/** null = aún no se sabe; true/false = respuesta del backend al probar el historial de envíos. */
const auxiliarHabilitado = ref<boolean | null>(null)
let sondeo: Promise<void> | null = null

/** Sondea una sola vez si el backend permite compartir al AUXILIAR (403 = deshabilitado, sin mostrar error). */
export function sondearCompartirAuxiliar(): Promise<void> {
  if (auxiliarHabilitado.value !== null) return Promise.resolve()
  if (!sondeo) {
    sondeo = http.get('/compartir/historial', { params: { size: 1 }, validateStatus: (s) => s === 200 || s === 403 })
      .then((r) => { auxiliarHabilitado.value = r.status === 200 })
      .catch(() => { auxiliarHabilitado.value = false })
  }
  return sondeo
}

/** Reactivo: ¿puede el usuario actual compartir documentos con el tutor? */
export function usePuedeCompartir() {
  const auth = useAuthStore()
  const esAux = computed(() => auth.rol === 'AUXILIAR')
  if (esAux.value) void sondearCompartirAuxiliar()
  const puede = computed(() => auth.can.compartir || (esAux.value && auxiliarHabilitado.value === true))
  return { puede, esAuxiliar: esAux }
}

/** Historial de envíos: ADMIN/VET siempre; AUXILIAR solo si está habilitado. */
export function puedeVerHistorialEnvios(): boolean {
  const auth = useAuthStore()
  return auth.can.compartir || (auth.rol === 'AUXILIAR' && auxiliarHabilitado.value === true)
}
export { historialEnvios }

/**
 * Documentos compartibles de una orden: un LABORATORIO (todos los exámenes LAB), un IMAGEN y un PATOLOGIA por examen.
 * Solo son enviables los que tienen todos sus exámenes del tipo VALIDADOS (los cancelados no cuentan).
 */
export function documentosDeOrden(o: OrdenDetalle): DocumentoCompartible[] {
  const out: DocumentoCompartible[] = []
  const porTipo: [TipoExamen[], TipoDocumentoCompartir, string][] = [
    [['LAB'], 'LABORATORIO', 'Resultados de laboratorio'],
    [['PATOLOGIA'], 'PATOLOGIA', 'Informe de patología'],
  ]
  for (const [tipos, doc, etiqueta] of porTipo) {
    const items = o.items.filter((i) => tipos.includes(i.tipo) && i.estado !== 'CANCELADO')
    if (!items.length) continue
    const pend = items.filter((i) => i.estado !== 'VALIDADO')
    out.push({
      tipo: doc, id: o.id, titulo: `${etiqueta} ${o.codigo}`, enviable: pend.length === 0,
      motivo: pend.length ? `Faltan por validar: ${pend.map((i) => i.examenNombre).join(', ')}. Solo se comparten resultados validados.` : undefined,
    })
  }
  for (const i of o.items.filter((x) => (x.tipo === 'IMAGEN' || x.tipo === 'FUNCIONAL') && x.estado !== 'CANCELADO')) {
    out.push({
      tipo: 'IMAGEN', id: o.id, itemId: i.id, titulo: `${i.examenNombre} (${o.codigo})`, enviable: i.estado === 'VALIDADO',
      motivo: i.estado === 'VALIDADO' ? undefined : 'El informe aún no está validado. Solo se comparten informes validados.',
    })
  }
  return out
}

export function documentoDeProcedimiento(p: Procedimiento): DocumentoCompartible {
  const ok = p.estado === 'REALIZADA'
  return {
    tipo: 'PROCEDIMIENTO', id: p.id, titulo: `${p.tipo === 'CIRUGIA' ? 'Cirugía' : 'Procedimiento'} ${p.codigo}`, enviable: ok,
    motivo: ok ? undefined : 'Solo se comparten procedimientos REALIZADOS.',
  }
}
