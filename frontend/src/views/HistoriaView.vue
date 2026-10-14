<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { createAlergia, createPeso, deleteAlergia, getHistoria, getPaciente, listAlergias, listPesos } from '@/api/pacientes'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaFecha, hoy } from '@/utils/fecha'
import type { Alergia, HistoriaItem, Paciente, PesoRegistro, SeveridadAlergia, TipoAlergia, TipoHistoria } from '@/types'

const route = useRoute()
const auth = useAuthStore()
const ui = useUiStore()
const id = computed(() => Number(route.params.id))

const paciente = ref<Paciente | null>(null)
const alergias = ref<Alergia[]>([])
const pesos = ref<PesoRegistro[]>([])
const items = ref<HistoriaItem[]>([])
const tipo = ref<TipoHistoria | null>(null)
const page = ref(1)
const totalPages = ref(0)
const total = ref(0)
const loading = ref(false)

const META: Record<TipoHistoria, { icon: string; color: string; label: string }> = {
  CONSULTA: { icon: 'mdi-stethoscope', color: 'primary', label: 'Consulta' },
  VACUNA: { icon: 'mdi-needle', color: 'info', label: 'Vacuna' },
  PESO: { icon: 'mdi-scale-bathroom', color: 'secondary', label: 'Peso' },
  ALERGIA: { icon: 'mdi-alert-circle-outline', color: 'error', label: 'Alergia' },
}
const TIPOS = [{ title: 'Todo', value: null }, ...(Object.keys(META) as TipoHistoria[]).map((k) => ({ title: META[k].label + 's', value: k }))]

const activas = computed(() => alergias.value.filter((a) => a.activo))
const ultimoPeso = computed(() => pesos.value[0] ?? null)
const puedePeso = computed(() => auth.can.signos)
const puedeAlergia = computed(() => auth.can.clinicaWrite)

async function loadResumen() {
  try {
    const [p, a, w] = await Promise.all([getPaciente(id.value), listAlergias(id.value), listPesos(id.value)])
    paciente.value = p; alergias.value = a; pesos.value = w
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo cargar el paciente'))
  }
}

async function loadHistoria() {
  loading.value = true
  try {
    const r = await getHistoria(id.value, tipo.value ?? undefined, page.value - 1, 10)
    items.value = r.items
    totalPages.value = r.totalPages
    total.value = r.totalElements
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo cargar la historia'))
  } finally {
    loading.value = false
  }
}

// Registrar peso
const pesoDialog = ref(false)
const pesoForm = reactive({ pesoKg: null as number | null, fecha: hoy() })
const guardando = ref(false)
const errorForm = ref('')
async function guardarPeso() {
  if (!pesoForm.pesoKg || pesoForm.pesoKg <= 0) { errorForm.value = 'Ingrese un peso válido'; return }
  guardando.value = true
  try {
    await createPeso(id.value, Number(pesoForm.pesoKg), pesoForm.fecha)
    pesoDialog.value = false
    ui.showSuccess('Peso registrado')
    await Promise.all([loadResumen(), loadHistoria()])
  } catch (e) { errorForm.value = errorText(e, 'No se pudo registrar el peso') } finally { guardando.value = false }
}

// Alergias
const alergiaDialog = ref(false)
const alergiaForm = reactive({ descripcion: '', severidad: 'MEDIA' as SeveridadAlergia, tipo: 'MEDICAMENTO' as TipoAlergia, reaccion: '' })
const quitar = ref<Alergia | null>(null)
async function guardarAlergia() {
  if (!alergiaForm.descripcion.trim()) { errorForm.value = 'Ingrese la descripción'; return }
  guardando.value = true
  try {
    await createAlergia(id.value, { ...alergiaForm, descripcion: alergiaForm.descripcion.trim(), reaccion: alergiaForm.reaccion.trim() || undefined })
    alergiaDialog.value = false
    ui.showSuccess('Alergia registrada')
    await Promise.all([loadResumen(), loadHistoria()])
  } catch (e) { errorForm.value = errorText(e, 'No se pudo registrar la alergia') } finally { guardando.value = false }
}
async function eliminarAlergia() {
  if (!quitar.value) return
  try {
    await deleteAlergia(id.value, quitar.value.id)
    quitar.value = null
    await Promise.all([loadResumen(), loadHistoria()])
  } catch (e) { ui.showError(errorText(e, 'No se pudo eliminar la alergia')) }
}

