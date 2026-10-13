import { http } from './client'
import type { Especie, Raza } from '@/types'

export async function listEspecies(): Promise<Especie[]> {
  const response = await http.get<Especie[]>('/catalogos/especies')
  return response.data
}

export async function listRazas(especieId: number): Promise<Raza[]> {
  const response = await http.get<Raza[]>('/catalogos/razas', { params: { especieId } })
  return response.data
}