<script setup lang="ts">
import { ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import OrdenesTab from '@/components/compras/OrdenesTab.vue'
import ProveedoresTab from '@/components/compras/ProveedoresTab.vue'

const tab = ref('ordenes')
</script>

<template>
  <div>
    <PageHeader title="Compras" subtitle="Proveedores, órdenes de compra y recepción de mercancía con lote y vencimiento" />
    <v-card border elevation="0">
      <v-tabs v-model="tab" color="primary" density="comfortable">
        <v-tab value="ordenes" prepend-icon="mdi-clipboard-list-outline">Órdenes de compra</v-tab>
        <v-tab value="proveedores" prepend-icon="mdi-truck-outline">Proveedores</v-tab>
      </v-tabs>
      <v-divider />
      <v-card-text>
        <v-tabs-window v-model="tab">
          <v-tabs-window-item value="ordenes"><OrdenesTab /></v-tabs-window-item>
          <v-tabs-window-item value="proveedores"><ProveedoresTab v-if="tab === 'proveedores'" /></v-tabs-window-item>
        </v-tabs-window>
      </v-card-text>
    </v-card>
  </div>
</template>
