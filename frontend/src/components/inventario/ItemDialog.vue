<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { saveItem } from '@/api/inventario'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { required } from '@/utils/rules'
import { TIPOS_CON_STOCK, TIPOS_ITEM, type Categoria, type Item, type TipoItem } from '@/types/fase4'

const props = defineProps<{ modelValue: boolean; item?: Item | null; categorias: Categoria[] }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; guardado: [i: Item] }>()
const auth = useAuthStore()
const ui = useUiStore()

const vacio = () => ({
  codigo: '', nombre: '', tipo: 'PRODUCTO' as TipoItem, categoriaId: null as number | null,
  precioVenta: 0, costo: 0, impuestoPct: 0, stockMinimo: 0, controlaStock: true, esControlado: false,
  tieneVencimiento: true, requiereFormula: false, activo: true, unidadMedida: '', presentacion: '', marca: '',
  principioActivo: '', concentracion: '', registroIca: '', codigoBarras: '', descripcion: '',
})
const form = reactive(vacio())
const guardando = ref(false)
const error = ref('')
const formRef = ref<{ validate: () => Promise<{ valid: boolean }> } | null>(null)
const esNuevo = computed(() => !props.item)
const puedeStock = computed(() => TIPOS_CON_STOCK.includes(form.tipo))
const ivas = [0, 5, 19]

watch(() => props.modelValue, (open) => {
  if (!open) return
  error.value = ''
  Object.assign(form, vacio())
  const i = props.item
  if (i) {
    Object.assign(form, {
      codigo: i.codigo, nombre: i.nombre, tipo: i.tipo, categoriaId: i.categoriaId ?? null, precioVenta: i.precioVenta,
      costo: i.costo ?? 0, impuestoPct: i.impuestoPct, stockMinimo: i.stockMinimo, controlaStock: i.controlaStock,
      esControlado: i.esControlado, tieneVencimiento: i.tieneVencimiento, requiereFormula: i.requiereFormula, activo: i.activo,
      unidadMedida: i.unidadMedida ?? '', presentacion: i.presentacion ?? '', marca: i.marca ?? '',
      principioActivo: i.principioActivo ?? '', concentracion: i.concentracion ?? '', registroIca: i.registroIca ?? '',
      codigoBarras: i.codigoBarras ?? '', descripcion: i.descripcion ?? '',
    })
  }
})
watch(() => form.tipo, () => { if (!puedeStock.value) { form.controlaStock = false; form.esControlado = false } })
watch(() => form.esControlado, (v) => { if (v) form.controlaStock = true })

