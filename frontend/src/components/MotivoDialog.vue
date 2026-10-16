<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  modelValue: boolean
  title: string
  message?: string
  label?: string
  confirmText?: string
  minLength?: number
  color?: string
  loading?: boolean
  /** Si es false el motivo es opcional. */
  required?: boolean
}>(), { label: 'Motivo', confirmText: 'Aceptar', minLength: 5, color: 'error', loading: false, required: true })

const emit = defineEmits<{ 'update:modelValue': [value: boolean]; confirm: [motivo: string] }>()
const motivo = ref('')
const tocado = ref(false)
watch(() => props.modelValue, (v) => { if (v) { motivo.value = ''; tocado.value = false } })

function error(): string {
  if (!props.required && !motivo.value.trim()) return ''
  return motivo.value.trim().length >= props.minLength ? '' : `Escriba al menos ${props.minLength} caracteres`
}
function aceptar() {
  tocado.value = true
  if (error()) return
  emit('confirm', motivo.value.trim())
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="520" @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="text-h6 pt-4">{{ title }}</v-card-title>
      <v-card-text>
        <p v-if="message" class="mb-3">{{ message }}</p>
        <v-textarea v-model="motivo" :label="label" rows="2" auto-grow autofocus :error-messages="tocado ? error() : ''" />
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn :color="color" variant="flat" :loading="loading" @click="aceptar">{{ confirmText }}</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
