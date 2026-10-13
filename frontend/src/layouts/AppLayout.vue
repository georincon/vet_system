<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useDisplay } from 'vuetify'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { ROL_LABELS, type Rol } from '@/types'

interface NavItem {
  title: string
  icon: string
  to: string
}

interface NavGroup {
  title: string
  items: NavItem[]
}

const auth = useAuthStore()
const router = useRouter()
const { mobile } = useDisplay()

const drawer = ref(!mobile.value)
const confirmLogout = ref(false)

const groups = computed<NavGroup[]>(() => {
  const all: NavGroup[] = [
    {
      title: 'Operación',
      items: [
        { title: 'Dashboard', icon: 'mdi-view-dashboard-outline', to: '/dashboard' },
        { title: 'Agenda', icon: 'mdi-calendar-clock-outline', to: '/agenda' },
        { title: 'Tutores', icon: 'mdi-account-heart-outline', to: '/tutores' },
        { title: 'Pacientes', icon: 'mdi-paw-outline', to: '/pacientes' },
        ...(auth.can.clinicaRead
          ? [{ title: 'Consultas', icon: 'mdi-stethoscope', to: '/consultas' }]
          : []),
      ],
    },
  ]

  if (auth.isAdmin) {
    all.push({
      title: 'Sistema',
      items: [
        { title: 'Usuarios', icon: 'mdi-account-cog-outline', to: '/usuarios' },
        { title: 'Auditoría', icon: 'mdi-shield-search', to: '/auditoria' },
      ],
    })
  }

  return all
})

const userLabel = computed(() => {
  const user = auth.usuario
  if (!user) return ''
  const rolLabel = ROL_LABELS[user.rol as Rol] ?? user.rol
  return `${user.nombre} (${rolLabel})`
})

function onLogout() {
  confirmLogout.value = false
  auth.logout()
  void router.push({ name: 'login' })
}
</script>

<template>
  <v-app>
    <v-app-bar flat class="vet-appbar" height="64">
      <v-app-bar-nav-icon
        variant="text"
        aria-label="Mostrar u ocultar el menú"
        @click="drawer = !drawer"
      />
      <v-toolbar-title class="vet-brand">
        <img src="/patita.svg" alt="" />
        <span>Sistema de Gestión Veterinaria</span>
      </v-toolbar-title>
      <v-spacer />
      <v-chip
        v-if="auth.usuario"
        class="mr-2" size="small" variant="outlined" color="white"
        prepend-icon="mdi-account-circle"
        :text="userLabel"
      />
      <v-btn
        variant="text"
        icon="mdi-logout"
        aria-label="Cerrar sesión"
        title="Cerrar sesión"
        @click="confirmLogout = true"
      />
    </v-app-bar>

    <v-navigation-drawer v-model="drawer" elevation="0" width="280">
      <v-list nav density="comfortable" class="py-4 px-2">
        <template v-for="(group, index) in groups" :key="group.title">
          <v-list-subheader class="text-medium-emphasis text-caption font-weight-bold px-3">
            {{ group.title.toUpperCase() }}
          </v-list-subheader>
          <v-list-item
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            :prepend-icon="item.icon"
            :title="item.title"
            color="primary"
            rounded="xl"
            class="mb-1"
          />
          <v-divider v-if="index < groups.length - 1" class="my-4" />
        </template>
      </v-list>
    </v-navigation-drawer>

    <v-main>
      <v-container fluid class="pa-4 pa-sm-6">
        <div class="vet-content">
          <router-view />
          <div class="vet-footer-accent"></div>
        </div>
      </v-container>
    </v-main>

    <ConfirmDialog
      v-model="confirmLogout"
      title="Cerrar sesión"
      message="¿Desea cerrar la sesión actual?"
      confirm-text="Cerrar sesión"
      color="primary"
      @confirm="onLogout"
    />
  </v-app>
</template>