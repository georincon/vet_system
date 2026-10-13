import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listEstadosCita } from '@/api/citas'
import { listSalas, listTiposCita } from '@/api/agenda'
import { listEspecies } from '@/api/catalogos'
import { listVeterinarios } from '@/api/usuarios'
import type { Especie, EstadoCita, Sala, TipoCita, VeterinarioOpcion } from '@/types'

/** Catálogos de baja rotación (se cargan una vez por sesión). */
export const useCatalogosStore = defineStore('catalogos', () => {
  const veterinarios = ref<VeterinarioOpcion[]>([])
  const salas = ref<Sala[]>([])
  const tiposCita = ref<TipoCita[]>([])
  const especies = ref<Especie[]>([])
  const estadosCita = ref<EstadoCita[]>([])
  let loaded: Promise<void> | null = null

  function ensure(): Promise<void> {
    if (!loaded) {
      loaded = Promise.all([
        listVeterinarios().then((v) => (veterinarios.value = v)),
        listSalas().then((v) => (salas.value = v)),
        listTiposCita().then((v) => (tiposCita.value = v)),
        listEspecies().then((v) => (especies.value = v)),
        listEstadosCita().then((v) => (estadosCita.value = v)),
      ]).then(() => undefined).catch((e) => {
        loaded = null
        throw e
      })
    }
    return loaded
  }

  function reload() {
    loaded = null
    return ensure()
  }

  return { veterinarios, salas, tiposCita, especies, estadosCita, ensure, reload }
})
