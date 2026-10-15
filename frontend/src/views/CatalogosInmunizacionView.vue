<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { listEspecies } from '@/api/catalogos'
import {
  listAntiparasitarios, listEsquemas, listVacunas, saveAntiparasitario, saveEsquema, saveVacuna,
} from '@/api/inmunizacion'
import PageHeader from '@/components/PageHeader.vue'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import type { Especie } from '@/types'
import {
  CLASIFICACIONES, TIPOS_ANTIPARASITARIO, type Antiparasitario, type Esquema, type EsquemaDosis, type Vacuna,
} from '@/types/fase3'

const ui = useUiStore()
const tab = ref('vacunas')
const especies = ref<Especie[]>([])
const vacunas = ref<Vacuna[]>([])
const antis = ref<Antiparasitario[]>([])
const esquemas = ref<Esquema[]>([])
const loading = ref(false)
const especieOpts = computed(() => [{ title: 'Genérica (todas)', value: null as number | null }, ...especies.value.map((e) => ({ title: e.nombre, value: e.id }))])
const vacunaOpts = computed(() => vacunas.value.map((v) => ({ title: `${v.nombre} (${v.codigo})`, value: v.id })))

async function cargar() {
  loading.value = true
  try {
    const [v, a, e] = await Promise.all([listVacunas(), listAntiparasitarios(), listEsquemas()])
    vacunas.value = v; antis.value = a; esquemas.value = e
  } catch (e) { ui.showError(errorText(e, 'No se pudieron cargar los catálogos')) } finally { loading.value = false }
}

const dialog = ref<'vacuna' | 'anti' | 'esquema' | null>(null)
const editId = ref<number | null>(null)
const guardando = ref(false)
const error = ref('')
const nullIfEmpty = (v: unknown) => (v === '' || v === undefined ? null : v)

const vf = reactive({ codigo: '', nombre: '', especieId: null as number | null, periodoEficaciaDias: 365, clasificacion: 'NUCLEO' as Vacuna['clasificacion'],
  enfermedadesObjetivo: '', fabricante: '', viaAdministracion: '', dosisMl: null as number | null, itemId: null as number | null, obligatoriaLey: false, activo: true })
const af = reactive({ codigo: '', nombre: '', tipo: 'INTERNA' as Antiparasitario['tipo'], principioActivo: '', especieId: null as number | null, intervaloDias: 90, itemId: null as number | null, activo: true })
const ef = reactive({ codigo: '', nombre: '', vacunaId: null as number | null, especieId: null as number | null, fuente: '', notas: '', activo: true, dosis: [] as EsquemaDosis[] })

function abrirVacuna(v?: Vacuna) {
  editId.value = v?.id ?? null; error.value = ''
  Object.assign(vf, v ? { codigo: v.codigo, nombre: v.nombre, especieId: v.especieId ?? null, periodoEficaciaDias: v.periodoEficaciaDias, clasificacion: v.clasificacion ?? 'NUCLEO',
    enfermedadesObjetivo: v.enfermedadesObjetivo ?? '', fabricante: v.fabricante ?? '', viaAdministracion: v.viaAdministracion ?? '', dosisMl: v.dosisMl ?? null, itemId: v.itemId ?? null,
    obligatoriaLey: !!v.obligatoriaLey, activo: v.activo }
    : { codigo: '', nombre: '', especieId: null, periodoEficaciaDias: 365, clasificacion: 'NUCLEO', enfermedadesObjetivo: '', fabricante: '', viaAdministracion: '', dosisMl: null, itemId: null, obligatoriaLey: false, activo: true })
  dialog.value = 'vacuna'
}
function abrirAnti(a?: Antiparasitario) {
  editId.value = a?.id ?? null; error.value = ''
  Object.assign(af, a ? { codigo: a.codigo, nombre: a.nombre, tipo: a.tipo, principioActivo: a.principioActivo ?? '', especieId: a.especieId ?? null, intervaloDias: a.intervaloDias, itemId: a.itemId ?? null, activo: a.activo }
    : { codigo: '', nombre: '', tipo: 'INTERNA', principioActivo: '', especieId: null, intervaloDias: 90, itemId: null, activo: true })
  dialog.value = 'anti'
}
function abrirEsquema(e?: Esquema) {
  editId.value = e?.id ?? null; error.value = ''
  Object.assign(ef, e ? { codigo: e.codigo, nombre: e.nombre, vacunaId: e.vacunaId, especieId: e.especieId ?? null, fuente: e.fuente ?? '', notas: e.notas ?? '', activo: e.activo, dosis: e.dosis.map((d) => ({ ...d })) }
    : { codigo: '', nombre: '', vacunaId: null, especieId: null, fuente: '', notas: '', activo: true, dosis: [{ orden: 1, nombreDosis: 'Dosis 1', edadMinimaSemanas: null, intervaloDiasPrevia: null, esRefuerzo: false, repetirCadaDias: null }] })
  dialog.value = 'esquema'
}
function agregarDosis() {
  const orden = ef.dosis.reduce((m, d) => Math.max(m, d.orden), 0) + 1
  ef.dosis.push({ orden, nombreDosis: `Dosis ${orden}`, edadMinimaSemanas: null, intervaloDiasPrevia: null, esRefuerzo: false, repetirCadaDias: null })
}

