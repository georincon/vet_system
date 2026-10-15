const COP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', minimumFractionDigits: 0, maximumFractionDigits: 2 })
const NUM = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 3 })

/** Dinero en pesos colombianos (es-CO). */
export function cop(v?: number | null): string {
  return v === null || v === undefined || Number.isNaN(Number(v)) ? '—' : COP.format(Number(v))
}

/** Cantidades (hasta 3 decimales). */
export function cant(v?: number | null): string {
  return v === null || v === undefined || Number.isNaN(Number(v)) ? '—' : NUM.format(Number(v))
}

/** Redondeo a centavos (HALF_UP como el backend) para comparar sumas de pagos. */
export function r2(v: number): number {
  return Math.round((v + Number.EPSILON) * 100) / 100
}

/** Cálculo de un renglón igual al del backend: base = bruto - descuento; IVA = base * pct. */
export function calcLinea(cantidad: number, precio: number, descuento: number, ivaPct: number) {
  const bruto = r2(cantidad * precio)
  const base = r2(bruto - descuento)
  const impuesto = r2(base * ivaPct / 100)
  return { bruto, base, impuesto, total: r2(base + impuesto) }
}
