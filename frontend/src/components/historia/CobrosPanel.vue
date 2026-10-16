<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getFactura, listFacturas, listPresupuestos } from '@/api/facturacion'
import EstadoChip from '@/components/EstadoChip.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { cop } from '@/utils/dinero'
import { errorText } from '@/utils/errors'
import { etiquetaFecha, etiquetaInstante } from '@/utils/fecha'
import type { TutorResumen } from '@/types'
import type { Factura, FacturaResumen, Presupuesto } from '@/types/fase4'

/** Solo lectura: sin facturar, sin notas crédito. Roles según la matriz de la Fase 4. */
const props = defineProps<{ pacienteId: number; tutores: TutorResumen[] }>()
const auth = useAuthStore()
const ui = useUiStore()

const alcance = ref<'paciente' | 'tutor'>('paciente')
const presupuestos = ref<Presupuesto[]>([])
const facturas = ref<FacturaResumen[]>([])
const detalle = ref<Record<number, Factura>>({})
const abiertas = ref<number[]>([])
const loading = ref(false)

const verFacturas = computed(() => auth.can.cobrosFacturas)
const tutorPrincipal = computed(() => props.tutores.find((t) => t.esPrincipal) ?? props.tutores[0] ?? null)
const total = computed(() => facturas.value.reduce((a, f) => a + (f.estado === 'ANULADA' ? 0 : f.total), 0))
const saldo = computed(() => facturas.value.reduce((a, f) => a + (f.estado === 'ANULADA' ? 0 : f.saldo), 0))

async function load() {
  loading.value = true
  const porTutor = alcance.value === 'tutor' && tutorPrincipal.value
  try {
    const filtro = porTutor ? { tutorId: tutorPrincipal.value!.id } : { pacienteId: props.pacienteId }
    const [p, f] = await Promise.all([
      auth.can.cobrosHistoria ? listPresupuestos({ ...filtro, size: 50 }).then((r) => r.items) : Promise.resolve([]),
      verFacturas.value ? listFacturas({ ...filtro, size: 50 }).then((r) => r.items) : Promise.resolve([]),
    ])
    presupuestos.value = p; facturas.value = f
  } catch (e) { ui.showError(errorText(e, 'No se pudo cargar el historial de cobros')) } finally { loading.value = false }
}
async function alternar(f: FacturaResumen) {
  if (abiertas.value.includes(f.id)) { abiertas.value = abiertas.value.filter((x) => x !== f.id); return }
  abiertas.value.push(f.id)
  if (!detalle.value[f.id]) {
    try { detalle.value[f.id] = await getFactura(f.id) } catch (e) { ui.showError(errorText(e, 'No se pudo cargar la factura')) }
  }
}
watch([alcance, () => props.pacienteId], load)
onMounted(load)
</script>

<template>
  <div>
    <div class="d-flex flex-wrap align-center ga-3 mb-3">
      <v-btn-toggle v-model="alcance" mandatory density="compact" color="primary" variant="outlined" divided aria-label="Alcance del historial">
        <v-btn value="paciente" size="small">Del paciente</v-btn>
        <v-btn value="tutor" size="small" :disabled="!tutorPrincipal">Del tutor{{ tutorPrincipal ? ` (${tutorPrincipal.nombres ?? ''})` : '' }}</v-btn>
      </v-btn-toggle>
      <v-chip size="small" variant="tonal" prepend-icon="mdi-eye-outline" text="Solo lectura" />
      <template v-if="verFacturas">
        <span class="text-body-2">Facturado: <strong class="vet-monto">{{ cop(total) }}</strong></span>
        <span class="text-body-2">Saldo: <strong class="vet-monto">{{ cop(saldo) }}</strong></span>
      </template>
    </div>
    <v-progress-linear v-if="loading" indeterminate color="primary" />
    <v-alert v-if="!verFacturas" type="info" variant="tonal" density="compact" class="mb-3"
      text="Su rol ve únicamente los presupuestos de sus propias consultas; las facturas y pagos las consulta el administrador." />

    <h3 class="vet-section-title">Presupuestos</h3>
    <v-table v-if="presupuestos.length" density="compact" class="mb-4">
      <thead><tr><th>Código</th><th>Fecha</th><th>Estado</th><th class="text-right">Total</th><th>Consulta</th></tr></thead>
      <tbody>
        <tr v-for="p in presupuestos" :key="p.id">
          <td>{{ p.codigo }}</td><td>{{ etiquetaFecha(p.fecha) }}</td><td><EstadoChip :estado="p.estado" /></td>
          <td class="text-right vet-monto">{{ cop(p.total) }}</td>
          <td><router-link v-if="p.consultaId" :to="{ name: 'consulta', params: { id: p.consultaId } }">Consulta #{{ p.consultaId }}</router-link><span v-else>—</span></td>
        </tr>
      </tbody>
    </v-table>
    <p v-else-if="!loading" class="text-medium-emphasis mb-4">Sin presupuestos.</p>

    <template v-if="verFacturas">
      <h3 class="vet-section-title">Facturas y pagos</h3>
      <v-table v-if="facturas.length" density="compact">
        <thead><tr><th></th><th>Número</th><th>Fecha</th><th>Estado</th><th class="text-right">Total</th><th class="text-right">Saldo</th></tr></thead>
        <tbody>
          <template v-for="f in facturas" :key="f.id">
            <tr>
              <td><v-btn size="x-small" variant="text" :icon="abiertas.includes(f.id) ? 'mdi-chevron-up' : 'mdi-chevron-down'" :aria-label="`Ver pagos de ${f.numero}`" @click="alternar(f)" /></td>
              <td>{{ f.numero }}</td><td>{{ etiquetaFecha(f.fecha) }}</td><td><EstadoChip :estado="f.estado" /></td>
              <td class="text-right vet-monto">{{ cop(f.total) }}</td><td class="text-right vet-monto">{{ cop(f.saldo) }}</td>
            </tr>
            <tr v-if="abiertas.includes(f.id)">
              <td></td>
              <td colspan="5">
                <div v-if="!detalle[f.id]" class="text-caption">Cargando…</div>
                <template v-else>
                  <div v-for="pg in detalle[f.id].pagos" :key="pg.id" class="text-body-2">
                    <v-icon icon="mdi-cash" size="x-small" /> {{ etiquetaInstante(pg.fecha) }} · {{ pg.metodo }} · <span class="vet-monto">{{ cop(pg.monto) }}</span>
                    <span v-if="pg.referencia" class="text-caption text-medium-emphasis"> · ref. {{ pg.referencia }}</span>
                  </div>
                  <div v-if="!detalle[f.id].pagos.length" class="text-caption text-medium-emphasis">Sin pagos registrados.</div>
                  <div v-for="n in detalle[f.id].notasCredito" :key="n.id" class="text-caption">Nota crédito {{ n.numero }}: {{ cop(n.total) }}</div>
                </template>
              </td>
            </tr>
          </template>
        </tbody>
      </v-table>
      <p v-else-if="!loading" class="text-medium-emphasis">Sin facturas.</p>
    </template>
  </div>
</template>