async function guardar() {
  const r = await formRef.value?.validate()
  if (!r?.valid) return
  guardando.value = true
  error.value = ''
  try {
    const t = (s: string) => (esNuevo.value ? s.trim() || undefined : s.trim())
    const body = {
      codigo: form.codigo.trim(), nombre: form.nombre.trim(), tipo: form.tipo, categoriaId: form.categoriaId ?? undefined,
      precioVenta: Number(form.precioVenta), impuestoPct: Number(form.impuestoPct), stockMinimo: Number(form.stockMinimo),
      controlaStock: form.controlaStock, esControlado: form.esControlado, tieneVencimiento: form.tieneVencimiento,
      requiereFormula: form.requiereFormula, activo: form.activo,
      ...(auth.can.inventarioCostos ? { costo: Number(form.costo) } : {}),
      unidadMedida: t(form.unidadMedida), presentacion: t(form.presentacion), marca: t(form.marca),
      principioActivo: t(form.principioActivo), concentracion: t(form.concentracion), registroIca: t(form.registroIca),
      codigoBarras: t(form.codigoBarras), descripcion: t(form.descripcion),
    }
    const guardado = await saveItem(props.item?.id ?? null, body)
    ui.showSuccess(esNuevo.value ? 'Ítem creado' : 'Ítem actualizado')
    emit('update:modelValue', false)
    emit('guardado', guardado)
  } catch (e) {
    error.value = errorText(e, 'No se pudo guardar el ítem')
    if (e instanceof ApiError && e.status === 409) error.value = `Conflicto: ${error.value}`
  } finally { guardando.value = false }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="860" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">{{ esNuevo ? 'Nuevo ítem' : `Editar ${item?.codigo}` }}</v-card-title>
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-form ref="formRef" @submit.prevent="guardar">
          <v-row dense>
            <v-col cols="12" sm="4"><v-text-field v-model="form.codigo" label="Código" :rules="[required()]" :readonly="!esNuevo" /></v-col>
            <v-col cols="12" sm="8"><v-text-field v-model="form.nombre" label="Nombre" :rules="[required()]" /></v-col>
            <v-col cols="12" sm="4"><v-select v-model="form.tipo" :items="TIPOS_ITEM" label="Tipo" /></v-col>
            <v-col cols="12" sm="4">
              <v-select v-model="form.categoriaId" :items="categorias" item-title="nombre" item-value="id" label="Categoría" clearable />
            </v-col>
            <v-col cols="6" sm="4">
              <v-select v-model="form.impuestoPct" :items="ivas" label="IVA (%)" />
            </v-col>
            <v-col cols="12" sm="4"><v-text-field v-model.number="form.precioVenta" type="number" min="0" step="100" label="Precio de venta (COP)" prefix="$" :rules="[required()]" /></v-col>
            <v-col v-if="auth.can.inventarioCostos" cols="6" sm="4"><v-text-field v-model.number="form.costo" type="number" min="0" step="100" label="Costo (solo administración)" prefix="$" /></v-col>
            <v-col v-if="puedeStock" cols="6" sm="4"><v-text-field v-model.number="form.stockMinimo" type="number" min="0" label="Stock mínimo" /></v-col>
          </v-row>
          <div class="d-flex flex-wrap ga-4 mt-2">
            <v-switch v-model="form.controlaStock" color="primary" density="compact" hide-details label="Controla stock" :disabled="!puedeStock || form.esControlado" />
            <v-switch v-model="form.esControlado" color="primary" density="compact" hide-details label="Medicamento controlado" :disabled="!puedeStock" />
            <v-switch v-model="form.tieneVencimiento" color="primary" density="compact" hide-details label="Tiene vencimiento" :disabled="!puedeStock" />
            <v-switch v-model="form.requiereFormula" color="primary" density="compact" hide-details label="Requiere fórmula" :disabled="!puedeStock" />
            <v-switch v-if="!esNuevo" v-model="form.activo" color="primary" density="compact" hide-details label="Activo" />
          </div>
          <v-alert v-if="!puedeStock" type="info" variant="tonal" density="compact" class="mt-3"
            text="Solo productos, medicamentos y alimentos controlan stock; los servicios y exámenes no tienen existencias." />
          <h3 class="vet-section-title mt-4">Detalle</h3>
          <v-row dense>
            <v-col cols="6" sm="3"><v-text-field v-model="form.unidadMedida" label="Unidad de medida" /></v-col>
            <v-col cols="6" sm="3"><v-text-field v-model="form.presentacion" label="Presentación" /></v-col>
            <v-col cols="6" sm="3"><v-text-field v-model="form.marca" label="Marca" /></v-col>
            <v-col cols="6" sm="3"><v-text-field v-model="form.codigoBarras" label="Código de barras" /></v-col>
            <v-col cols="12" sm="5"><v-text-field v-model="form.principioActivo" label="Principio activo" /></v-col>
            <v-col cols="6" sm="3"><v-text-field v-model="form.concentracion" label="Concentración" /></v-col>
            <v-col cols="6" sm="4"><v-text-field v-model="form.registroIca" label="Registro ICA / INVIMA" /></v-col>
            <v-col cols="12"><v-textarea v-model="form.descripcion" label="Descripción" rows="2" auto-grow /></v-col>
          </v-row>
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