watch([tipo], () => { page.value = 1; void loadHistoria() })
watch(id, () => { void loadResumen(); void loadHistoria() })
onMounted(() => { void loadResumen(); void loadHistoria() })
</script>

<template>
  <div>
    <PageHeader :title="paciente ? `Historia de ${paciente.nombre}` : 'Historia del paciente'"
      :subtitle="paciente ? `${paciente.codigo} · ${paciente.especie.nombre}${paciente.raza ? ' · ' + paciente.raza.nombre : ''}` : ''">
      <v-btn variant="flat" prepend-icon="mdi-arrow-left" :to="{ name: 'pacientes' }">Pacientes</v-btn>
    </PageHeader>

    <v-row v-if="paciente">
      <v-col cols="12" md="4">
        <v-card border elevation="0" class="h-100">
          <v-card-text>
            <h2 class="vet-section-title">Resumen</h2>
            <div class="mb-2"><strong>Sexo:</strong> {{ paciente.sexo === 'M' ? 'Macho' : 'Hembra' }}
              <template v-if="paciente.fechaNacimiento"> · Nac. {{ etiquetaFecha(paciente.fechaNacimiento) }}</template></div>
            <div class="mb-2 d-flex align-center ga-2">
              <strong>Último peso:</strong>
              <span>{{ ultimoPeso ? `${ultimoPeso.pesoKg} kg (${etiquetaFecha(ultimoPeso.fecha)})` : paciente.pesoActual != null ? paciente.pesoActual + ' kg' : '—' }}</span>
              <v-btn v-if="puedePeso" size="x-small" variant="tonal" icon="mdi-plus" title="Registrar peso"
                @click="pesoForm.pesoKg = null; pesoForm.fecha = hoy(); errorForm = ''; pesoDialog = true" />
            </div>
            <div class="d-flex align-center mb-1">
              <strong class="flex-grow-1">Alergias</strong>
              <v-btn v-if="puedeAlergia" size="x-small" variant="tonal" icon="mdi-plus" title="Agregar alergia"
                @click="Object.assign(alergiaForm, { descripcion: '', reaccion: '' }); errorForm = ''; alergiaDialog = true" />
            </div>
            <div class="d-flex flex-wrap ga-1">
              <v-chip v-for="a in activas" :key="a.id" size="small" variant="flat" prepend-icon="mdi-alert"
                :color="a.severidad === 'ALTA' ? 'error' : a.severidad === 'MEDIA' ? 'warning' : 'grey'"
                :closable="puedeAlergia" @click:close="quitar = a"
                :text="`${a.descripcion}${a.severidad ? ' (' + a.severidad.toLowerCase() + ')' : ''}`" />
              <span v-if="!activas.length" class="text-medium-emphasis">Sin alergias registradas</span>
            </div>
            <div v-if="paciente.alertas" class="mt-3"><v-icon icon="mdi-bell-alert-outline" size="small" /> {{ paciente.alertas }}</div>
          </v-card-text>
        </v-card>
      </v-col>
      <v-col cols="12" md="8">
        <v-card border elevation="0" class="h-100">
          <v-card-text>
            <div class="d-flex align-center flex-wrap ga-2 mb-2">
              <h2 class="vet-section-title flex-grow-1 mb-0">Línea de tiempo <span class="text-caption text-medium-emphasis">({{ total }})</span></h2>
              <v-btn-toggle v-model="tipo" density="compact" color="primary" variant="outlined" divided>
                <v-btn v-for="t in TIPOS" :key="String(t.value)" :value="t.value" size="small">{{ t.title }}</v-btn>
              </v-btn-toggle>
            </div>
            <v-progress-linear v-if="loading" indeterminate color="primary" />
            <v-timeline v-if="items.length" density="compact" side="end" class="mt-2">
              <v-timeline-item v-for="(it, i) in items" :key="it.tipo + it.referenciaId + i" size="small"
                :dot-color="META[it.tipo]?.color" :icon="META[it.tipo]?.icon">
                <div class="d-flex flex-wrap align-center ga-2">
                  <v-chip size="x-small" variant="tonal" :color="META[it.tipo]?.color" :text="META[it.tipo]?.label ?? it.tipo" />
                  <span class="text-caption text-medium-emphasis">{{ etiquetaFecha(it.fecha) }}</span>
                  <EstadoChip v-if="it.estado && it.tipo === 'CONSULTA'" :estado="it.estado" size="x-small" />
                </div>
                <div class="font-weight-bold">
                  <router-link v-if="it.tipo === 'CONSULTA'" :to="{ name: 'consulta', params: { id: it.referenciaId } }">{{ it.titulo }}</router-link>
                  <template v-else>{{ it.titulo }}</template>
                </div>
                <div v-if="it.detalle" class="text-body-2 text-medium-emphasis">{{ it.detalle }}</div>
              </v-timeline-item>
            </v-timeline>
            <div v-else-if="!loading" class="text-medium-emphasis py-6 text-center">Sin eventos en la historia.</div>
            <div v-if="totalPages > 1" class="d-flex justify-center mt-3">
              <v-pagination v-model="page" :length="totalPages" :total-visible="6" @update:model-value="loadHistoria" />
            </div>
          </v-card-text>
        </v-card>
      </v-col>
    </v-row>

    <v-dialog v-model="pesoDialog" max-width="400">
      <v-card>
        <v-card-title class="pt-4">Registrar peso</v-card-title>
        <v-card-text>
          <v-alert v-if="errorForm" type="error" variant="tonal" density="compact" class="mb-3" :text="errorForm" />
          <v-text-field v-model.number="pesoForm.pesoKg" label="Peso (kg)" type="number" step="0.01" class="mb-3" />
          <v-text-field v-model="pesoForm.fecha" label="Fecha" type="date" :max="hoy()" />
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer /><v-btn variant="text" @click="pesoDialog = false">Cancelar</v-btn>
          <v-btn color="primary" variant="flat" :loading="guardando" @click="guardarPeso">Guardar</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
    <v-dialog v-model="alergiaDialog" max-width="480">
      <v-card>
        <v-card-title class="pt-4">Agregar alergia</v-card-title>
        <v-card-text>
          <v-alert v-if="errorForm" type="error" variant="tonal" density="compact" class="mb-3" :text="errorForm" />
          <v-text-field v-model="alergiaForm.descripcion" label="Descripción (p. ej. Penicilina)" class="mb-3" />
          <v-row dense>
            <v-col cols="6"><v-select v-model="alergiaForm.severidad" label="Severidad" :items="['BAJA', 'MEDIA', 'ALTA']" /></v-col>
            <v-col cols="6"><v-select v-model="alergiaForm.tipo" label="Tipo" :items="['MEDICAMENTO', 'ALIMENTO', 'AMBIENTAL', 'VACUNA', 'OTRA']" /></v-col>
          </v-row>
          <v-text-field v-model="alergiaForm.reaccion" label="Reacción" class="mt-3" />
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer /><v-btn variant="text" @click="alergiaDialog = false">Cancelar</v-btn>
          <v-btn color="primary" variant="flat" :loading="guardando" @click="guardarAlergia">Guardar</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
    <ConfirmDialog :model-value="!!quitar" title="Quitar alergia" :message="`¿Dar de baja la alergia a ${quitar?.descripcion}?`" confirm-text="Quitar"
      @update:model-value="!$event && (quitar = null)" @confirm="eliminarAlergia" />
  </div>
</template>
