<script setup lang="ts">
import { reactive, ref } from 'vue'
import { onMounted } from 'vue'
import { createTutor, listPacientesDeTutor, listTutores, updateTutor } from '@/api/tutores'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { formRules } from '@/utils/rules'
import type { Rule } from '@/utils/rules'
import {
  TIPOS_DOCUMENTO,
  TIPO_DOCUMENTO_LABELS,
  type PacienteResumen,
  type TipoDocumento,
  type Tutor,
  type TutorRequest,
} from '@/types'

interface FormHandle {
  validate: () => Promise<{ valid: boolean }>
  reset: () => void
}

const ui = useUiStore()

const items = ref<Tutor[]>([])
const loading = ref(false)
const params = reactive({ q: '', page: 0, size: 10, totalElements: 0, totalPages: 0 })

const dialog = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormHandle | null>(null)
const form = reactive<TutorRequest>({
  tipoDocumento: 'CC',
  numeroDocumento: '',
  nombres: '',
  apellidos: '',
  telefono: '',
  telefonoAlt: '',
  email: '',
  direccion: '',
  ciudad: '',
  notas: '',
  activo: true,
})

const pacientesDialog = ref(false)
const pacientesLoading = ref(false)
const pacientes = ref<PacienteResumen[]>([])

const tipoDocumentoRules: Rule[] = [formRules.requiredSelect('Seleccione el tipo de documento')]
const numeroDocumentoRules: Rule[] = [formRules.required('Ingrese el número de documento')]
const nombresRules: Rule[] = [formRules.required('Ingrese los nombres')]
const emailRules: Rule[] = [formRules.email()]

const tipoDocumentoOptions = TIPOS_DOCUMENTO.map((tipo) => ({
  title: TIPO_DOCUMENTO_LABELS[tipo],
  value: tipo,
}))

function tipoDocumentoLabel(tipo: TipoDocumento): string {
  return TIPO_DOCUMENTO_LABELS[tipo] ?? tipo
}

function tutorNombre(tutor: Tutor): string {
  return `${tutor.nombres} ${tutor.apellidos ?? ''}`.trim()
}

