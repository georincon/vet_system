<script setup lang="ts">
import { computed } from 'vue'
import { GRUPOS_FCI } from '@/utils/razas'
import type { Raza } from '@/types'

const TAMANOS: Record<string, string> = {
  MINI: 'Mini', PEQUENO: 'Pequeño', MEDIANO: 'Mediano', GRANDE: 'Grande', GIGANTE: 'Gigante',
}

const props = defineProps<{ modelValue: number | null | undefined; razas: Raza[]; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: number | null] }>()

function grupoLabel(r: Raza): string {
  return r.grupoFci ? `Grupo ${r.grupoFci} · ${GRUPOS_FCI[r.grupoFci] ?? ''}` : 'Sin grupo FCI'
}

function detalle(r: Raza): string {
  return [grupoLabel(r), r.origen, r.tamano ? TAMANOS[r.tamano] ?? r.tamano : null].filter(Boolean).join(' · ')
}

/** Ordenadas por grupo FCI y nombre; el texto de búsqueda incluye grupo y origen. */
const items = computed(() =>
  [...props.razas]
    .sort((a, b) => (a.grupoFci ?? 99) - (b.grupoFci ?? 99) || a.nombre.localeCompare(b.nombre, 'es'))
    .map((r) => ({ title: r.nombre, value: r.id, raw: r, search: `${r.nombre} ${detalle(r)}` })),
)

const selected = computed(() => props.razas.find((r) => r.id === props.modelValue) ?? null)

interface Opcion { title: string; value: number; raw: Raza; search: string }
function opc(item: unknown): Opcion {
  return item as Opcion
}

function filter(_value: unknown, query: string, item?: unknown): boolean {
  const q = query.toLowerCase()
  return !q || ((item as { raw?: Opcion } | undefined)?.raw?.search ?? '').toLowerCase().includes(q)
}
</script>

<template>
  <div>
    <v-autocomplete
      :model-value="modelValue"
      label="Raza (buscar por nombre, grupo u origen)"
      :items="items"
      item-title="title"
      item-value="value"
      :custom-filter="filter"
      :disabled="disabled"
      clearable
      no-data-text="Sin coincidencias"
      @update:model-value="emit('update:modelValue', ($event as number | null) ?? null)"
    >
      <template #item="{ props: itemProps, item }">
        <v-list-item v-bind="itemProps" :title="opc(item).title" :subtitle="detalle(opc(item).raw)">
          <template v-if="opc(item).raw.potencialmentePeligrosa" #append>
            <v-chip size="x-small" color="error" variant="tonal" prepend-icon="mdi-alert">Pot. peligrosa</v-chip>
          </template>
        </v-list-item>
      </template>
    </v-autocomplete>
    <v-alert
      v-if="selected?.potencialmentePeligrosa"
      type="warning"
      variant="tonal"
      density="compact"
      class="mt-2"
      icon="mdi-alert"
    >
      <strong>Raza potencialmente peligrosa</strong> (Ley 746 de 2002 y Ley 1801 de 2016). Informe al tutor
      sobre los requisitos legales de tenencia.
    </v-alert>
    <div v-else-if="selected" class="text-caption text-medium-emphasis mt-1">{{ detalle(selected) }}</div>
  </div>
</template>
