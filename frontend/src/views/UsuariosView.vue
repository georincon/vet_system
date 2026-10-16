<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { listUsuarios, createUsuario } from '@/api/usuarios'
import { getPerfilProfesional, updatePerfilProfesionalUsuario } from '@/api/formula'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { formRules, password } from '@/utils/rules'
import type { Rule } from '@/utils/rules'
import { ROLES, ROL_LABELS, type Rol, type Usuario, type UsuarioRequest } from '@/types'

interface FormHandle {
  validate: () => Promise<{ valid: boolean }>
  reset: () => void
}

const ui = useUiStore()
const auth = useAuthStore()

const items = ref<Usuario[]>([])
const loading = ref(false)
const params = reactive({ q: '', page: 0, size: 10, totalElements: 0, totalPages: 0 })

const dialog = ref(false)
const saving = ref(false)
const formRef = ref<FormHandle | null>(null)
const form = reactive<UsuarioRequest>({
  username: '',
  password: '',
  nombre: '',
  email: '',
  rol: 'RECEPCION',
  activo: true,
})

const usernameRules: Rule[] = [formRules.required('Ingrese el usuario')]
const passwordRules: Rule[] = [
  formRules.required('Ingrese la contraseña'),
  password('La contraseña debe tener al menos 6 caracteres'),
]
const nombreRules: Rule[] = [formRules.required('Ingrese el nombre')]
const emailRules: Rule[] = [formRules.email()]
const rolRules: Rule[] = [formRules.requiredSelect('Seleccione un rol')]

function rolLabel(rol: Rol): string {
  return ROL_LABELS[rol] ?? rol
}

const rolOptions = ROLES.map((rol) => ({ title: ROL_LABELS[rol] ?? rol, value: rol }))