async function load() {
  loading.value = true
  try {
    const page = await listTutores(params.q || undefined, params.page, params.size)
    items.value = page.items
    params.totalElements = page.totalElements
    params.totalPages = page.totalPages
  } catch (error) {
    ui.showError(errorText(error, 'No se pudieron cargar los tutores'))
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.tipoDocumento = 'CC'
  form.numeroDocumento = ''
  form.nombres = ''
  form.apellidos = ''
  form.telefono = ''
  form.telefonoAlt = ''
  form.email = ''
  form.direccion = ''
  form.ciudad = ''
  form.notas = ''
  form.activo = true
}

function openCreate() {
  editingId.value = null
  resetForm()
  formRef.value?.reset()
  dialog.value = true
}

function openEdit(tutor: Tutor) {
  editingId.value = tutor.id
  form.tipoDocumento = tutor.tipoDocumento
  form.numeroDocumento = tutor.numeroDocumento
  form.nombres = tutor.nombres
  form.apellidos = tutor.apellidos ?? ''
  form.telefono = tutor.telefono ?? ''
  form.telefonoAlt = tutor.telefonoAlt ?? ''
  form.email = tutor.email ?? ''
  form.direccion = tutor.direccion ?? ''
  form.ciudad = tutor.ciudad ?? ''
  form.notas = tutor.notas ?? ''
  form.activo = tutor.activo
  dialog.value = true
}

async function save() {
  const result = await formRef.value?.validate()
  if (!result?.valid) return
  saving.value = true
  try {
    if (editingId.value === null) {
      await createTutor(form)
      ui.showSuccess('Tutor creado correctamente')
    } else {
      await updateTutor(editingId.value, form)
      ui.showSuccess('Tutor actualizado correctamente')
    }
    dialog.value = false
    params.page = editingId.value === null ? 0 : params.page
    await load()
  } catch (error) {
    ui.showError(errorText(error, 'No se pudo guardar el tutor'))
  } finally {
    saving.value = false
  }
}

async function openPacientes(tutor: Tutor) {
  pacientes.value = []
  pacientesDialog.value = true
  pacientesLoading.value = true
  try {
    pacientes.value = await listPacientesDeTutor(tutor.id)
  } catch (error) {
    ui.showError(errorText(error, 'No se pudieron cargar los pacientes del tutor'))
  } finally {
    pacientesLoading.value = false
  }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div>
    <div class="vet-header">
      <div><h1>Tutores</h1><p>Gestión de tutores</p></div>
      <v-btn variant="flat" prepend-icon="mdi-account-plus-outline" @click="openCreate">
        Nuevo tutor
      </v-btn>
    </div>

    <v-card elevation="0" border rounded="xl" class="mb-1">
      <v-card-text class="d-flex align-center ga-3 py-4 px-6">
        <v-text-field
          v-model="params.q"
          label="Buscar por nombres, apellidos o documento"
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
          { title: 'Documento', key: 'numeroDocumento' },
          { title: 'Nombre', key: 'nombreCompleto' },
          { title: 'Teléfono', key: 'telefono' },
          { title: 'Correo', key: 'email' },
          { title: 'Ciudad', key: 'ciudad' },
          { title: 'Estado', key: 'activo' },
          { title: 'Acciones', key: 'acciones', sortable: false, align: 'end' },
        ]"
        :items="items"
        :loading="loading"
        :items-per-page="-1"
        hide-default-footer
        density="comfortable"
      >
        <template #item.numeroDocumento="{ item }">
          <div class="text-body-2">{{ tipoDocumentoLabel(item.tipoDocumento) }}</div>
          <div class="font-weight-medium">{{ item.numeroDocumento }}</div>
        </template>
        <template #item.nombreCompleto="{ item }">{{ tutorNombre(item) }}</template>
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
            size="small"
            variant="text"
            icon="mdi-paw"
            title="Ver pacientes"
            @click="openPacientes(item)"
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

    <v-dialog v-model="dialog" max-width="680" scrollable>
      <v-card rounded="xl" elevation="4">
        <v-card-title class="text-h6 pt-4">
          {{ editingId === null ? 'Nuevo tutor' : 'Editar tutor' }}
        </v-card-title>
        <v-card-text>
          <v-form ref="formRef" @submit.prevent="save">
            <v-row dense class="mb-1">
              <v-col cols="12" sm="6" class="pr-6">
                <v-select
                  v-model="form.tipoDocumento"
                  label="Tipo de documento"
                  :items="tipoDocumentoOptions"
                  item-title="title"
                  item-value="value"
                  :rules="tipoDocumentoRules"
                  density="comfortable"
                />
              </v-col>
              <v-col cols="12" sm="6" class="pl-6">
                <v-text-field
                  v-model="form.numeroDocumento"
                  label="Número de documento"
                  :rules="numeroDocumentoRules"
                  density="comfortable"
                  autofocus
                />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" sm="6" class="pr-6">
                <v-text-field v-model="form.nombres" label="Nombres" :rules="nombresRules" density="comfortable" />
              </v-col>
              <v-col cols="12" sm="6" class="pl-6">
                <v-text-field v-model="form.apellidos" label="Apellidos" density="comfortable" />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" sm="6" class="pr-6">
                <v-text-field v-model="form.telefono" label="Teléfono" density="comfortable" />
              </v-col>
              <v-col cols="12" sm="6" class="pl-6">
                <v-text-field v-model="form.telefonoAlt" label="Teléfono alternativo" density="comfortable" />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" class="pr-2">
                <v-text-field v-model="form.email" label="Correo electrónico" :rules="emailRules" type="email" density="comfortable" />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" sm="6" class="pr-6">
                <v-text-field v-model="form.direccion" label="Dirección" density="comfortable" />
              </v-col>
              <v-col cols="12" sm="6" class="pl-6">
                <v-text-field v-model="form.ciudad" label="Ciudad" density="comfortable" />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" class="pr-2">
                <v-textarea v-model="form.notas" label="Notas" rows="3" density="comfortable" auto-grow />
              </v-col>
            </v-row>
            <v-switch v-model="form.activo" label="Tutor activo" color="primary" hide-details />
          </v-form>
        </v-card-text>
        <v-card-actions class="px-4 pb-4">
          <v-spacer />
          <v-btn variant="text" @click="dialog = false">Cancelar</v-btn>
          <v-btn color="primary" :loading="saving" :disabled="saving" variant="flat" @click="save">
            {{ editingId === null ? 'Crear tutor' : 'Guardar cambios' }}
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog v-model="pacientesDialog" max-width="640">
      <v-card>
        <v-card-title class="text-h6 pt-4">Pacientes del tutor</v-card-title>
        <v-card-text>
          <v-alert v-if="!pacientesLoading && pacientes.length === 0" type="info" variant="tonal">
            Este tutor no tiene pacientes registrados.
          </v-alert>
          <v-list v-else>
            <v-list-item v-for="paciente in pacientes" :key="paciente.id" :title="paciente.nombre">
              <template #prepend>
                <v-icon icon="mdi-paw" color="primary" />
              </template>
              <template #subtitle>
                {{ paciente.especie }}{{ paciente.raza ? ` · ${paciente.raza}` : '' }} ·
                {{ paciente.sexo === 'M' ? 'Macho' : 'Hembra' }} · {{ paciente.codigo }}
              </template>
            </v-list-item>
          </v-list>
        </v-card-text>
        <v-card-actions class="px-4 pb-4">
          <v-spacer />
          <v-btn variant="text" @click="pacientesDialog = false">Cerrar</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>

<style scoped>
.flex-0-1-220 {
  flex: 0 1 220px;
}
</style>