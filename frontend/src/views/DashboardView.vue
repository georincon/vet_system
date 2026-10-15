<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getResumen } from '@/api/dashboard'
import EstadoChip from '@/components/EstadoChip.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { cop } from '@/utils/dinero'
import { errorText } from '@/utils/errors'
import { etiquetaFecha, hhmm } from '@/utils/fecha'
import type { DashboardResumen } from '@/types'

const auth = useAuthStore()
const resumen = ref<DashboardResumen | null>(null)
const loading = ref(true)
const error = ref('')

interface Tarjeta { label: string; value: number | string | undefined; icon: string; color: string; to?: string }
const tarjetas = computed<Tarjeta[]>(() => {
  const r = resumen.value
  const clinica = auth.can.clinicaRead
  const base: Tarjeta[] = [
    { label: 'Citas hoy', value: r?.citasHoy, icon: 'mdi-calendar-today', color: 'primary', to: '/agenda' },
    { label: 'Citas pendientes', value: r?.citasPendientes, icon: 'mdi-calendar-clock-outline', color: 'warning', to: '/agenda' },
    { label: 'Pacientes activos', value: r?.pacientesActivos, icon: 'mdi-paw', color: 'primary', to: '/pacientes' },
    { label: 'Tutores activos', value: r?.tutoresActivos, icon: 'mdi-account-heart-outline', color: 'secondary', to: '/tutores' },
    { label: 'Consultas abiertas', value: r?.consultasAbiertas, icon: 'mdi-stethoscope', color: 'info', to: clinica ? '/consultas' : undefined },
    { label: 'Consultas del mes', value: r?.consultasMes, icon: 'mdi-clipboard-text-clock-outline', color: 'primary', to: clinica ? '/consultas' : undefined },
    { label: 'Vacunas por vencer (30 d)', value: r?.vacunasPorVencer, icon: 'mdi-needle', color: 'error', to: '/vacunacion/pendientes' },
  ]
  // Fase 4: ventas solo si el backend las entrega (null para VET/AUX); stock según rol.
  const f4: Tarjeta[] = []
  const facturacion = auth.can.facturacionOperar ? '/facturacion?tab=documentos' : undefined
  if (r?.ventasHoy !== null && r?.ventasHoy !== undefined) f4.push({ label: 'Ventas de hoy', value: cop(r.ventasHoy), icon: 'mdi-cash-multiple', color: 'success', to: facturacion })
  if (r?.ventasMes !== null && r?.ventasMes !== undefined) f4.push({ label: 'Ventas del mes', value: cop(r.ventasMes), icon: 'mdi-chart-line', color: 'success', to: auth.can.reportesVentas ? '/facturacion?tab=reportes' : facturacion })
  if (auth.can.inventarioRead && r?.stockBajo !== null && r?.stockBajo !== undefined) f4.push({ label: 'Ítems bajo el mínimo', value: r.stockBajo, icon: 'mdi-package-down', color: 'warning', to: '/inventario?tab=alertas' })
  if (auth.can.inventarioRead && r?.lotesPorVencer !== null && r?.lotesPorVencer !== undefined) f4.push({ label: 'Lotes por vencer (30 d)', value: r.lotesPorVencer, icon: 'mdi-timer-sand', color: 'error', to: '/inventario?tab=lotes' })
  return [...base, ...f4]
})

const atajos = computed(() => [
  { title: 'Agenda', icon: 'mdi-calendar-week', to: '/agenda', show: true },
  { title: 'Nueva cita', icon: 'mdi-calendar-plus', to: '/agenda', show: auth.can.agendaWrite },
  { title: 'Sala de espera', icon: 'mdi-seat', to: '/agenda', show: true },
  { title: 'Pacientes', icon: 'mdi-paw-outline', to: '/pacientes', show: true },
  { title: 'Tutores', icon: 'mdi-account-heart-outline', to: '/tutores', show: true },
  { title: 'Consultas', icon: 'mdi-stethoscope', to: '/consultas', show: auth.can.clinicaRead },
  { title: 'Vacunación pendiente', icon: 'mdi-needle', to: '/vacunacion/pendientes', show: true },
  { title: 'Comunicaciones', icon: 'mdi-email-fast-outline', to: '/comunicaciones', show: auth.can.comunicacionesRead },
  { title: 'Inventario', icon: 'mdi-package-variant-closed', to: '/inventario', show: auth.can.inventarioRead },
  { title: 'Caja y facturas', icon: 'mdi-cash-register', to: '/facturacion', show: auth.can.presupuestosRead },
  { title: 'Compras', icon: 'mdi-truck-delivery-outline', to: '/compras', show: auth.can.comprasRead },
  { title: 'Auditoría', icon: 'mdi-shield-search', to: '/auditoria', show: auth.isAdmin },
].filter((a) => a.show))

async function load() {
  loading.value = true
  error.value = ''
  try {
    resumen.value = await getResumen()
  } catch (e) {
    error.value = errorText(e, 'No se pudo cargar el resumen')
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="Dashboard" :subtitle="`Bienvenido, ${auth.usuario?.nombre || auth.usuario?.username}`">
      <v-btn variant="flat" prepend-icon="mdi-refresh" :loading="loading" @click="load">Actualizar</v-btn>
    </PageHeader>

    <v-alert v-if="error" type="error" variant="tonal" class="mb-4" :text="error" />

    <h2 class="vet-section-title">Resumen</h2>
    <v-row>
      <v-col v-for="t in tarjetas" :key="t.label" cols="6" md="4" lg="3">
        <v-card :to="t.to" class="vet-stat-card h-100" elevation="0" border>
          <v-card-text class="d-flex flex-column align-center py-6">
            <v-avatar :color="t.color" size="48" class="mb-3"><v-icon :icon="t.icon" color="white" size="26" /></v-avatar>
            <div class="text-h4 font-weight-bold">
              <v-progress-circular v-if="loading" indeterminate :size="24" :width="3" />
              <template v-else>{{ t.value ?? '—' }}</template>
            </div>
            <div class="text-body-2 text-medium-emphasis mt-2 text-center">{{ t.label }}</div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-row class="mt-4">
      <v-col cols="12" md="7">
        <h2 class="vet-section-title">Próximas citas</h2>
        <v-card border elevation="0">
          <v-list v-if="resumen?.proximasCitas.length" lines="two">
            <v-list-item v-for="c in resumen.proximasCitas" :key="c.id" to="/agenda" prepend-icon="mdi-calendar-clock"
              :title="`${etiquetaFecha(c.fecha)} · ${hhmm(c.horaInicio)} · ${c.paciente.nombre}`"
              :subtitle="`${c.veterinario.nombre}${c.motivo ? ' · ' + c.motivo : ''}`">
              <template #append><EstadoChip :estado="c.estado" /></template>
            </v-list-item>
          </v-list>
          <v-card-text v-else class="text-medium-emphasis">{{ loading ? 'Cargando…' : 'No hay citas próximas.' }}</v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" md="5">
        <h2 class="vet-section-title">Atajos</h2>
        <div class="d-flex flex-wrap ga-2">
          <v-btn v-for="a in atajos" :key="a.title" :to="a.to" variant="tonal" color="primary" :prepend-icon="a.icon">{{ a.title }}</v-btn>
        </div>
        <div class="vet-info-bar mt-6 text-body-2">
          <strong>Rol:</strong> {{ auth.usuario?.rol }}. Los módulos visibles dependen de su rol.
        </div>
      </v-col>
    </v-row>
  </div>
</template>
