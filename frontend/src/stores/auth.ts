import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as authApi from '@/api/auth'
import type { LoginResponse, Rol, Usuario } from '@/types'

const TOKEN_KEY = 'vet_token'
const SESSION_KEY = 'vet_session'

interface PersistedSession {
  usuario: Usuario
  expiraEn: number
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(null)
  const usuario = ref<Usuario | null>(null)
  const expiraEn = ref<number | null>(null)

  const isAuthenticated = computed(
    () => !!token.value && !!expiraEn.value && expiraEn.value > Date.now(),
  )
  const isAdmin = computed(() => usuario.value?.rol === 'ADMIN')
  const rol = computed(() => usuario.value?.rol ?? null)

  const hasRole = (...roles: Rol[]) => !!usuario.value && roles.includes(usuario.value.rol)
  /** Permisos coherentes con la matriz de roles del backend (docs/api-sprint1.md). */
  const can = computed(() => ({
    agendaWrite: hasRole('ADMIN', 'VETERINARIO', 'RECEPCION'),
    agendaAdmin: hasRole('ADMIN', 'RECEPCION'),
    iniciarConsulta: hasRole('ADMIN', 'VETERINARIO'),
    clinicaRead: hasRole('ADMIN', 'VETERINARIO', 'AUXILIAR'),
    clinicaWrite: hasRole('ADMIN', 'VETERINARIO'),
    signos: hasRole('ADMIN', 'VETERINARIO', 'AUXILIAR'),
    /** Fase 3: catálogos y pendientes de lectura para todos; aplicar con supervisión (AUXILIAR). */
    inmunizacionAplicar: hasRole('ADMIN', 'VETERINARIO', 'AUXILIAR'),
    inmunizacionAnular: hasRole('ADMIN', 'VETERINARIO'),
    inmunizacionCatalogos: hasRole('ADMIN'),
    comunicacionesRead: hasRole('ADMIN', 'RECEPCION'),
    comunicacionesWrite: hasRole('ADMIN'),
    /** Fase 4 (docs/api-fase4.md, matriz de roles). */
    inventarioRead: hasRole('ADMIN', 'RECEPCION', 'VETERINARIO', 'AUXILIAR'),
    inventarioWrite: hasRole('ADMIN'),
    inventarioUso: hasRole('ADMIN', 'VETERINARIO', 'AUXILIAR'),
    inventarioCostos: hasRole('ADMIN'),
    controladosRead: hasRole('ADMIN', 'VETERINARIO'),
    comprasRead: hasRole('ADMIN'),
    comprasWrite: hasRole('ADMIN'),
    /** Caja, POS, facturas, pagos, cartera y abonos. */
    facturacionOperar: hasRole('ADMIN', 'RECEPCION'),
    /** VETERINARIO solo ve los presupuestos de sus consultas. */
    presupuestosRead: hasRole('ADMIN', 'RECEPCION', 'VETERINARIO'),
    notasCredito: hasRole('ADMIN'),
    reportesVentas: hasRole('ADMIN'),
    reportesCartera: hasRole('ADMIN', 'RECEPCION'),
    ventasDashboard: hasRole('ADMIN', 'RECEPCION'),
  }))

  function setSession(data: LoginResponse) {
    token.value = data.token
    usuario.value = data.usuario
    expiraEn.value = data.expiraEn
    localStorage.setItem(TOKEN_KEY, data.token)
    const session: PersistedSession = { usuario: data.usuario, expiraEn: data.expiraEn }
    localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  }

  function logout() {
    token.value = null
    usuario.value = null
    expiraEn.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(SESSION_KEY)
  }

  function hydrate() {
    const storedToken = localStorage.getItem(TOKEN_KEY)
    const storedSession = localStorage.getItem(SESSION_KEY)
    if (!storedToken || !storedSession) {
      logout()
      return
    }
    try {
      const session = JSON.parse(storedSession) as PersistedSession
      if (!session.usuario || !session.expiraEn || session.expiraEn <= Date.now()) {
        logout()
        return
      }
      token.value = storedToken
      usuario.value = session.usuario
      expiraEn.value = session.expiraEn
      void refreshUser()
    } catch {
      logout()
    }
  }

  async function refreshUser() {
    if (!isAuthenticated.value) return
    try {
      usuario.value = await authApi.fetchMe()
    } catch {
    }
  }

  async function login(username: string, password: string): Promise<void> {
    const data = await authApi.login(username, password)
    setSession(data)
  }

  return {
    token,
    usuario,
    expiraEn,
    isAuthenticated,
    isAdmin,
    rol,
    can,
    login,
    logout,
    hydrate,
    refreshUser,
  }
})