async function guardar() {
  guardando.value = true; error.value = ''
  try {
    if (dialog.value === 'vacuna') {
      if (!vf.codigo.trim() || !vf.nombre.trim() || !vf.periodoEficaciaDias) throw new Error('Código, nombre y periodo de eficacia son obligatorios')
      await saveVacuna(editId.value, { ...vf, codigo: vf.codigo.trim(), nombre: vf.nombre.trim(), dosisMl: vf.dosisMl || null, itemId: vf.itemId || null })
    } else if (dialog.value === 'anti') {
      if (!af.codigo.trim() || !af.nombre.trim() || !af.intervaloDias) throw new Error('Código, nombre e intervalo son obligatorios')
      await saveAntiparasitario(editId.value, { ...af, codigo: af.codigo.trim(), nombre: af.nombre.trim(), itemId: af.itemId || null })
    } else if (dialog.value === 'esquema') {
      if (!ef.codigo.trim() || !ef.nombre.trim() || !ef.vacunaId) throw new Error('Código, nombre y vacuna son obligatorios')
      if (!ef.dosis.length) throw new Error('Agregue al menos una dosis')
      await saveEsquema(editId.value, { ...ef, codigo: ef.codigo.trim(), nombre: ef.nombre.trim(), vacunaId: ef.vacunaId,
        dosis: ef.dosis.map((d) => ({ ...d, edadMinimaSemanas: nullIfEmpty(d.edadMinimaSemanas) as number | null, intervaloDiasPrevia: nullIfEmpty(d.intervaloDiasPrevia) as number | null, repetirCadaDias: nullIfEmpty(d.repetirCadaDias) as number | null })) })
    }
    ui.showSuccess('Guardado')
    dialog.value = null
    await cargar()
  } catch (e) { error.value = errorText(e, 'No se pudo guardar') } finally { guardando.value = false }
}

onMounted(async () => {
  void cargar()
  try { especies.value = await listEspecies() } catch { /* opcional */ }
})
</script>

