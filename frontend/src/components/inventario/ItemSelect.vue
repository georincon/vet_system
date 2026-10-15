<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { listItems } from '@/api/inventario'
import type { Item } from '@/types/fase4'

const props = withDefaults(defineProps<{
  modelValue: number | null | undefined
  label?: string
  /** Filtros fijos del catálogo, p. ej. solo con stock. */
  controlaStock?: boolean | null
  tipo?: string | null
  soloActivos?: boolean
  rules?: ((v: unknown) => boolean | string)[]
  disabled?: boolean
  density?: 'default' | 'comfortable' | 'compact'
  hideDetails?: boolean
}>(), { controlaStock: undefined, soloActivos: true, hideDetails: false })
const emit = defineEmits<{ 'update:modelValue': [v: number | null]; seleccion: [v: Item | null] }>()

interface Opcion { title: string; value: number; item: Item }
const items = ref<Opcion[]>([])
const search = ref('')
const loading = ref(false)
let timer: number | undefined

function opcion(i: Item): Opcion { return { title: `${i.codigo} · ${i.nombre}`, value: i.id, item: i } }
async function buscar(q: string) {
  loading.value = true
  try {
    const r = await listItems({ q, controlaStock: props.controlaStock ?? null, tipo: props.tipo, activo: props.soloActivos === false ? null : true, size: 20 })
    const actual = items.value.find((o) => o.value === props.modelValue)
    items.value = r.items.map(opcion)
    if (actual && !items.value.some((o) => o.value === actual.value)) items.value.unshift(actual)
  } finally { loading.value = false }
}
watch(search, (q) => {
  window.clearTimeout(timer)
  const sel = items.value.find((o) => o.value === props.modelValue)
  if (sel && q === sel.title) return
  timer = window.setTimeout(() => void buscar(q ?? ''), 300)
})
buscar('').catch(() => undefined)
onBeforeUnmount(() => window.clearTimeout(timer))

function onUpdate(v: number | null) {
  emit('update:modelValue', v ?? null)
  emit('seleccion', items.value.find((o) => o.value === v)?.item ?? null)
}
</script>

<template>
  <v-autocomplete :model-value="modelValue" v-model:search="search" :items="items" item-title="title" item-value="value"
    :label="label ?? 'Ítem'" :rules="rules" :loading="loading" :disabled="disabled" :density="density" :hide-details="hideDetails"
    no-filter clearable no-data-text="Escriba para buscar ítems" prepend-inner-icon="mdi-package-variant-closed" @update:model-value="onUpdate" />
</template>
