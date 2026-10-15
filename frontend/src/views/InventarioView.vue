<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { listBodegas, listCategorias } from '@/api/inventario'
import PageHeader from '@/components/PageHeader.vue'
import AlertasTab from '@/components/inventario/AlertasTab.vue'
import ControladosTab from '@/components/inventario/ControladosTab.vue'
import ItemsTab from '@/components/inventario/ItemsTab.vue'
import KardexTab from '@/components/inventario/KardexTab.vue'
import LotesTab from '@/components/inventario/LotesTab.vue'
import { useAuthStore } from '@/stores/auth'
import type { Bodega, Categoria } from '@/types/fase4'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const TABS = ['items', 'lotes', 'kardex', 'alertas', 'controlados']
const tab = ref(TABS.includes(String(route.query.tab)) ? String(route.query.tab) : 'items')
const categorias = ref<Categoria[]>([])
const bodegas = ref<Bodega[]>([])
const puedeControlados = computed(() => auth.can.controladosRead)

watch(tab, (t) => { void router.replace({ query: { ...route.query, tab: t } }) })
watch(() => route.query.tab, (t) => { if (typeof t === 'string' && TABS.includes(t) && t !== tab.value) tab.value = t })
onMounted(async () => {
  try { [categorias.value, bodegas.value] = await Promise.all([listCategorias(), listBodegas()]) } catch { /* los filtros quedan vacíos */ }
})
</script>

<template>
  <div>
    <PageHeader title="Inventario" subtitle="Ítems, lotes y vencimientos, kardex, alertas y libro de controlados" />
    <v-alert v-if="!auth.can.inventarioWrite" type="info" variant="tonal" density="compact" class="mb-4"
      :text="auth.can.inventarioUso ? 'Consulta de existencias. Puede registrar el uso de un lote; los ajustes y el catálogo son de administración.' : 'Consulta de existencias de solo lectura.'" />
    <v-card border elevation="0">
      <v-tabs v-model="tab" color="primary" density="comfortable" show-arrows>
        <v-tab value="items" prepend-icon="mdi-package-variant-closed">Ítems</v-tab>
        <v-tab value="lotes" prepend-icon="mdi-barcode">Lotes y vencimientos</v-tab>
        <v-tab value="kardex" prepend-icon="mdi-swap-vertical">Kardex</v-tab>
        <v-tab value="alertas" prepend-icon="mdi-alert-outline">Alertas</v-tab>
        <v-tab v-if="puedeControlados" value="controlados" prepend-icon="mdi-book-lock-outline">Libro de controlados</v-tab>
      </v-tabs>
      <v-divider />
      <v-card-text>
        <v-tabs-window v-model="tab">
          <v-tabs-window-item value="items"><ItemsTab :categorias="categorias" /></v-tabs-window-item>
          <v-tabs-window-item value="lotes"><LotesTab v-if="tab === 'lotes'" :bodegas="bodegas" /></v-tabs-window-item>
          <v-tabs-window-item value="kardex"><KardexTab v-if="tab === 'kardex'" /></v-tabs-window-item>
          <v-tabs-window-item value="alertas"><AlertasTab v-if="tab === 'alertas'" /></v-tabs-window-item>
          <v-tabs-window-item v-if="puedeControlados" value="controlados"><ControladosTab v-if="tab === 'controlados'" /></v-tabs-window-item>
        </v-tabs-window>
      </v-card-text>
    </v-card>
  </div>
</template>