<template>
  <div>
    <PageHeader title="Catálogos de inmunización" subtitle="Vacunas, antiparasitarios y esquemas de dosis">
      <v-btn variant="flat" prepend-icon="mdi-plus" @click="tab === 'vacunas' ? abrirVacuna() : tab === 'anti' ? abrirAnti() : abrirEsquema()">
        {{ tab === 'vacunas' ? 'Nueva vacuna' : tab === 'anti' ? 'Nuevo antiparasitario' : 'Nuevo esquema' }}
      </v-btn>
    </PageHeader>

    <v-card border elevation="0">
      <v-tabs v-model="tab" color="primary">
        <v-tab value="vacunas" prepend-icon="mdi-needle">Vacunas ({{ vacunas.length }})</v-tab>
        <v-tab value="anti" prepend-icon="mdi-bug-outline">Antiparasitarios ({{ antis.length }})</v-tab>
        <v-tab value="esquemas" prepend-icon="mdi-calendar-multiple-check">Esquemas ({{ esquemas.length }})</v-tab>
      </v-tabs>
      <v-divider />
      <v-card-text>
        <v-tabs-window v-model="tab">
          <v-tabs-window-item value="vacunas">
            <v-data-table :items="vacunas" :loading="loading" :items-per-page="15" density="comfortable" no-data-text="Sin vacunas" items-per-page-text="Filas"
              :headers="[{ title: 'Código', key: 'codigo' }, { title: 'Nombre', key: 'nombre' }, { title: 'Especie', key: 'especie' }, { title: 'Clasificación', key: 'clasificacion' },
                { title: 'Eficacia (d)', key: 'periodoEficaciaDias' }, { title: 'Estado', key: 'activo' }, { title: '', key: 'a', sortable: false, align: 'end' }]">
              <template #item.especie="{ item }">{{ item.especie ?? 'Genérica' }}</template>
              <template #item.clasificacion="{ item }">
                <v-chip size="small" variant="tonal" :text="CLASIFICACIONES.find((c) => c.value === item.clasificacion)?.title ?? '—'" />
                <v-chip v-if="item.obligatoriaLey" size="x-small" color="error" variant="tonal" class="ml-1" text="Ley" />
              </template>
              <template #item.activo="{ item }"><v-chip size="small" variant="tonal" :color="item.activo ? 'success' : 'grey'" :text="item.activo ? 'Activa' : 'Inactiva'" /></template>
              <template #item.a="{ item }"><v-btn size="small" variant="text" icon="mdi-pencil" title="Editar" @click="abrirVacuna(item)" /></template>
            </v-data-table>
          </v-tabs-window-item>
          <v-tabs-window-item value="anti">
            <v-data-table :items="antis" :loading="loading" :items-per-page="15" density="comfortable" no-data-text="Sin antiparasitarios" items-per-page-text="Filas"
              :headers="[{ title: 'Código', key: 'codigo' }, { title: 'Nombre', key: 'nombre' }, { title: 'Tipo', key: 'tipo' }, { title: 'Especie', key: 'especie' },
                { title: 'Intervalo (d)', key: 'intervaloDias' }, { title: 'Estado', key: 'activo' }, { title: '', key: 'a', sortable: false, align: 'end' }]">
              <template #item.especie="{ item }">{{ item.especie ?? 'Genérico' }}</template>
              <template #item.activo="{ item }"><v-chip size="small" variant="tonal" :color="item.activo ? 'success' : 'grey'" :text="item.activo ? 'Activo' : 'Inactivo'" /></template>
              <template #item.a="{ item }"><v-btn size="small" variant="text" icon="mdi-pencil" title="Editar" @click="abrirAnti(item)" /></template>
            </v-data-table>
          </v-tabs-window-item>
          <v-tabs-window-item value="esquemas">
            <v-data-table :items="esquemas" :loading="loading" :items-per-page="15" density="comfortable" no-data-text="Sin esquemas" items-per-page-text="Filas"
              :headers="[{ title: 'Código', key: 'codigo' }, { title: 'Esquema', key: 'nombre' }, { title: 'Vacuna', key: 'vacuna' }, { title: 'Especie', key: 'especie' },
                { title: 'Dosis', key: 'dosis', sortable: false }, { title: 'Estado', key: 'activo' }, { title: '', key: 'a', sortable: false, align: 'end' }]">
              <template #item.especie="{ item }">{{ item.especie ?? 'Genérico' }}</template>
              <template #item.dosis="{ item }">{{ item.dosis.length }} dosis</template>
              <template #item.activo="{ item }"><v-chip size="small" variant="tonal" :color="item.activo ? 'success' : 'grey'" :text="item.activo ? 'Activo' : 'Inactivo'" /></template>
              <template #item.a="{ item }"><v-btn size="small" variant="text" icon="mdi-pencil" title="Editar" @click="abrirEsquema(item)" /></template>
            </v-data-table>
          </v-tabs-window-item>
        </v-tabs-window>
      </v-card-text>
    </v-card>

    <v-dialog :model-value="dialog === 'vacuna'" max-width="720" scrollable @update:model-value="!$event && (dialog = null)">
      <v-card>
        <v-card-title class="pt-4">{{ editId ? 'Editar vacuna' : 'Nueva vacuna' }}</v-card-title>
        <v-card-text>
          <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
          <v-row dense>
            <v-col cols="12" sm="4"><v-text-field v-model="vf.codigo" label="Código" /></v-col>
            <v-col cols="12" sm="8"><v-text-field v-model="vf.nombre" label="Nombre" /></v-col>
            <v-col cols="12" sm="4"><v-select v-model="vf.especieId" :items="especieOpts" label="Especie" /></v-col>
            <v-col cols="6" sm="4"><v-select v-model="vf.clasificacion" :items="CLASIFICACIONES" label="Clasificación" /></v-col>
            <v-col cols="6" sm="4"><v-text-field v-model.number="vf.periodoEficaciaDias" type="number" min="1" label="Periodo de eficacia (días)" /></v-col>
            <v-col cols="12" sm="6"><v-text-field v-model="vf.enfermedadesObjetivo" label="Enfermedades objetivo" /></v-col>
            <v-col cols="12" sm="6"><v-text-field v-model="vf.fabricante" label="Fabricante" /></v-col>
            <v-col cols="4"><v-text-field v-model="vf.viaAdministracion" label="Vía" /></v-col>
            <v-col cols="4"><v-text-field v-model.number="vf.dosisMl" type="number" step="0.1" label="Dosis (ml)" /></v-col>
            <v-col cols="4"><v-text-field v-model.number="vf.itemId" type="number" label="ID ítem inventario" /></v-col>
          </v-row>
          <v-switch v-model="vf.obligatoriaLey" label="Obligatoria por ley" color="primary" hide-details density="compact" />
          <v-switch v-model="vf.activo" label="Activa" color="primary" hide-details density="compact" />
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer /><v-btn variant="text" @click="dialog = null">Cancelar</v-btn><v-btn color="primary" variant="flat" :loading="guardando" @click="guardar">Guardar</v-btn></v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog :model-value="dialog === 'anti'" max-width="640" scrollable @update:model-value="!$event && (dialog = null)">
      <v-card>
        <v-card-title class="pt-4">{{ editId ? 'Editar antiparasitario' : 'Nuevo antiparasitario' }}</v-card-title>
        <v-card-text>
          <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
          <v-row dense>
            <v-col cols="12" sm="4"><v-text-field v-model="af.codigo" label="Código" /></v-col>
            <v-col cols="12" sm="8"><v-text-field v-model="af.nombre" label="Nombre" /></v-col>
            <v-col cols="6" sm="4"><v-select v-model="af.tipo" :items="TIPOS_ANTIPARASITARIO" label="Tipo" /></v-col>
            <v-col cols="6" sm="4"><v-select v-model="af.especieId" :items="especieOpts" label="Especie" /></v-col>
            <v-col cols="6" sm="4"><v-text-field v-model.number="af.intervaloDias" type="number" min="1" label="Intervalo (días)" /></v-col>
            <v-col cols="12" sm="8"><v-text-field v-model="af.principioActivo" label="Principio activo" /></v-col>
            <v-col cols="12" sm="4"><v-text-field v-model.number="af.itemId" type="number" label="ID ítem inventario" /></v-col>
          </v-row>
          <v-switch v-model="af.activo" label="Activo" color="primary" hide-details density="compact" />
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer /><v-btn variant="text" @click="dialog = null">Cancelar</v-btn><v-btn color="primary" variant="flat" :loading="guardando" @click="guardar">Guardar</v-btn></v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog :model-value="dialog === 'esquema'" max-width="1000" scrollable @update:model-value="!$event && (dialog = null)">
      <v-card>
        <v-card-title class="pt-4">{{ editId ? 'Editar esquema' : 'Nuevo esquema' }}</v-card-title>
        <v-card-text>
          <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
          <v-row dense>
            <v-col cols="12" sm="3"><v-text-field v-model="ef.codigo" label="Código" /></v-col>
            <v-col cols="12" sm="5"><v-text-field v-model="ef.nombre" label="Nombre" /></v-col>
            <v-col cols="6" sm="4"><v-select v-model="ef.especieId" :items="especieOpts" label="Especie" /></v-col>
            <v-col cols="12" sm="6"><v-select v-model="ef.vacunaId" :items="vacunaOpts" label="Vacuna" /></v-col>
            <v-col cols="12" sm="6"><v-text-field v-model="ef.fuente" label="Fuente (p. ej. WSAVA 2024)" /></v-col>
          </v-row>
          <h3 class="vet-section-title mt-2">Calendario de dosis</h3>
          <div class="text-caption text-medium-emphasis mb-2">Al guardar se reemplaza el calendario por orden; no se puede eliminar una dosis ya usada por aplicaciones.</div>
          <v-table density="compact">
            <thead><tr><th>Orden</th><th>Nombre</th><th>Edad mín. (sem)</th><th>Intervalo previo (d)</th><th>Refuerzo</th><th>Repetir cada (d)</th><th /></tr></thead>
            <tbody>
              <tr v-for="(d, i) in ef.dosis" :key="i">
                <td><v-text-field v-model.number="d.orden" type="number" min="1" density="compact" hide-details variant="underlined" /></td>
                <td><v-text-field v-model="d.nombreDosis" density="compact" hide-details variant="underlined" /></td>
                <td><v-text-field v-model.number="d.edadMinimaSemanas" type="number" min="0" density="compact" hide-details variant="underlined" /></td>
                <td><v-text-field v-model.number="d.intervaloDiasPrevia" type="number" min="0" density="compact" hide-details variant="underlined" /></td>
                <td><v-checkbox v-model="d.esRefuerzo" density="compact" hide-details /></td>
                <td><v-text-field v-model.number="d.repetirCadaDias" type="number" min="1" density="compact" hide-details variant="underlined" /></td>
                <td><v-btn size="x-small" variant="text" color="error" icon="mdi-delete-outline" title="Quitar dosis" @click="ef.dosis.splice(i, 1)" /></td>
              </tr>
            </tbody>
          </v-table>
          <v-btn class="mt-2" size="small" variant="tonal" prepend-icon="mdi-plus" @click="agregarDosis">Agregar dosis</v-btn>
          <v-textarea v-model="ef.notas" label="Notas" rows="2" auto-grow class="mt-3" />
          <v-switch v-model="ef.activo" label="Activo" color="primary" hide-details density="compact" />
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer /><v-btn variant="text" @click="dialog = null">Cancelar</v-btn><v-btn color="primary" variant="flat" :loading="guardando" @click="guardar">Guardar</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
