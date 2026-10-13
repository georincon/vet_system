<script setup lang="ts">
withDefaults(
  defineProps<{
    modelValue: boolean
    title: string
    message: string
    confirmText?: string
    cancelText?: string
    color?: string
    loading?: boolean
  }>(),
  {
    confirmText: 'Aceptar',
    cancelText: 'Cancelar',
    color: 'error',
    loading: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: []
}>()
</script>

<template>
  <v-dialog
    :model-value="modelValue"
    max-width="480"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <v-card>
      <v-card-title class="text-h6 pt-4">{{ title }}</v-card-title>
      <v-card-text class="text-body-1">{{ message }}</v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">
          {{ cancelText }}
        </v-btn>
        <v-btn
          :color="color"
          variant="flat"
          :loading="loading"
          @click="emit('confirm')"
        >
          {{ confirmText }}
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>