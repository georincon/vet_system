import { http } from './client'
import type { Page, Usuario, UsuarioRequest, VeterinarioOpcion } from '@/types'

export async function listUsuarios(q?: string, page = 0, size = 20): Promise<Page<Usuario>> {
  const response = await http.get<Page<Usuario>>('/usuarios', { params: { q, page, size } })
  return response.data
}

export async function createUsuario(data: UsuarioRequest): Promise<Usuario> {
  const response = await http.post<Usuario>('/usuarios', data)
  return response.data
}

/** Veterinarios activos para desplegables (accesible a todos los roles). */
export async function listVeterinarios(): Promise<VeterinarioOpcion[]> {
  const response = await http.get<VeterinarioOpcion[]>('/veterinarios')
  return response.data
}
