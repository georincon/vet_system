import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { turnoActual } from '@/api/facturacion'
import type { Turno } from '@/types/fase4'

/** Turno de caja abierto del usuario (compartido por POS, pagos y abonos). */
export const useCajaStore = defineStore('caja', () => {
  const turno = ref<Turno | null>(null)
  const cargado = ref(false)
  const abierto = computed(() => turno.value?.estado === 'ABIERTO')

  async function refrescar() {
    try { turno.value = await turnoActual() } catch { turno.value = null }
    cargado.value = true
    return turno.value
  }
  function set(t: Turno | null) { turno.value = t; cargado.value = true }
  return { turno, cargado, abierto, refrescar, set }
})
