<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { onMounted } from 'vue'
import { listEspecies, listRazas } from '@/api/catalogos'
import { createPaciente, listPacientes, updatePaciente } from '@/api/pacientes'
import { listTutores } from '@/api/tutores'
import PageHeader from '@/components/PageHeader.vue'
import RazaAutocomplete from '@/components/RazaAutocomplete.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { formRules } from '@/utils/rules'
import type { Rule } from '@/utils/rules'
import {
  SEXOS,
  SEXO_LABELS,
  type Especie,
  type Paciente,
  type PacienteRequest,
  type Raza,
  type Sexo,
  type Tutor,
} from '@/types'

interface FormHandle {
  validate: () => Promise<{ valid: boolean }>
  reset: () => void
}

const ui = useUiStore()
const auth = useAuthStore()

const items = ref<Paciente[]>([])
const loading = ref(false)
const params = reactive({ q: '', page: 0, size: 10, totalElements: 0, totalPages: 0 })

const dialog = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormHandle | null>(null)
const form = reactive<PacienteRequest>({
  nombre: '',
  especieId: undefined as unknown as number,
  razaId: null,
  sexo: 'F',
  estadoReproductivo: '',
  color: '',
  fechaNacimiento: null as unknown as string,
  microchip: '',
  pesoActual: null,
  esterilizado: false,
  fallecido: false,
  alertas: '',
  notas: '',
  tutorIds: [],
  tutorPrincipalId: null,
})

const especies = ref<Especie[]>([])
const razas = ref<Raza[]>([])
const tutores = ref<Tutor[]>([])
const tutorOptionsLoading = ref(false)

const nombreRules: Rule[] = [formRules.required('Ingrese el nombre del paciente')]
const especieRules: Rule[] = [formRules.requiredSelect('Seleccione la especie')]
const sexoRules: Rule[] = [formRules.requiredSelect('Seleccione el sexo')]

const sexoOptions = SEXOS.map((sexo) => ({ title: SEXO_LABELS[sexo], value: sexo }))
const especieOptions = computed(() => especies.value.map((especie) => ({ title: especie.nombre, value: especie.id })))
const tutorOptions = computed(() => tutores.value.map((tutor) => ({
  title: tutorNombre(tutor),
  value: tutor.id,
})))
const tutorPrincipalOptions = computed(() => {
  const seleccionados = new Set(form.tutorIds)
  return tutorOptions.value.filter((option) => seleccionados.has(option.value))
})

function tutorNombre(tutor: Tutor): string {
  return `${tutor.nombres} ${tutor.apellidos ?? ''}`.trim()
}

function sexoLabel(sexo: Sexo): string {
  return SEXO_LABELS[sexo] ?? sexo
}

function pesoFormateado(paciente: Paciente): string {
  return paciente.pesoActual != null ? `${Number(paciente.pesoActual)} kg` : '—'
}

async function load() {
  loading.value = true
  try {
    const page = await listPacientes(params.q || undefined, params.page, params.size)
    items.value = page.items
    params.totalElements = page.totalElements
    params.totalPages = page.totalPages
  } catch (error) {
    ui.showError(errorText(error, 'No se pudieron cargar los pacientes'))
  } finally {
    loading.value = false
  }
}

async function loadEspecies() {
  try {
    especies.value = await listEspecies()
  } catch (error) {
    ui.showError(errorText(error, 'No se pudieron cargar las especies'))
  }
}

async function loadRazas(especieId: number | undefined | null) {
  if (especieId == null) {
    razas.value = []
    form.razaId = null
    return
  }
  try {
    razas.value = await listRazas(especieId)
  } catch (error) {
    ui.showError(errorText(error, 'No se pudieron cargar las razas'))
    razas.value = []
  }
  if (!razas.value.some((raza) => raza.id === form.razaId)) {
    form.razaId = null
  }
}

async function loadTutores() {
  tutorOptionsLoading.value = true
  try {
    const page = await listTutores(undefined, 0, 200)
    tutores.value = page.items
  } catch (error) {
    ui.showError(errorText(error, 'No se pudieron cargar los tutores'))
  } finally {
    tutorOptionsLoading.value = false
  }
}

watch(() => form.especieId, (value) => {
  void loadRazas(value)
})

watch(
  () => form.tutorIds,
  (tutorIds) => {
    const seleccionados = new Set(tutorIds)
    if (form.tutorPrincipalId != null && !seleccionados.has(form.tutorPrincipalId)) {
      form.tutorPrincipalId = tutorIds.length > 0 ? tutorIds[0] : null
    }
  },
)

function resetForm() {
  form.nombre = ''
  form.especieId = undefined as unknown as number
  form.razaId = null
  form.sexo = 'F'
  form.estadoReproductivo = ''
  form.color = ''
  form.fechaNacimiento = null as unknown as string
  form.microchip = ''
  form.pesoActual = null
  form.esterilizado = false
  form.fallecido = false
  form.alertas = ''
  form.notas = ''
  form.tutorIds = []
  form.tutorPrincipalId = null
  razas.value = []
}

