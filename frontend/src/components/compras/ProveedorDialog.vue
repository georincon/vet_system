<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { saveProveedor } from '@/api/inventario'
import { useUiStore } from '@/stores/ui'
import { email, required } from '@/utils/rules'
import { errorText } from '@/utils/errors'
import type { Proveedor } from '@/types/fase4'

const props = defineProps<{ modelValue: boolean; proveedor?: Proveedor | null }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; guardado: [p: Proveedor] }>()
const ui = useUiStore()
const vacio = () => ({ tipoDocumento: 'NIT', numeroDocumento: '', razonSocial: '', contacto: '', telefono: '', email: '', direccion: '', ciudad: '', plazoPagoDias: 30, notas: '', activo: true })
const form = reactive(vacio())
const guardando = ref(false)
const error = ref('')
const formRef = ref<{ validate: () => Promise<{ valid: boolean }> } | null>(null)

watch(() => props.modelValue, (o) => {
  if (!o) return
  error.value = ''
  Object.assign(form, vacio())
  const p = props.proveedor
  if (p) Object.assign(form, {
    tipoDocumento: p.tipoDocumento, numeroDocumento: p.numeroDocumento, razonSocial: p.razonSocial, contacto: p.contacto ?? '',
    telefono: p.telefono ?? '', email: p.email ?? '', direccion: p.direccion ?? '', ciudad: p.ciudad ?? '',
    plazoPagoDias: p.plazoPagoDias ?? 0, notas: p.notas ?? '', activo: p.activo,
  })
})
async function guardar() {
  const r = await formRef.value?.validate()
  if (!r?.valid) return
  guardando.value = true; error.value = ''
  try {
    const nuevo = !props.proveedor
    const t = (s: string) => (nuevo ? s.trim() || undefined : s.trim())
    const g = await saveProveedor(props.proveedor?.id ?? null, {
      tipoDocumento: form.tipoDocumento, numeroDocumento: form.numeroDocumento.trim(), razonSocial: form.razonSocial.trim(),
      contacto: t(form.contacto), telefono: t(form.telefono), email: t(form.email), direccion: t(form.direccion), ciudad: t(form.ciudad),
      plazoPagoDias: Number(form.plazoPagoDias) || 0, notas: t(form.notas), activo: form.activo,
    })
    ui.showSuccess(nuevo ? 'Proveedor creado' : 'Proveedor actualizado')
    emit('update:modelValue', false); emit('guardado', g)
  } catch (e) { error.value = errorText(e, 'No se pudo guardar el proveedor') } finally { guardando.value = false }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="720" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">{{ proveedor ? 'Editar proveedor' : 'Nuevo proveedor' }}</v-card-title>
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-form ref="formRef" @submit.prevent="guardar">
          <v-row dense>
            <v-col cols="12" sm="3"><v-select v-model="form.tipoDocumento" :items="['NIT', 'CC', 'CE', 'PASAPORTE', 'OTRO']" label="Tipo de documento" /></v-col>
            <v-col cols="12" sm="4"><v-text-field v-model="form.numeroDocumento" label="Número" :rules="[required()]" /></v-col>
            <v-col cols="12" sm="5"><v-text-field v-model="form.razonSocial" label="Razón social" :rules="[required()]" /></v-col>
            <v-col cols="12" sm="4"><v-text-field v-model="form.contacto" label="Contacto" /></v-col>
            <v-col cols="12" sm="4"><v-text-field v-model="form.telefono" label="Teléfono" /></v-col>
            <v-col cols="12" sm="4"><v-text-field v-model="form.email" label="Correo" :rules="[email()]" /></v-col>
            <v-col cols="12" sm="5"><v-text-field v-model="form.direccion" label="Dirección" /></v-col>
            <v-col cols="6" sm="4"><v-text-field v-model="form.ciudad" label="Ciudad" /></v-col>
            <v-col cols="6" sm="3"><v-text-field v-model.number="form.plazoPagoDias" type="number" min="0" label="Plazo de pago (días)" /></v-col>
            <v-col cols="12"><v-textarea v-model="form.notas" label="Notas" rows="2" auto-grow /></v-col>
          </v-row>
          <v-switch v-if="proveedor" v-model="form.activo" color="primary" density="compact" hide-details label="Activo" />
        </v-form>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn color="primary" variant="flat" :loading="guardando" @click="guardar">Guardar</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
