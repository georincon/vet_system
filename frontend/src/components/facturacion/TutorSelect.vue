<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { listTutores } from '@/api/tutores'
import type { Tutor } from '@/types'

const props = defineProps<{
  modelValue: number | null | undefined
  label?: string
  /** Texto cuando no hay tutor elegido (p. ej. Consumidor final). */
  placeholder?: string
  rules?: ((v: unknown) => boolean | string)[]
  density?: 'default' | 'comfortable' | 'compact'
  hideDetails?: boolean
  clearable?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [v: number | null]; seleccion: [v: Tutor | null] }>()

interface Opcion { title: string; value: number; tutor: Tutor }
const items = ref<Opcion[]>([])
const search = ref('')
const loading = ref(false)
let timer: number | undefined
const opcion = (t: Tutor): Opcion => ({ title: `${[t.nombres, t.apellidos].filter(Boolean).join(' ')} · ${t.tipoDocumento} ${t.numeroDocumento}`, value: t.id, tutor: t })

async function buscar(q: string) {
  loading.value = true
  try {
    const r = await listTutores(q || undefined, 0, 20)
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
  emit('seleccion', items.value.find((o) => o.value === v)?.tutor ?? null)
}
</script>

<template>
  <v-autocomplete :model-value="modelValue" v-model:search="search" :items="items" item-title="title" item-value="value"
    :label="label ?? 'Tutor'" :placeholder="placeholder" persistent-placeholder :rules="rules" :loading="loading" :density="density"
    :hide-details="hideDetails" :clearable="clearable ?? true" no-filter no-data-text="Escriba para buscar tutores"
    prepend-inner-icon="mdi-account-heart-outline" @update:model-value="onUpdate" />
</template>
