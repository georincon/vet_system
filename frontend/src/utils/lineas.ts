import type { LineaUI } from '@/components/facturacion/LineasEditor.vue'
import type { Linea } from '@/types/fase4'

let seq = 1
/** Renglón del backend a renglón editable. */
export function aLineaUI(l: Linea): LineaUI {
  return {
    key: ++seq, itemId: l.itemId ?? null, descripcion: l.descripcion ?? '', cantidad: Number(l.cantidad), precioUnitario: Number(l.precioUnitario ?? 0),
    impuestoPct: Number(l.impuestoPct ?? 0), descuento: Number(l.descuento ?? 0),
    extra: { consultaId: l.consultaId, pacienteId: l.pacienteId, veterinarioId: l.veterinarioId },
  }
}

/** Renglón editable a cuerpo de petición. Solo ADMIN puede fijar precio/IVA de un ítem del catálogo (el backend responde 422 si no). */
export function aLineaBody(l: LineaUI, esAdmin: boolean): Linea {
  const base: Linea = {
    itemId: l.itemId, descripcion: l.descripcion || undefined, cantidad: Number(l.cantidad), descuento: Number(l.descuento) || 0,
    consultaId: l.extra?.consultaId ?? undefined, pacienteId: l.extra?.pacienteId ?? undefined, veterinarioId: l.extra?.veterinarioId ?? undefined,
  }
  if (!l.itemId || esAdmin) { base.precioUnitario = Number(l.precioUnitario); base.impuestoPct = Number(l.impuestoPct) }
  return base
}
