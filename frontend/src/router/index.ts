import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import type { Rol } from '@/types'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    roles?: Rol[]
    public?: boolean
    title?: string
  }
}

/** RECEPCION no accede a la clínica (403 en el backend). */
const CLINICA: Rol[] = ['ADMIN', 'VETERINARIO', 'AUXILIAR']

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true, title: 'Iniciar sesión' },
    },
    {
      path: '/',
      name: 'root',
      redirect: { name: 'dashboard' },
      meta: { requiresAuth: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      meta: { requiresAuth: true },
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: 'Dashboard' },
        },
        {
          path: 'usuarios',
          name: 'usuarios',
          component: () => import('@/views/UsuariosView.vue'),
          meta: { title: 'Usuarios', roles: ['ADMIN'] },
        },
        {
          path: 'tutores',
          name: 'tutores',
          component: () => import('@/views/TutoresView.vue'),
          meta: { title: 'Tutores' },
        },
        {
          path: 'pacientes',
          name: 'pacientes',
          component: () => import('@/views/PacientesView.vue'),
          meta: { title: 'Pacientes' },
        },
        {
          path: 'agenda',
          name: 'agenda',
          component: () => import('@/views/AgendaView.vue'),
          meta: { title: 'Agenda' },
        },
        {
          path: 'pacientes/:id(\\d+)/historia',
          name: 'historia',
          component: () => import('@/views/HistoriaView.vue'),
          meta: { title: 'Historia del paciente', roles: CLINICA },
        },
        {
          path: 'auditoria',
          name: 'auditoria',
          component: () => import('@/views/AuditoriaView.vue'),
          meta: { title: 'Auditoría', roles: ['ADMIN'] },
        },
        {
          path: 'citas',
          name: 'citas',
          component: () => import('@/views/CitasView.vue'),
          meta: { title: 'Citas' },
        },
        {
          path: 'consultas',
          name: 'consultas',
          component: () => import('@/views/ConsultasView.vue'),
          meta: { title: 'Consultas', roles: CLINICA },
        },
        {
          path: 'consultas/:id(\\d+)',
          name: 'consulta',
          component: () => import('@/views/ConsultaView.vue'),
          meta: { title: 'Consulta clínica', roles: CLINICA },
        },
      ],
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: 'Página no encontrada' },
    },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  if (to.name === 'login' && auth.isAuthenticated) {
    const redirect = to.query.redirect
    return typeof redirect === 'string' && redirect.startsWith('/') ? redirect : { name: 'dashboard' }
  }

  const roles = to.meta.roles
  if (roles && roles.length > 0) {
    const rol = auth.usuario?.rol
    if (!rol || !roles.includes(rol)) {
      useUiStore().showError('No tiene permisos')
      return { name: 'dashboard' }
    }
  }

  const title = to.meta.title
  document.title = title ? `${title} | Sistema de Gestión Veterinaria` : 'Sistema de Gestión Veterinaria'
  return true
})
