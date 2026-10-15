<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { listProveedores } from '@/api/inventario'
import { errorText } from '@/utils/errors'
import type { Proveedor } from '@/types/fase4'
import ProveedorDialog from './ProveedorDialog.vue'

const items = ref<Proveedor[]>([])
const loading = ref(false)
const error = ref('')
const dialog = ref(false)
const editando = ref<Proveedor | null>(null)
const f = reactive({ q: '', page: 1, pages: 0, total: 0 })
let timer: number | undefined
const headers = [
  { title: 'Razón social', key: 'razonSocial', sortable: false }, { title: 'Documento', key: 'numeroDocumento', sortable: false },
  { title: 'Contacto', key: 'contacto', sortable: false }, { title: 'Ciudad', key: 'ciudad', sortable: false },
  { title: 'Plazo', key: 'plazoPagoDias', sortable: false }, { title: 'Estado', key: 'activo', sortable: false },
  { title: '', key: 'acciones', sortable: false, align: 'end' as const },
]
async function cargar() {
  loading.value = true; error.value = ''
  try {
    const r = await listProveedores(f.q.trim() || undefined, f.page - 1, 15)
    items.value = r.items; f.pages = r.totalPages; f.total = r.totalElements
  } catch (e) { error.value = errorText(e, 'No se pudieron cargar los proveedores') } finally { loading.value = false }
}
function buscar() { window.clearTimeout(timer); timer = window.setTimeout(() => { f.page = 1; void cargar() }, 350) }
function abrir(p: Proveedor | null) { editando.value = p; dialog.value = true }
onMounted(cargar)
</script>

<template>
  <div>
    <div class="d-flex flex-wrap ga-2 mb-2 align-center">
      <v-text-field v-model="f.q" label="Buscar proveedor" prepend-inner-icon="mdi-magnify" clearable hide-details density="comfortable" class="flex-grow-1 vet-filtro-ancho" @update:model-value="buscar" />
      <v-btn color="primary" variant="flat" prepend-icon="mdi-plus" @click="abrir(null)">Nuevo proveedor</v-btn>
    </div>
    <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-2" :text="error" />
    <v-data-table :headers="headers" :items="items" :loading="loading" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin proveedores">
      <template #item.numeroDocumento="{ item }">{{ item.tipoDocumento }} {{ item.numeroDocumento }}</template>
      <template #item.contacto="{ item }">{{ item.contacto ?? '—' }}<div class="text-caption text-medium-emphasis">{{ item.telefono ?? item.email ?? '' }}</div></template>
      <template #item.ciudad="{ item }">{{ item.ciudad ?? '—' }}</template>
      <template #item.plazoPagoDias="{ item }">{{ item.plazoPagoDias ?? 0 }} d</template>
      <template #item.activo="{ item }"><v-chip size="small" variant="tonal" :color="item.activo ? 'success' : 'grey'" :text="item.activo ? 'Activo' : 'Inactivo'" /></template>
      <template #item.acciones="{ item }"><v-btn icon="mdi-pencil-outline" size="small" variant="text" aria-label="Editar proveedor" @click="abrir(item)" /></template>
    </v-data-table>
    <div class="d-flex align-center justify-space-between mt-2">
      <span class="text-caption text-medium-emphasis">{{ f.total }} proveedor(es)</span>
      <v-pagination v-if="f.pages > 1" v-model="f.page" :length="f.pages" :total-visible="7" @update:model-value="cargar" />
    </div>
    <ProveedorDialog v-model="dialog" :proveedor="editando" @guardado="cargar" />
  </div>
</template>
