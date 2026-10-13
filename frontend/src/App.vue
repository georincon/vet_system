<script setup lang="ts">
import { useUiStore } from '@/stores/ui'

const ui = useUiStore()
</script>

<template>
  <v-snackbar
    v-model="ui.snackbar.visible"
    :color="ui.snackbar.color"
    :timeout="ui.snackbar.timeout"
    location="top right"
    multi-line
  >
    {{ ui.snackbar.text }}
    <template #actions>
      <v-btn variant="text" size="small" @click="ui.snackbar.visible = false">Cerrar</v-btn>
    </template>
  </v-snackbar>

  <router-view />
</template>