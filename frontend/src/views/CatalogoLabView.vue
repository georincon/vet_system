<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { getExamen, listExamenes, saveExamen } from '@/api/laboratorio'
import PageHeader from '@/components/PageHeader.vue'
import ExamenDialog from '@/components/laboratorio/ExamenDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useCatalogosStore } from '@/stores/catalogos'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { TIPOS_EXAMEN, type ExamenCatalogo, type ExamenRequest, type TipoExamen } from '@/types/etapa'

const auth = useAuthStore()
const ui = useUiStore()
const catalogos = useCatalogosStore()

const items = ref<ExamenCatalogo[]>([])
const loading = ref(false)
const tipo = ref<TipoExamen | null>(null)
const q = ref('')
const soloActivos = ref(true)
const detalle = ref<ExamenCatalogo | null>(null)
const detalleDialog = ref(false)
const edicion = ref(false)
const editando = ref<ExamenCatalogo | null>(null)

const puedeEscribir = computed(() => auth.can.catalogoLabWrite)
const especie = (id: number) => catalogos.especies.find((e) => e.id === id)?.nombre ?? `#${id}`

async function cargar() {
  loading.value = true
  try { items.value = await listExamenes({ tipo: tipo.value, q: q.value || undefined, soloActivos: soloActivos.value }) }
  catch (e) { ui.showError(errorText(e, 'No se pudo cargar el catálogo')) } finally { loading.value = false }
}
watch([tipo, soloActivos], cargar)
let t: number | undefined
watch(q, () => { window.clearTimeout(t); t = window.setTimeout(cargar, 300) })

async function ver(e: ExamenCatalogo) {
  try { detalle.value = await getExamen(e.id); detalleDialog.value = true }
  catch (err) { ui.showError(errorText(err, 'No se pudo cargar el detalle')) }
}
async function editar(e: ExamenCatalogo | null) {
  try { editando.value = e ? await getExamen(e.id) : null; edicion.value = true }
  catch (err) { ui.showError(errorText(err, 'No se pudo cargar el examen')) }
}
async function alternar(e: ExamenCatalogo) {
  try {
    const d = await getExamen(e.id)
    const body: ExamenRequest = { codigo: d.codigo, nombre: d.nombre, tipo: d.tipo, activo: !d.activo }
    await saveExamen(e.id, body)
    ui.showSuccess(d.activo ? 'Examen desactivado' : 'Examen activado')
    await cargar()
  } catch (err) { ui.showError(errorText(err, 'No se pudo cambiar el estado')) }
}

onMounted(() => { void catalogos.ensure(); void cargar() })
const headers = [
  { title: 'Código', key: 'codigo' }, { title: 'Examen', key: 'nombre' }, { title: 'Tipo', key: 'tipo' },
  { title: 'Muestra', key: 'muestra' }, { title: 'Codificación', key: 'loincEstado', sortable: false }, { title: 'Estado', key: 'activo' },
  { title: '', key: 'acciones', sortable: false, align: 'end' as const },
]
</script>

