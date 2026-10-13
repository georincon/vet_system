<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { listPacientes } from '@/api/pacientes'
import type { PacienteResumen } from '@/types'

const props = defineProps<{
  modelValue: number | null | undefined
  inicial?: PacienteResumen | null
  label?: string
  rules?: ((v: unknown) => boolean | string)[]
  disabled?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: number | null]; seleccion: [value: PacienteResumen | null] }>()

interface Opcion { title: string; value: number; paciente: PacienteResumen }

const items = ref<Opcion[]>([])
const search = ref('')
const loading = ref(false)
let timer: number | undefined

function opcion(p: PacienteResumen): Opcion {
  return { title: `${p.nombre} (${p.codigo})${p.especie ? ' · ' + p.especie : ''}`, value: p.id, paciente: p }
}

function asegurar(p?: PacienteResumen | null) {
  if (p && !items.value.some((i) => i.value === p.id)) items.value = [opcion(p), ...items.value]
}

watch(() => props.inicial, (p) => asegurar(p), { immediate: true })

async function buscar(q: string) {
  loading.value = true
  try {
    const page = await listPacientes(q || undefined, 0, 20)
    const actual = items.value.find((i) => i.value === props.modelValue)
    items.value = page.items.map((p) => opcion({
      id: p.id, codigo: p.codigo, nombre: p.nombre, especie: p.especie.nombre,
      raza: p.raza?.nombre, sexo: p.sexo, fechaNacimiento: p.fechaNacimiento, activo: p.activo,
    }))
    if (actual) asegurar(actual.paciente)
  } finally {
    loading.value = false
  }
}

watch(search, (q) => {
  window.clearTimeout(timer)
  const seleccionado = items.value.find((i) => i.value === props.modelValue)
  if (seleccionado && q === seleccionado.title) return
  timer = window.setTimeout(() => void buscar(q), 300)
})
buscar('').catch(() => undefined)
onBeforeUnmount(() => window.clearTimeout(timer))

function onUpdate(v: number | null) {
  emit('update:modelValue', v ?? null)
  emit('seleccion', items.value.find((i) => i.value === v)?.paciente ?? null)
}
</script>

<template>
  <v-autocomplete
    :model-value="modelValue"
    v-model:search="search"
    :items="items"
    item-title="title"
    item-value="value"
    :label="label ?? 'Paciente'"
    :rules="rules"
    :loading="loading"
    :disabled="disabled"
    no-filter
    clearable
    no-data-text="Escriba para buscar pacientes"
    prepend-inner-icon="mdi-paw"
    @update:model-value="onUpdate"
  />
</template>
