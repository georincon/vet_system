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
    /** Etapa A (docs/api-etapa-a-clinica.md, matriz de roles): RECEPCION no accede a nada clínico. */
    laboratorioRead: hasRole('ADMIN', 'VETERINARIO', 'AUXILIAR'),
    laboratorioOrdenar: hasRole('ADMIN', 'VETERINARIO'),
    /** Toma de muestra, captura de resultados e importación (AUXILIAR incluido). */
    laboratorioCapturar: hasRole('ADMIN', 'VETERINARIO', 'AUXILIAR'),
    /** Validar, corregir, informes de imagen/patología. */
    laboratorioValidar: hasRole('ADMIN', 'VETERINARIO'),
    catalogoLabRead: hasRole('ADMIN', 'VETERINARIO', 'AUXILIAR'),
    catalogoLabWrite: hasRole('ADMIN', 'VETERINARIO'),
    archivosSubir: hasRole('ADMIN', 'VETERINARIO', 'AUXILIAR'),
    archivosEliminar: hasRole('ADMIN', 'VETERINARIO'),
    procedimientosRead: hasRole('ADMIN', 'VETERINARIO', 'AUXILIAR'),
    procedimientosWrite: hasRole('ADMIN', 'VETERINARIO'),
    monitoreoAnestesico: hasRole('ADMIN', 'VETERINARIO', 'AUXILIAR'),
    /** Etapa B. */
    planWrite: hasRole('ADMIN', 'VETERINARIO'),
    perfilProfesional: hasRole('ADMIN', 'VETERINARIO'),
    /** ADMIN y VETERINARIO comparten siempre; AUXILIAR solo si el backend lo habilita (ver utils/compartir.ts). */
    compartir: hasRole('ADMIN', 'VETERINARIO'),
    compartirRevocar: hasRole('ADMIN', 'VETERINARIO'),
    /** Historial de cobros en la historia: ADMIN ve presupuestos y facturas; VETERINARIO solo presupuestos de sus consultas. */
    cobrosHistoria: hasRole('ADMIN', 'VETERINARIO'),
    cobrosFacturas: hasRole('ADMIN'),
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