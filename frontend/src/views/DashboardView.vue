<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { listUsuarios } from '@/api/usuarios'

const auth = useAuthStore()

const usuariosCount = ref<number | null>(null)
const loading = ref(true)
const loadingMessage = ref('')

async function loadCounts() {
  loading.value = true
  try {
    const page = await listUsuarios(undefined, 0, 1)
    usuariosCount.value = page.totalElements
  } catch {
    usuariosCount.value = null
    loadingMessage.value = 'No se pudo consultar el estado de la API'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadCounts()
})
</script>

<template>
  <div>
    <div class="vet-header">
      <div>
        <h1>Dashboard</h1>
        <p>Bienvenido, {{ auth.usuario?.nombre || auth.usuario?.username }}</p>
      </div>
      <v-btn variant="flat" prepend-icon="mdi-refresh" :loading="loading" @click="loadCounts">
        Actualizar
      </v-btn>
    </div>

    <h2 class="vet-section-title">Resumen</h2>
    <v-row>
      <v-col cols="12" sm="6" md="4" lg="3">
        <v-card :to="'/usuarios'" class="vet-stat-card h-100" elevation="0" border>
          <v-card-text class="d-flex flex-column align-center py-8">
            <v-avatar color="primary" size="56" class="mb-4">
              <v-icon icon="mdi-account-group-outline" color="white" size="28" />
            </v-avatar>
            <div class="text-h3 font-weight-bold">
              <v-progress-circular v-if="loading" indeterminate :size="28" :width="3" />
              <template v-else>{{ usuariosCount ?? '—' }}</template>
            </div>
            <div class="text-body-1 text-medium-emphasis mt-4">Usuarios del sistema</div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <div class="vet-info-bar mt-8">
      <strong>Estado del sistema:</strong>
      {{ loadingMessage || 'Autenticación, usuarios, tutores, pacientes, citas y consultas habilitados.' }}
    </div>
  </div>
</template>
