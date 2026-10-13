<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { errorText } from '@/utils/errors'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { formRules } from '@/utils/rules'
import type { Rule } from '@/utils/rules'

interface FormHandle {
  validate: () => Promise<{ valid: boolean }>
}

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()
const route = useRoute()

const formRef = ref<FormHandle | null>(null)
const username = ref('admin')
const password = ref('admin')
const loading = ref(false)
const showPassword = ref(false)

const usernameRules: Rule[] = [formRules.required('Ingrese el usuario')]
const passwordRules: Rule[] = [formRules.required('Ingrese la contraseña')]

async function submit() {
  const result = await formRef.value?.validate()
  if (!result?.valid) return
  loading.value = true
  try {
    await auth.login(username.value, password.value)
    const redirect = route.query.redirect
    const target =
      typeof redirect === 'string' && redirect.startsWith('/') ? redirect : { name: 'dashboard' }
    await router.push(target)
    ui.showSuccess('Sesión iniciada correctamente')
  } catch (error) {
    ui.showError(errorText(error, 'No se pudo iniciar sesión'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <v-app>
    <v-main>
      <div class="login-wrapper">
        <div class="login-left" role="presentation"></div>
        <div class="login-right">
          <div class="login-right-inner">
            <div class="text-center mb-4">
              <img class="login-logo" src="/patita-verde.svg" alt="Patita" />
            </div>
            <h1 class="login-title">Bienvenido al sistema</h1>
            <p class="login-subtitle">Sistema de Gestión Veterinaria</p>

            <v-form ref="formRef" class="mt-6" @submit.prevent="submit">
              <v-text-field
                v-model="username"
                label="Usuario"
                placeholder="Digite su nombre de usuario"
                prepend-inner-icon="mdi-account-outline"
                :rules="usernameRules"
                autocomplete="username"
                autofocus
              />
              <v-text-field
                v-model="password"
                class="mt-4"
                label="Contraseña"
                placeholder="Digite su contraseña"
                :type="showPassword ? 'text' : 'password'"
                prepend-inner-icon="mdi-lock-outline"
                :append-inner-icon="showPassword ? 'mdi-eye-off-outline' : 'mdi-eye-outline'"
                :rules="passwordRules"
                autocomplete="current-password"
                @click:append-inner="showPassword = !showPassword"
              />
              <v-btn
                class="mt-6"
                type="submit"
                color="primary"
                size="large"
                block
                :loading="loading"
                :disabled="loading"
              >
                Iniciar sesión
              </v-btn>
            </v-form>

            <p class="login-footer-note">Clínica Veterinaria &middot; Sistema de Gestión</p>
            <div class="vet-footer-accent"></div>
          </div>
        </div>
      </div>
    </v-main>
  </v-app>
</template>
