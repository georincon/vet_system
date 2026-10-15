<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/PageHeader.vue'
import CarteraTab from '@/components/facturacion/CarteraTab.vue'
import DocumentosTab from '@/components/facturacion/DocumentosTab.vue'
import FacturaDetalleDialog from '@/components/facturacion/FacturaDetalleDialog.vue'
import PosPanel from '@/components/facturacion/PosPanel.vue'
import PresupuestoDialog from '@/components/facturacion/PresupuestoDialog.vue'
import ReportesTab from '@/components/facturacion/ReportesTab.vue'
import TurnoPanel from '@/components/facturacion/TurnoPanel.vue'
import { useAuthStore } from '@/stores/auth'
import { useCajaStore } from '@/stores/caja'
import { cop } from '@/utils/dinero'

const auth = useAuthStore()
const caja = useCajaStore()
const route = useRoute()
const router = useRouter()

const operar = computed(() => auth.can.facturacionOperar)
const TABS = computed(() => (operar.value ? ['pos', 'documentos', 'cartera', 'caja', ...(auth.can.reportesVentas ? ['reportes'] : [])] : ['documentos']))
const inicial = String(route.query.tab ?? '')
const tab = ref(TABS.value.includes(inicial) ? inicial : operar.value ? 'pos' : 'documentos')

const presupuesto = ref<{ abierto: boolean; id: number | null }>({ abierto: false, id: null })
const factura = ref<{ abierto: boolean; id: number | null }>({ abierto: false, id: null })
const docs = ref<InstanceType<typeof DocumentosTab> | null>(null)

watch(tab, (t) => { void router.replace({ query: { ...route.query, tab: t } }) })
function irCaja() { tab.value = 'caja' }
function verPresupuesto(id: number | null) { presupuesto.value = { abierto: true, id } }
function verFactura(id: number) { factura.value = { abierto: true, id } }
function refrescarDocs() { void docs.value?.recargar() }

onMounted(() => {
  if (operar.value) void caja.refrescar()
  // Enlaces profundos desde la consulta: ?presupuesto=ID o ?factura=ID
  const p = Number(route.query.presupuesto)
  const f = Number(route.query.factura)
  if (p) { tab.value = 'documentos'; verPresupuesto(p) } else if (f) { tab.value = 'documentos'; verFactura(f) }
})
</script>

<template>
  <div>
    <PageHeader title="Caja y facturas" :subtitle="operar ? 'Turno de caja, venta de mostrador, presupuestos, facturas, cartera y reportes' : 'Presupuestos de sus consultas'">
      <v-chip v-if="operar && caja.cargado" :color="caja.abierto ? 'white' : 'warning'" variant="flat" :prepend-icon="caja.abierto ? 'mdi-lock-open-variant-outline' : 'mdi-lock-outline'"
        :text="caja.abierto ? `Caja abierta · efectivo esperado ${cop(caja.turno?.resumen.efectivoEsperado)}` : 'Caja cerrada'" />
    </PageHeader>
    <v-card border elevation="0">
      <v-tabs v-model="tab" color="primary" density="comfortable" show-arrows>
        <v-tab v-if="operar" value="pos" prepend-icon="mdi-cash-register">Venta de mostrador</v-tab>
        <v-tab value="documentos" prepend-icon="mdi-receipt-text-outline">{{ operar ? 'Presupuestos y facturas' : 'Presupuestos' }}</v-tab>
        <v-tab v-if="operar" value="cartera" prepend-icon="mdi-account-cash-outline">Cartera</v-tab>
        <v-tab v-if="operar" value="caja" prepend-icon="mdi-safe-square-outline">Turno de caja</v-tab>
        <v-tab v-if="auth.can.reportesVentas" value="reportes" prepend-icon="mdi-chart-bar">Reportes</v-tab>
      </v-tabs>
      <v-divider />
      <v-card-text>
        <v-tabs-window v-model="tab">
          <v-tabs-window-item v-if="operar" value="pos"><PosPanel v-if="tab === 'pos'" @abrir-caja="irCaja" @ver-factura="verFactura" /></v-tabs-window-item>
          <v-tabs-window-item value="documentos"><DocumentosTab v-if="tab === 'documentos'" ref="docs" @ver-presupuesto="verPresupuesto" @ver-factura="verFactura" @abrir-caja="irCaja" /></v-tabs-window-item>
          <v-tabs-window-item v-if="operar" value="cartera"><CarteraTab v-if="tab === 'cartera'" @ver-factura="verFactura" @abrir-caja="irCaja" /></v-tabs-window-item>
          <v-tabs-window-item v-if="operar" value="caja"><TurnoPanel v-if="tab === 'caja'" /></v-tabs-window-item>
          <v-tabs-window-item v-if="auth.can.reportesVentas" value="reportes"><ReportesTab v-if="tab === 'reportes'" /></v-tabs-window-item>
        </v-tabs-window>
      </v-card-text>
    </v-card>
    <PresupuestoDialog v-model="presupuesto.abierto" :presupuesto-id="presupuesto.id" @cambio="refrescarDocs" @facturada="verFactura" @abrir-caja="irCaja" />
    <FacturaDetalleDialog v-model="factura.abierto" :factura-id="factura.id" @cambio="refrescarDocs" @abrir-caja="irCaja" />
  </div>
</template>