<template>
  <div>
    <PageHeader title="Catálogo de exámenes" subtitle="Exámenes, paneles, parámetros y rangos de referencia">
      <v-btn variant="flat" prepend-icon="mdi-arrow-left" :to="{ name: 'laboratorio' }">Bandeja</v-btn>
      <v-btn v-if="puedeEscribir" variant="flat" prepend-icon="mdi-plus" @click="editar(null)">Nuevo examen</v-btn>
    </PageHeader>
    <v-alert type="warning" variant="tonal" density="compact" class="mb-4" icon="mdi-alert-outline"
      text="Los rangos de referencia, los valores críticos y los códigos LOINC son orientativos y están POR VALIDAR con el laboratorio o el veterinario responsable (dependen del equipo y el método). Los LOINC son de medicina humana." />
    <v-card border elevation="0" class="mb-3">
      <v-card-text class="d-flex flex-wrap ga-3 align-center">
        <v-select v-model="tipo" :items="TIPOS_EXAMEN" item-title="title" item-value="value" label="Tipo" clearable class="vet-filtro" />
        <v-text-field v-model="q" label="Buscar" prepend-inner-icon="mdi-magnify" clearable class="vet-filtro vet-filtro-ancho" />
        <v-switch v-model="soloActivos" label="Solo activos" color="primary" hide-details />
      </v-card-text>
    </v-card>
    <v-data-table :headers="headers" :items="items" :loading="loading" :items-per-page="25" density="comfortable" item-value="id" hover @click:row="(_: Event, r: { item: ExamenCatalogo }) => ver(r.item)">
      <template #item.nombre="{ item }">{{ item.nombre }}<v-chip v-if="item.esPanel" size="x-small" class="ml-2" variant="tonal" text="Panel" /></template>
      <template #item.tipo="{ item }">{{ TIPOS_EXAMEN.find((x) => x.value === item.tipo)?.title ?? item.tipo }}</template>
      <template #item.loincEstado="{ item }">
        <v-chip v-if="item.loincEstado === 'POR_VALIDAR'" size="small" color="warning" variant="tonal" prepend-icon="mdi-alert-outline" text="LOINC por validar" />
        <v-chip v-else-if="item.loincEstado === 'VALIDADO'" size="small" color="success" variant="tonal" text="LOINC validado" />
      </template>
      <template #item.activo="{ item }"><v-chip size="small" variant="tonal" :color="item.activo ? 'success' : 'grey'" :text="item.activo ? 'Activo' : 'Inactivo'" /></template>
      <template #item.acciones="{ item }">
        <v-btn size="small" variant="text" icon="mdi-eye-outline" :aria-label="`Ver ${item.nombre}`" @click.stop="ver(item)" />
        <template v-if="puedeEscribir">
          <v-btn size="small" variant="text" icon="mdi-pencil" :aria-label="`Editar ${item.nombre}`" @click.stop="editar(item)" />
          <v-btn size="small" variant="text" :icon="item.activo ? 'mdi-eye-off-outline' : 'mdi-eye-check-outline'" :aria-label="item.activo ? 'Desactivar' : 'Activar'" @click.stop="alternar(item)" />
        </template>
      </template>
    </v-data-table>

    <v-dialog v-model="detalleDialog" max-width="1000" scrollable>
      <v-card v-if="detalle">
        <v-card-title class="pt-4">{{ detalle.nombre }} <span class="text-caption">({{ detalle.codigo }})</span></v-card-title>
        <v-card-text>
          <div class="vet-info-bar mb-3">
            {{ detalle.muestra ?? 'Sin muestra' }}<template v-if="detalle.requiereAyuno"> · requiere ayuno</template>
            <template v-if="detalle.tiempoEntregaHoras"> · entrega {{ detalle.tiempoEntregaHoras }} h</template>
            <template v-if="detalle.codigoLoinc"> · LOINC {{ detalle.codigoLoinc }} ({{ detalle.loincEstado === 'VALIDADO' ? 'validado' : 'por validar' }})</template>
          </div>
          <div v-for="p in detalle.parametros ?? []" :key="p.codigo" class="mb-4">
            <div class="d-flex flex-wrap align-center ga-2">
              <strong>{{ p.nombre }}</strong> <span class="text-caption">({{ p.codigo }}) · {{ p.tipoValor }} · {{ p.unidad ?? 'sin unidad' }}<template v-if="p.unidadUcum"> · UCUM {{ p.unidadUcum }}</template></span>
              <v-chip v-if="p.codigoLoinc" size="x-small" :color="p.codificacionEstado === 'VALIDADO' ? 'success' : 'warning'" variant="tonal" :text="`LOINC ${p.codigoLoinc}${p.codificacionEstado === 'VALIDADO' ? '' : ' (por validar)'}`" />
              <v-chip v-if="p.activo === false" size="x-small" text="Inactivo" />
            </div>
            <div v-if="p.opciones" class="text-caption">Opciones: {{ p.opciones }} · normal: {{ p.valorNormalTexto ?? '—' }}</div>
            <v-table v-if="p.rangos?.length" density="compact" class="mt-1">
              <thead><tr><th>Especie</th><th>Sexo</th><th>Edad (meses)</th><th>Rango</th><th>Crítico</th><th>Fuente</th></tr></thead>
              <tbody>
                <tr v-for="(r, i) in p.rangos" :key="i">
                  <td>{{ especie(r.especieId) }}</td><td>{{ r.sexo ?? 'Ambos' }}</td>
                  <td>{{ r.edadMinMeses ?? '0' }} – {{ r.edadMaxMeses ?? '∞' }}</td>
                  <td>{{ r.valorMin ?? '—' }} – {{ r.valorMax ?? '—' }}</td>
                  <td>{{ r.criticoMin ?? '—' }} / {{ r.criticoMax ?? '—' }}</td>
                  <td class="text-wrap">{{ r.fuente ?? '' }}</td>
                </tr>
              </tbody>
            </v-table>
          </div>
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer /><v-btn variant="text" @click="detalleDialog = false">Cerrar</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
    <ExamenDialog v-model="edicion" :examen="editando" @guardado="cargar" />
  </div>
</template>
