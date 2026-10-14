<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { listAuditoria } from '@/api/dashboard'
import { listUsuarios } from '@/api/usuarios'
import PageHeader from '@/components/PageHeader.vue'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'
import type { AuditoriaEntry } from '@/types'

const ui = useUiStore()
const ENTIDADES = ['paciente', 'tutor', 'consulta', 'receta', 'cita', 'bloqueo_agenda', 'factura', 'pago']
const items = ref<AuditoriaEntry[]>([])
const loading = ref(false)
const filtros = reactive({ entidad: null as string | null, usuarioId: null as number | null, desde: '', hasta: '' })
const pag = reactive({ page: 1, totalPages: 0, total: 0 })
const usuarios = ref<{ title: string; value: number }[]>([])
const expandidos = ref<number[]>([])

const COLOR: Record<string, string> = { INSERT: 'success', UPDATE: 'info', DELETE: 'error' }

async function load() {
  loading.value = true
  try {
    const p = await listAuditoria({
      entidad: filtros.entidad ?? undefined, usuarioId: filtros.usuarioId, desde: filtros.desde, hasta: filtros.hasta,
      page: pag.page - 1, size: 20,
    })
    items.value = p.items
    pag.totalPages = p.totalPages
    pag.total = p.totalElements
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo cargar la auditoría'))
  } finally {
    loading.value = false
  }
}

function json(d: unknown): string {
  if (typeof d === 'string') {
    try { return JSON.stringify(JSON.parse(d), null, 2) } catch { return d }
  }
  return JSON.stringify(d ?? {}, null, 2)
}

function toggle(id: number) {
  expandidos.value = expandidos.value.includes(id) ? expandidos.value.filter((x) => x !== id) : [...expandidos.value, id]
}

watch(filtros, () => { pag.page = 1; void load() })
onMounted(async () => {
  void load()
  try {
    usuarios.value = (await listUsuarios(undefined, 0, 200)).items.map((u) => ({ title: `${u.nombre} (${u.username})`, value: u.id }))
  } catch { /* el filtro de usuario queda vacío */ }
})
</script>

<template>
  <div>
    <PageHeader title="Auditoría" subtitle="Registro de cambios por entidad y usuario" />
    <v-card border elevation="0" class="mb-3">
      <v-card-text class="d-flex align-center flex-wrap ga-3">
        <v-select v-model="filtros.entidad" :items="ENTIDADES" label="Entidad" clearable style="max-width: 200px" />
        <v-select v-model="filtros.usuarioId" :items="usuarios" label="Usuario" clearable style="max-width: 260px" />
        <v-text-field v-model="filtros.desde" type="date" label="Desde" clearable style="max-width: 170px" />
        <v-text-field v-model="filtros.hasta" type="date" label="Hasta" clearable style="max-width: 170px" />
        <v-btn variant="tonal" icon="mdi-refresh" title="Actualizar" :loading="loading" @click="load" />
      </v-card-text>
    </v-card>

    <v-card border elevation="0">
      <v-data-table
        :headers="[
          { title: 'Fecha', key: 'ocurridoEn' }, { title: 'Usuario', key: 'usuario' }, { title: 'Acción', key: 'accion' },
          { title: 'Entidad', key: 'entidad' }, { title: 'ID', key: 'entidadId' }, { title: 'IP', key: 'ip' },
          { title: 'Detalle', key: 'detalle', sortable: false, align: 'end' },
        ]"
        :items="items" :loading="loading" :items-per-page="-1" hide-default-footer no-data-text="Sin registros"
        item-value="id"
      >
        <template #item.ocurridoEn="{ item }">{{ etiquetaInstante(item.ocurridoEn) }}</template>
        <template #item.usuario="{ item }">{{ item.usuario ?? 'sistema' }}</template>
        <template #item.accion="{ item }"><v-chip size="small" variant="tonal" :color="COLOR[item.accion]" :text="item.accion" /></template>
        <template #item.ip="{ item }">{{ item.ip ?? '—' }}</template>
        <template #item.detalle="{ item }">
          <v-btn size="small" variant="text" :icon="expandidos.includes(item.id) ? 'mdi-chevron-up' : 'mdi-code-json'"
            title="Ver detalle" @click="toggle(item.id)" />
        </template>
        <template #item.entidadId="{ item }">{{ item.entidadId ?? '—' }}</template>
      </v-data-table>
      <v-expand-transition>
        <div v-if="expandidos.length" class="pa-4 border-t">
          <div v-for="item in items.filter((i) => expandidos.includes(i.id))" :key="item.id" class="mb-3">
            <div class="text-caption font-weight-bold">#{{ item.id }} · {{ item.entidad }} {{ item.entidadId }} · {{ item.accion }}</div>
            <pre class="vet-info-bar" style="overflow:auto; font-size: 12px; max-height: 260px">{{ json(item.detalle) }}</pre>
          </div>
        </div>
      </v-expand-transition>
      <div v-if="pag.totalPages > 1" class="pa-4 d-flex justify-center">
        <v-pagination v-model="pag.page" :length="pag.totalPages" :total-visible="7" @update:model-value="load" />
      </div>
    </v-card>
  </div>
</template>