function openCreate() {
  editingId.value = null
  resetForm()
  formRef.value?.reset()
  dialog.value = true
}

function openEdit(paciente: Paciente) {
  editingId.value = paciente.id
  form.nombre = paciente.nombre
  form.especieId = paciente.especie.id
  form.sexo = paciente.sexo
  form.estadoReproductivo = paciente.estadoReproductivo ?? ''
  form.color = paciente.color ?? ''
  form.fechaNacimiento = paciente.fechaNacimiento ?? (null as unknown as string)
  form.microchip = paciente.microchip ?? ''
  form.pesoActual = paciente.pesoActual ?? null
  form.esterilizado = paciente.esterilizado
  form.fallecido = paciente.fallecido
  form.alertas = paciente.alertas ?? ''
  form.notas = paciente.notas ?? ''
  form.tutorIds = paciente.tutores.map((tutor) => tutor.id)
  const principal = paciente.tutores.find((tutor) => tutor.esPrincipal)
  form.tutorPrincipalId = principal?.id ?? (form.tutorIds[0] ?? null)
  const razaIdToRestore = paciente.raza?.id ?? null
  dialog.value = true
  void (async () => {
    await loadRazas(paciente.especie.id)
    if (razaIdToRestore != null && razas.value.some((raza) => raza.id === razaIdToRestore)) {
      form.razaId = razaIdToRestore
    } else {
      form.razaId = null
    }
  })()
}

function buildPayload(): PacienteRequest {
  return {
    ...form,
    razaId: form.razaId ?? undefined,
    fechaNacimiento: form.fechaNacimiento || undefined,
    pesoActual: form.pesoActual ?? undefined,
    tutorIds: [...form.tutorIds],
    tutorPrincipalId: form.tutorPrincipalId ?? undefined,
  } as PacienteRequest
}

