<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { listItems } from '@/api/inventario'
import { useAuthStore } from '@/stores/auth'
import { errorText } from '@/utils/errors'
import { cant, cop } from '@/utils/dinero'
import { TIPOS_ITEM, type Categoria, type Item } from '@/types/fase4'
import ItemDialog from './ItemDialog.vue'

defineProps<{ categorias: Categoria[] }>()
const auth = useAuthStore()
const items = ref<Item[]>([])
const loading = ref(false)
const error = ref('')
const dialog = ref(false)
const editando = ref<Item | null>(null)
const f = reactive({ q: '', tipo: null as string | null, categoriaId: null as number | null, bajoMinimo: false, activo: true as boolean | null, page: 1, pages: 0, total: 0 })
let timer: number | undefined

const headers = computed(() => [
  { title: 'Código', key: 'codigo', sortable: false },
  { title: 'Ítem', key: 'nombre', sortable: false },
  { title: 'Tipo', key: 'tipo', sortable: false },
  { title: 'Precio', key: 'precioVenta', sortable: false, align: 'end' as const },
  { title: 'IVA', key: 'impuestoPct', sortable: false, align: 'end' as const },
  ...(auth.can.inventarioCostos ? [{ title: 'Costo', key: 'costo', sortable: false, align: 'end' as const }] : []),
  { title: 'Stock', key: 'stockActual', sortable: false, align: 'end' as const },
  { title: 'Mínimo', key: 'stockMinimo', sortable: false, align: 'end' as const },
  { title: 'Estado', key: 'activo', sortable: false },
  ...(auth.can.inventarioWrite ? [{ title: '', key: 'acciones', sortable: false, align: 'end' as const }] : []),
])
const tipoLabel = (t: string) => TIPOS_ITEM.find((x) => x.value === t)?.title ?? t

async function cargar() {
  loading.value = true; error.value = ''
  try {
    const r = await listItems({
      q: f.q.trim(), tipo: f.tipo, categoriaId: f.categoriaId, bajoMinimo: f.bajoMinimo || null, activo: f.activo, page: f.page - 1, size: 15,
    })
    items.value = r.items; f.pages = r.totalPages; f.total = r.totalElements
  } catch (e) { error.value = errorText(e, 'No se pudo cargar el inventario') } finally { loading.value = false }
}
function filtrar() { f.page = 1; void cargar() }
function buscar() { window.clearTimeout(timer); timer = window.setTimeout(filtrar, 350) }
function nuevo() { editando.value = null; dialog.value = true }
function editar(i: Item) { editando.value = i; dialog.value = true }
defineExpose({ recargar: cargar, nuevo })
onMounted(cargar)
</script>

<template>
  <div>
    <div class="d-flex flex-wrap ga-2 mb-2 align-center">
      <v-text-field v-model="f.q" label="Buscar por código o nombre" prepend-inner-icon="mdi-magnify" clearable hide-details density="comfortable" class="flex-grow-1 vet-filtro-ancho" @update:model-value="buscar" />
      <v-select v-model="f.tipo" :items="TIPOS_ITEM" label="Tipo" clearable hide-details density="comfortable" class="vet-filtro" @update:model-value="filtrar" />
      <v-select v-model="f.categoriaId" :items="categorias" item-title="nombre" item-value="id" label="Categoría" clearable hide-details density="comfortable" class="vet-filtro" @update:model-value="filtrar" />
      <v-switch v-model="f.bajoMinimo" label="Bajo mínimo" color="primary" density="compact" hide-details @update:model-value="filtrar" />
      <v-switch v-model="f.activo" :true-value="true" :false-value="null" label="Solo activos" color="primary" density="compact" hide-details @update:model-value="filtrar" />
      <v-btn v-if="auth.can.inventarioWrite" color="primary" variant="flat" prepend-icon="mdi-plus" @click="nuevo">Nuevo ítem</v-btn>
    </div>
    <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-2" :text="error" />
    <v-data-table :headers="headers" :items="items" :loading="loading" :items-per-page="-1" hide-default-footer density="comfortable" no-data-text="Sin ítems para estos filtros">
      <template #item.nombre="{ item }">
        {{ item.nombre }}
        <v-chip v-if="item.esControlado" size="x-small" color="error" variant="tonal" class="ml-1" text="Controlado" />
        <div class="text-caption text-medium-emphasis">{{ item.categoria ?? 'Sin categoría' }}</div>
      </template>
      <template #item.tipo="{ item }">{{ tipoLabel(item.tipo) }}</template>
      <template #item.precioVenta="{ item }">{{ cop(item.precioVenta) }}</template>
      <template #item.impuestoPct="{ item }">{{ item.impuestoPct }}%</template>
      <template #item.costo="{ item }">{{ cop(item.costo) }}</template>
      <template #item.stockActual="{ item }">
        <template v-if="item.controlaStock">
          <v-chip size="small" variant="tonal" :color="item.bajoMinimo ? 'error' : 'success'" :text="cant(item.stockActual)" />
          <div v-if="item.stockTotal !== item.stockActual" class="text-caption text-medium-emphasis">total {{ cant(item.stockTotal) }}</div>
        </template>
        <span v-else class="text-medium-emphasis">—</span>
      </template>
      <template #item.stockMinimo="{ item }">{{ item.controlaStock ? cant(item.stockMinimo) : '—' }}</template>
      <template #item.activo="{ item }"><v-chip size="small" variant="tonal" :color="item.activo ? 'success' : 'grey'" :text="item.activo ? 'Activo' : 'Inactivo'" /></template>
      <template #item.acciones="{ item }"><v-btn icon="mdi-pencil-outline" size="small" variant="text" aria-label="Editar ítem" @click="editar(item)" /></template>
    </v-data-table>
    <div class="d-flex align-center justify-space-between mt-2">
      <span class="text-caption text-medium-emphasis">{{ f.total }} ítem(s)</span>
      <v-pagination v-if="f.pages > 1" v-model="f.page" :length="f.pages" :total-visible="7" @update:model-value="cargar" />
    </div>
    <ItemDialog v-model="dialog" :item="editando" :categorias="categorias" @guardado="cargar" />
  </div>
</template>
