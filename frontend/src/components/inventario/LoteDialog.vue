<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { crearLote } from '@/api/inventario'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { required } from '@/utils/rules'
import { hoy } from '@/utils/fecha'
import type { Bodega } from '@/types/fase4'
import ItemSelect from './ItemSelect.vue'

const props = defineProps<{ modelValue: boolean; bodegas: Bodega[] }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; guardado: [] }>()
const ui = useUiStore()
const form = reactive({ itemId: null as number | null, bodegaId: null as number | null, numeroLote: '', fechaVencimiento: '', cantidad: 1, costoUnitario: 0, motivo: 'Carga inicial' })
const guardando = ref(false)
const error = ref('')
const formRef = ref<{ validate: () => Promise<{ valid: boolean }> } | null>(null)

watch(() => props.modelValue, (o) => {
  if (!o) return
  error.value = ''
  Object.assign(form, { itemId: null, bodegaId: props.bodegas[0]?.id ?? null, numeroLote: '', fechaVencimiento: '', cantidad: 1, costoUnitario: 0, motivo: 'Carga inicial' })
})
async function guardar() {
  const r = await formRef.value?.validate()
  if (!r?.valid) return
  guardando.value = true; error.value = ''
  try {
    await crearLote({ ...form, fechaVencimiento: form.fechaVencimiento || undefined })
    ui.showSuccess('Lote cargado')
    emit('update:modelValue', false); emit('guardado')
  } catch (e) { error.value = errorText(e, 'No se pudo cargar el lote') } finally { guardando.value = false }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="600" @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">Cargar lote (existencia inicial)</v-card-title>
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-form ref="formRef" @submit.prevent="guardar">
          <ItemSelect v-model="form.itemId" label="Ítem" :controla-stock="true" :rules="[(v: unknown) => !!v || 'Seleccione un ítem']" />
          <v-row dense>
            <v-col cols="12" sm="6"><v-select v-model="form.bodegaId" :items="bodegas" item-title="nombre" item-value="id" label="Bodega" :rules="[(v: unknown) => !!v || 'Obligatoria']" /></v-col>
            <v-col cols="12" sm="6"><v-text-field v-model="form.numeroLote" label="Número de lote" :rules="[required()]" /></v-col>
            <v-col cols="12" sm="6"><v-text-field v-model="form.fechaVencimiento" type="date" label="Vencimiento" :min="hoy()" /></v-col>
            <v-col cols="6" sm="3"><v-text-field v-model.number="form.cantidad" type="number" min="0" step="any" label="Cantidad" /></v-col>
            <v-col cols="6" sm="3"><v-text-field v-model.number="form.costoUnitario" type="number" min="0" label="Costo unit." prefix="$" /></v-col>
          </v-row>
        </v-form>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn color="primary" variant="flat" :loading="guardando" @click="guardar">Cargar</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
