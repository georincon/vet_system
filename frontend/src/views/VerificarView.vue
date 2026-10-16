<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { verificarDocumento } from '@/api/formula'
import { errorText } from '@/utils/errors'
import { etiquetaFecha } from '@/utils/fecha'
import type { VerificacionDocumento } from '@/types/etapa'

const route = useRoute()
const router = useRouter()
const codigo = ref(typeof route.params.codigo === 'string' ? route.params.codigo : '')
const resultado = ref<VerificacionDocumento | null>(null)
const loading = ref(false)
const error = ref('')

const TIPOS: Record<string, string> = { FORMULA_MEDICA: 'Fórmula médica' }
const ESTADOS: Record<string, { color: string; icon: string; texto: string; ayuda: string }> = {
  VIGENTE: { color: 'success', icon: 'mdi-check-decagram', texto: 'Documento auténtico y vigente', ayuda: 'La fórmula fue emitida por la clínica y está vigente.' },
  CUMPLIDA: { color: 'info', icon: 'mdi-check-all', texto: 'Documento auténtico (cumplida)', ayuda: 'La fórmula es auténtica y ya fue cumplida.' },
  VENCIDA: { color: 'warning', icon: 'mdi-calendar-alert', texto: 'Documento auténtico pero vencido', ayuda: 'La fecha de validez ya pasó.' },
  ANULADA: { color: 'error', icon: 'mdi-cancel', texto: 'Documento anulado', ayuda: 'La clínica anuló esta fórmula: no debe dispensarse.' },
  BORRADOR: { color: 'warning', icon: 'mdi-pencil-outline', texto: 'Borrador: consulta aún abierta', ayuda: 'La fórmula todavía no está firmada por el veterinario.' },
}
const info = computed(() => ESTADOS[resultado.value?.estado ?? ''] ?? ESTADOS.VIGENTE)

async function verificar() {
  const c = codigo.value.trim()
  if (!c) { error.value = 'Escriba el código de verificación'; return }
  loading.value = true; error.value = ''; resultado.value = null
  try { resultado.value = await verificarDocumento(c) }
  catch (e) { error.value = errorText(e, 'No se pudo consultar la verificación') }
  finally { loading.value = false }
}
function buscar() {
  const c = codigo.value.trim()
  if (c && route.params.codigo !== c) void router.replace({ name: 'verificar', params: { codigo: c } })
  else void verificar()
}
watch(() => route.params.codigo, (c) => { if (typeof c === 'string' && c) { codigo.value = c; void verificar() } })
onMounted(() => { if (codigo.value) void verificar() })
</script>

<template>
  <v-app>
    <v-app-bar flat class="vet-appbar" height="64">
      <v-toolbar-title class="vet-brand"><img src="/patita.svg" alt="" /><span>Verificación de documentos</span></v-toolbar-title>
    </v-app-bar>
    <v-main>
      <v-container class="py-8" style="max-width: 640px">
        <v-card border elevation="0">
          <v-card-text>
            <h1 class="vet-section-title">Verificar una fórmula médica</h1>
            <p class="text-body-2 mb-4">Escriba el código impreso en la fórmula (con o sin guion). Solo se confirma la autenticidad: no se muestran datos del paciente, del tutor ni de los medicamentos.</p>
            <v-form @submit.prevent="buscar">
              <v-text-field v-model="codigo" label="Código de verificación" autofocus clearable prepend-inner-icon="mdi-shield-search" :error-messages="error" />
              <v-btn type="submit" color="primary" variant="flat" class="mt-3" :loading="loading" prepend-icon="mdi-check-decagram-outline">Verificar</v-btn>
            </v-form>
          </v-card-text>
        </v-card>

        <div aria-live="polite" class="mt-4">
          <v-alert v-if="resultado && !resultado.valido" type="error" variant="tonal" icon="mdi-close-octagon-outline" title="Código no válido">
            No existe un documento con ese código. Revise que lo haya escrito bien; si persiste, desconfíe del documento.
          </v-alert>
          <v-card v-else-if="resultado" border elevation="0">
            <v-card-text>
              <v-alert :type="info.color as 'success' | 'info' | 'warning' | 'error'" variant="tonal" :icon="info.icon" :title="info.texto" :text="info.ayuda" class="mb-4" />
              <dl class="d-grid ga-2">
                <div><strong>Documento:</strong> {{ TIPOS[resultado.tipoDocumento ?? ''] ?? resultado.tipoDocumento }} {{ resultado.numero }}</div>
                <div><strong>Fecha:</strong> {{ etiquetaFecha(resultado.fecha) }}</div>
                <div><strong>Clínica:</strong> {{ resultado.clinica }}</div>
                <div><strong>Veterinario:</strong> {{ resultado.veterinario }}</div>
                <div><strong>Tarjeta profesional:</strong> {{ resultado.tarjetaProfesional || 'No registrada' }}</div>
              </dl>
            </v-card-text>
          </v-card>
        </div>
        <div class="vet-footer-accent"></div>
      </v-container>
    </v-main>
  </v-app>
</template>
