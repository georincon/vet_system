<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { reporteVentas } from '@/api/facturacion'
import { errorText } from '@/utils/errors'
import { cant, cop } from '@/utils/dinero'
import { etiquetaFecha, hoy } from '@/utils/fecha'
import { METODOS_PAGO, type ReporteVentas } from '@/types/fase4'

const r = ref<ReporteVentas | null>(null)
const loading = ref(false)
const error = ref('')
const hoyIso = hoy()
const f = reactive({ desde: `${hoyIso.slice(0, 8)}01`, hasta: hoyIso })
const metodo = (m: string) => METODOS_PAGO.find((x) => x.value === m)?.title ?? m

async function cargar() {
  error.value = ''
  if (!f.desde || !f.hasta || f.desde > f.hasta) { error.value = 'Rango de fechas inválido'; return }
  loading.value = true
  try { r.value = await reporteVentas(f.desde, f.hasta) } catch (e) { error.value = errorText(e, 'No se pudo generar el reporte') } finally { loading.value = false }
}

/** Gráfico de barras SVG (una sola serie, neto por día). Los valores exactos están en la tabla de abajo y en cada barra (title). */
const GW = 640, GH = 180, PAD_L = 8, PAD_B = 22
const barras = computed(() => {
  const dias = r.value?.porDia ?? []
  const max = Math.max(1, ...dias.map((d) => d.neto))
  const ancho = (GW - PAD_L) / Math.max(1, dias.length)
  const grosor = Math.min(36, ancho * 0.6)
  return dias.map((d, i) => {
    const h = Math.max(2, (d.neto / max) * (GH - PAD_B - 8))
    return { ...d, x: PAD_L + i * ancho + (ancho - grosor) / 2, y: GH - PAD_B - h, w: grosor, h, cx: PAD_L + i * ancho + ancho / 2, mostrarEtiqueta: dias.length <= 12 || i % Math.ceil(dias.length / 10) === 0 }
  })
})
const maxMetodo = computed(() => Math.max(1, ...(r.value?.porMetodoPago ?? []).map((m) => m.monto)))
onMounted(cargar)
</script>

<template>
  <div>
    <div class="d-flex flex-wrap ga-2 mb-3 align-center">
      <v-text-field v-model="f.desde" type="date" label="Desde" hide-details density="comfortable" class="vet-filtro" />
      <v-text-field v-model="f.hasta" type="date" label="Hasta" hide-details density="comfortable" class="vet-filtro" />
      <v-btn color="primary" variant="flat" prepend-icon="mdi-chart-bar" :loading="loading" @click="cargar">Generar</v-btn>
      <span class="text-caption text-medium-emphasis">Máximo 366 días. Neto = facturas y POS menos notas crédito.</span>
    </div>
    <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-2" :text="error" />
    <template v-if="r">
      <v-row class="mb-2">
        <v-col cols="6" md="3"><div class="vet-info-bar"><div class="text-caption">Documentos</div><div class="text-h6">{{ r.totales.documentos }}</div></div></v-col>
        <v-col cols="6" md="3"><div class="vet-info-bar"><div class="text-caption">Facturado</div><div class="text-h6 vet-monto">{{ cop(r.totales.facturado) }}</div></div></v-col>
        <v-col cols="6" md="3"><div class="vet-info-bar"><div class="text-caption">Notas crédito</div><div class="text-h6 vet-monto">{{ cop(r.totales.notasCredito) }}</div></div></v-col>
        <v-col cols="6" md="3"><div class="vet-info-bar"><div class="text-caption">Neto</div><div class="text-h6 vet-monto text-success">{{ cop(r.totales.neto) }}</div></div></v-col>
      </v-row>
      <p class="text-caption text-medium-emphasis">Bruto {{ cop(r.totales.bruto) }} · descuentos {{ cop(r.totales.descuentos) }} · IVA {{ cop(r.totales.impuestos) }}</p>

      <h3 class="vet-section-title mt-4">Ventas netas por día</h3>
      <div v-if="r.porDia.length" class="vet-grafico">
        <svg :viewBox="`0 0 ${GW} ${GH}`" role="img" :aria-label="`Ventas netas por día, ${r.porDia.length} días con ventas`" class="vet-svg">
          <line :x1="PAD_L" :x2="GW" :y1="GH - PAD_B" :y2="GH - PAD_B" class="vet-eje" />
          <g v-for="b in barras" :key="b.fecha">
            <rect :x="b.x" :y="b.y" :width="b.w" :height="b.h" rx="3" class="vet-barra-svg"><title>{{ etiquetaFecha(b.fecha) }}: {{ cop(b.neto) }} ({{ b.facturas }} doc.)</title></rect>
            <text v-if="b.mostrarEtiqueta" :x="b.cx" :y="GH - 6" text-anchor="middle" class="vet-eje-texto">{{ b.fecha.slice(8) }}/{{ b.fecha.slice(5, 7) }}</text>
          </g>
        </svg>
      </div>
      <p v-else class="text-medium-emphasis">Sin ventas en el rango.</p>
      <v-table density="compact" class="mt-2">
        <thead><tr><th>Fecha</th><th class="text-end">Documentos</th><th class="text-end">Facturado</th><th class="text-end">Notas crédito</th><th class="text-end">Neto</th></tr></thead>
        <tbody>
          <tr v-for="d in r.porDia" :key="d.fecha"><td>{{ etiquetaFecha(d.fecha) }}</td><td class="text-end">{{ d.facturas }}</td><td class="text-end vet-monto">{{ cop(d.facturado) }}</td><td class="text-end vet-monto">{{ cop(d.notasCredito) }}</td><td class="text-end vet-monto"><strong>{{ cop(d.neto) }}</strong></td></tr>
        </tbody>
      </v-table>

      <v-row class="mt-2">
        <v-col cols="12" md="5">
          <h3 class="vet-section-title">Por método de pago</h3>
          <div v-for="m in r.porMetodoPago" :key="m.metodo" class="mb-3">
            <div class="d-flex justify-space-between text-body-2"><span>{{ metodo(m.metodo) }} <span class="text-medium-emphasis">({{ m.pagos }})</span></span><strong class="vet-monto">{{ cop(m.monto) }}</strong></div>
            <div class="vet-barra-pista"><div class="vet-barra" :style="{ width: (m.monto / maxMetodo * 100) + '%' }"></div></div>
          </div>
          <p v-if="!r.porMetodoPago.length" class="text-medium-emphasis">Sin pagos.</p>
          <p class="text-caption text-medium-emphasis">Por método no se restan las devoluciones.</p>
        </v-col>
        <v-col cols="12" md="7">
          <h3 class="vet-section-title">Por ítem</h3>
          <v-table density="compact">
            <thead><tr><th>Ítem</th><th class="text-end">Cantidad</th><th class="text-end">Total</th></tr></thead>
            <tbody>
              <tr v-if="!r.porItem.length"><td colspan="3" class="text-center text-medium-emphasis py-3">Sin ventas</td></tr>
              <tr v-for="(i, k) in r.porItem" :key="k"><td>{{ i.descripcion }}<div class="text-caption text-medium-emphasis">{{ i.codigo }}</div></td><td class="text-end">{{ cant(i.cantidad) }}</td><td class="text-end vet-monto">{{ cop(i.total) }}</td></tr>
            </tbody>
          </v-table>
        </v-col>
      </v-row>
    </template>
  </div>
</template>