async function save() {
  const result = await formRef.value?.validate()
  if (!result?.valid) return
  if (form.tutorIds.length > 0 && form.tutorPrincipalId == null) {
    ui.showError('Seleccione el tutor principal')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (editingId.value === null) {
      await createPaciente(payload)
      ui.showSuccess('Paciente creado correctamente')
    } else {
      await updatePaciente(editingId.value, payload)
      ui.showSuccess('Paciente actualizado correctamente')
    }
    dialog.value = false
    params.page = editingId.value === null ? 0 : params.page
    await load()
  } catch (error) {
    ui.showError(errorText(error, 'No se pudo guardar el paciente'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void load()
  void loadEspecies()
  void loadTutores()
})
</script>

<template>
  <div>
    <PageHeader title="Pacientes" subtitle="Gestión de pacientes">
      <v-btn variant="flat" prepend-icon="mdi-paw" @click="openCreate">Nuevo paciente</v-btn>
    </PageHeader>

    <v-card elevation="0" border rounded="xl" class="mb-1">
      <v-card-text class="d-flex align-center ga-3 py-4 px-6">
        <v-text-field
          v-model="params.q"
          label="Buscar por nombre, código o tutor"
          prepend-inner-icon="mdi-magnify"
          density="comfortable"
          hide-details
          clearable
          variant="outlined"
          @keydown.enter="params.page = 0; load()"
          @click:clear="params.page = 0; load()"
        />
        <v-btn variant="tonal" prepend-icon="mdi-magnify" size="large" @click="params.page = 0; load()">
          Buscar
        </v-btn>
      </v-card-text>
    </v-card>

    <v-card elevation="0" border rounded="xl">
      <v-data-table
        :headers="[
          { title: 'Código', key: 'codigo' },
          { title: 'Nombre', key: 'nombre' },
          { title: 'Especie/Raza', key: 'especie' },
          { title: 'Sexo', key: 'sexo' },
          { title: 'Color', key: 'color' },
          { title: 'Nacimiento', key: 'fechaNacimiento' },
          { title: 'Peso', key: 'pesoActual' },
          { title: 'Estado', key: 'activo' },
          { title: 'Acciones', key: 'acciones', sortable: false, align: 'end' },
        ]"
        :items="items"
        :loading="loading"
        :items-per-page="-1"
        hide-default-footer
        density="comfortable"
      >
        <template #item.codigo="{ item }">
          <v-chip size="small" variant="tonal" :text="item.codigo" />
        </template>
        <template #item.especie="{ item }">
          {{ item.especie.nombre }}{{ item.raza ? ` · ${item.raza.nombre}` : '' }}
          <v-chip
            v-if="item.raza?.potencialmentePeligrosa"
            size="x-small"
            color="error"
            variant="tonal"
            prepend-icon="mdi-alert"
            class="ml-1"
            text="Pot. peligrosa"
          />
        </template>
        <template #item.sexo="{ item }">{{ sexoLabel(item.sexo) }}</template>
        <template #item.fechaNacimiento="{ item }">{{ item.fechaNacimiento ?? '—' }}</template>
        <template #item.pesoActual="{ item }">{{ pesoFormateado(item) }}</template>
        <template #item.activo="{ item }">
          <v-chip
            size="small"
            variant="tonal"
            :color="item.activo ? 'success' : 'error'"
            :text="item.activo ? 'Activo' : 'Inactivo'"
          />
        </template>
        <template #item.acciones="{ item }">
          <v-btn
            v-if="auth.can.clinicaRead"
            size="small"
            variant="text"
            icon="mdi-timeline-text-outline"
            title="Historia del paciente"
            :to="{ name: 'historia', params: { id: item.id } }"
          />
          <v-btn size="small" variant="text" icon="mdi-pencil" title="Editar" @click="openEdit(item)" />
        </template>
        <template #bottom>
          <div v-if="params.totalPages > 1" class="pa-4 d-flex justify-center">
            <v-pagination
              v-model="params.page"
              :length="params.totalPages"
              :total-visible="7"
              @update:model-value="load"
            />
          </div>
        </template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="dialog" max-width="760" scrollable>
      <v-card rounded="xl" elevation="4">
        <v-card-title class="text-h6 pt-4">
          {{ editingId === null ? 'Nuevo paciente' : 'Editar paciente' }}
        </v-card-title>
        <v-card-text>
          <v-form ref="formRef" @submit.prevent="save">
            <v-row dense class="mb-1">
              <v-col cols="12" sm="6" class="pr-6">
                <v-text-field
                  v-model="form.nombre"
                  label="Nombre del paciente"
                  :rules="nombreRules"
                  density="comfortable"
                  autofocus
                />
              </v-col>
              <v-col cols="12" sm="6" class="pl-6">
                <v-select
                  v-model="form.especieId"
                  label="Especie"
                  :items="especieOptions"
                  item-title="title"
                  item-value="value"
                  :rules="especieRules"
                  density="comfortable"
                />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" sm="6" class="pr-6">
                <RazaAutocomplete v-model="form.razaId" :razas="razas" :disabled="!form.especieId" />
              </v-col>
              <v-col cols="12" sm="6" class="pl-6">
                <v-select
                  v-model="form.sexo"
                  label="Sexo"
                  :items="sexoOptions"
                  item-title="title"
                  item-value="value"
                  :rules="sexoRules"
                  density="comfortable"
                />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" sm="6" class="pr-6">
                <v-text-field v-model="form.estadoReproductivo" label="Estado reproductivo" density="comfortable" />
              </v-col>
              <v-col cols="12" sm="6" class="pl-6">
                <v-text-field v-model="form.color" label="Color" density="comfortable" />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" sm="4" class="pr-6">
                <v-text-field v-model="form.fechaNacimiento" label="Fecha de nacimiento" type="date" density="comfortable" />
              </v-col>
              <v-col cols="12" sm="4" class="px-3">
                <v-text-field v-model="form.microchip" label="Microchip" density="comfortable" />
              </v-col>
              <v-col cols="12" sm="4" class="pl-6">
                <v-text-field
                  v-model.number="form.pesoActual"
                  label="Peso (kg)"
                  type="number"
                  density="comfortable"
                  min="0"
                  step="0.01"
                />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" class="pr-2">
                <v-select
                  v-model="form.tutorIds"
                  label="Tutores"
                  :items="tutorOptions"
                  item-title="title"
                  item-value="value"
                  multiple
                  clearable
                  density="comfortable"
                  :loading="tutorOptionsLoading"
                />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" class="pr-2">
                <v-select
                  v-if="form.tutorIds.length > 0"
                  v-model="form.tutorPrincipalId"
                  label="Tutor principal"
                  :items="tutorPrincipalOptions"
                  item-title="title"
                  item-value="value"
                  density="comfortable"
                />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" class="pr-2">
                <v-textarea v-model="form.alertas" label="Alertas" rows="3" density="comfortable" auto-grow />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" class="pr-2">
                <v-textarea v-model="form.notas" label="Notas" rows="3" density="comfortable" auto-grow />
              </v-col>
            </v-row>
            <v-switch
              v-model="form.esterilizado"
              label="Esterilizado"
              color="primary"
              hide-details
              class="mr-4"
            />
            <v-switch v-model="form.fallecido" label="Fallecido" color="error" hide-details />
          </v-form>
        </v-card-text>
        <v-card-actions class="px-4 pb-4">
          <v-spacer />
          <v-btn variant="text" @click="dialog = false">Cancelar</v-btn>
          <v-btn color="primary" :loading="saving" :disabled="saving" variant="flat" @click="save">
            {{ editingId === null ? 'Crear paciente' : 'Guardar cambios' }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>