async function load() {
  loading.value = true
  try {
    const page = await listUsuarios(params.q || undefined, params.page, params.size)
    items.value = page.items
    params.totalElements = page.totalElements
    params.totalPages = page.totalPages
  } catch (error) {
    ui.showError(errorText(error, 'No se pudieron cargar los usuarios'))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.username = ''
  form.password = ''
  form.nombre = ''
  form.email = ''
  form.rol = 'RECEPCION'
  dialog.value = true
}

async function save() {
  const result = await formRef.value?.validate()
  if (!result?.valid) return
  saving.value = true
  try {
    await createUsuario(form)
    ui.showSuccess('Usuario creado correctamente')
    dialog.value = false
    params.page = 0
    await load()
  } catch (error) {
    ui.showError(errorText(error, 'No se pudo crear el usuario'))
  } finally {
    saving.value = false
  }
}

// Tarjeta profesional (solo VETERINARIO/ADMIN; la edita el ADMIN)
const tpDialog = ref(false)
const tpUsuario = ref<Usuario | null>(null)
const tpNumero = ref('')
const tpEspecialidad = ref('')
const tpSaving = ref(false)
async function abrirTp(u: Usuario) {
  tpUsuario.value = u
  tpNumero.value = ''
  tpEspecialidad.value = ''
  tpDialog.value = true
  // El GET del perfil es del usuario actual; para otros se parte de vacío y "" limpia.
  if (u.id === auth.usuario?.id) {
    try { const p = await getPerfilProfesional(); tpNumero.value = p.numeroProfesional ?? ''; tpEspecialidad.value = p.especialidad ?? '' } catch { /* vacío */ }
  }
}
async function guardarTp() {
  if (!tpUsuario.value) return
  tpSaving.value = true
  try {
    await updatePerfilProfesionalUsuario(tpUsuario.value.id, {
      numeroProfesional: tpNumero.value.trim() || undefined, especialidad: tpEspecialidad.value.trim() || undefined,
    })
    ui.showSuccess('Tarjeta profesional actualizada')
    tpDialog.value = false
  } catch (error) { ui.showError(errorText(error, 'No se pudo guardar la tarjeta profesional')) } finally { tpSaving.value = false }
}

onMounted(() => {
  void load()
})
</script>

<template>
  <div>
    <div class="vet-header">
      <div><h1>Usuarios</h1><p>Administración de usuarios</p></div>
      <v-btn variant="flat" prepend-icon="mdi-account-plus-outline" @click="openCreate">
        Nuevo usuario
      </v-btn>
    </div>

    <v-card variant="outlined" class="mb-1">
      <v-card-text class="d-flex align-center ga-2 pt-4">
        <v-text-field
          v-model="params.q"
          label="Buscar por usuario o nombre"
          prepend-inner-icon="mdi-magnify"
          density="compact"
          hide-details
          clearable
          @keydown.enter="params.page = 0; load()"
          @click:clear="params.page = 0; load()"
        />
        <v-btn variant="tonal" prepend-icon="mdi-magnify" @click="params.page = 0; load()">
          Buscar
        </v-btn>
      </v-card-text>
    </v-card>

    <v-card variant="outlined">
      <v-data-table
        :headers="[
          { title: 'Usuario', key: 'username' },
          { title: 'Nombre', key: 'nombre' },
          { title: 'Correo', key: 'email' },
          { title: 'Rol', key: 'rol' },
          { title: 'Estado', key: 'activo' },
          { title: 'Tarjeta prof.', key: 'tp', sortable: false },
        ]"
        :items="items"
        :loading="loading"
        :items-per-page="-1"
        hide-default-footer
        density="comfortable"
      >
        <template #item.rol="{ item }">
          <v-chip
            size="small"
            variant="tonal"
            :color="item.rol === 'ADMIN' ? 'primary' : undefined"
            :text="rolLabel(item.rol)"
          />
        </template>
        <template #item.activo="{ item }">
          <v-chip
            size="small"
            variant="tonal"
            :color="item.activo ? 'success' : 'error'"
            :text="item.activo ? 'Activo' : 'Inactivo'"
          />
        </template>
        <template #item.tp="{ item }">
          <v-btn v-if="item.rol === 'VETERINARIO' || item.rol === 'ADMIN'" size="small" variant="text" prepend-icon="mdi-card-account-details-outline" @click="abrirTp(item)">
            Tarjeta profesional
          </v-btn>
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

    <v-dialog v-model="dialog" max-width="560">
      <v-card>
        <v-card-title class="text-h6 pt-4">Nuevo usuario</v-card-title>
        <v-card-text>
          <v-form ref="formRef" @submit.prevent="save">
            <v-row dense class="mb-1">
              <v-col cols="12" class="pr-2">
                <v-text-field v-model="form.username" label="Usuario" :rules="usernameRules" density="comfortable" autofocus />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" class="pr-2">
                <v-text-field
                  v-model="form.password"
                  label="Contraseña"
                  type="password"
                  :rules="passwordRules"
                  density="comfortable"
                />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" class="pr-2">
                <v-text-field v-model="form.nombre" label="Nombre completo" :rules="nombreRules" density="comfortable" />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" class="pr-2">
                <v-text-field v-model="form.email" label="Correo electrónico" :rules="emailRules" type="email" density="comfortable" />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" class="pr-2">
                <v-select
                  v-model="form.rol"
                  label="Rol"
                  :items="rolOptions"
                  item-title="title"
                  item-value="value"
                  :rules="rolRules"
                  density="comfortable"
                />
              </v-col>
            </v-row>
            <v-row dense class="mb-1">
              <v-col cols="12" class="pr-2">
                <v-switch v-model="form.activo" label="Usuario activo" color="primary" hide-details />
              </v-col>
            </v-row>
          </v-form>
        </v-card-text>
        <v-card-actions class="px-4 pb-4">
          <v-spacer />
          <v-btn variant="text" @click="dialog = false">Cancelar</v-btn>
          <v-btn color="primary" :loading="saving" :disabled="saving" variant="flat" @click="save">
            Crear usuario
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
    <v-dialog v-model="tpDialog" max-width="480">
      <v-card>
        <v-card-title class="pt-4">Tarjeta profesional · {{ tpUsuario?.nombre }}</v-card-title>
        <v-card-text>
          <v-text-field v-model="tpNumero" label="Tarjeta profesional (T.P.)" maxlength="50" class="mb-3" />
          <v-text-field v-model="tpEspecialidad" label="Especialidad" maxlength="120" />
        </v-card-text>
        <v-card-actions class="px-4 pb-4"><v-spacer /><v-btn variant="text" @click="tpDialog = false">Cancelar</v-btn>
          <v-btn color="primary" variant="flat" :loading="tpSaving" @click="guardarTp">Guardar</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>