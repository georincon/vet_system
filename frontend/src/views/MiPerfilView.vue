<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getPerfilProfesional, updatePerfilProfesional } from '@/api/formula'
import PageHeader from '@/components/PageHeader.vue'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import type { PerfilProfesional } from '@/types/etapa'

const ui = useUiStore()
const perfil = ref<PerfilProfesional | null>(null)
const numero = ref('')
const especialidad = ref('')
const loading = ref(true)
const saving = ref(false)

async function load() {
  try {
    perfil.value = await getPerfilProfesional()
    numero.value = perfil.value.numeroProfesional ?? ''
    especialidad.value = perfil.value.especialidad ?? ''
  } catch (e) { ui.showError(errorText(e, 'No se pudo cargar el perfil')) } finally { loading.value = false }
}
async function guardar() {
  saving.value = true
  try {
    // "" limpia el campo en el backend.
    perfil.value = await updatePerfilProfesional({ numeroProfesional: numero.value.trim(), especialidad: especialidad.value.trim() })
    ui.showSuccess('Perfil profesional actualizado')
  } catch (e) { ui.showError(errorText(e, 'No se pudo guardar el perfil')) } finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div>
    <PageHeader title="Mi perfil profesional" subtitle="Aparece en la firma de fórmulas e informes" />
    <v-progress-linear v-if="loading" indeterminate color="primary" />
    <v-card v-else-if="perfil" border elevation="0" style="max-width: 640px">
      <v-card-text>
        <h2 class="vet-section-title">{{ perfil.nombre }}</h2>
        <v-form @submit.prevent="guardar">
          <v-text-field v-model="numero" label="Tarjeta profesional (T.P.)" maxlength="50" class="mb-3" hint="Ej.: TP-12345. Sin ella, la fórmula dice «T.P. no registrada»." persistent-hint />
          <v-text-field v-model="especialidad" label="Especialidad" maxlength="120" class="mb-4" />
          <v-btn type="submit" color="primary" variant="flat" :loading="saving" prepend-icon="mdi-content-save-outline">Guardar</v-btn>
        </v-form>
      </v-card-text>
    </v-card>
  </div>
</template>
