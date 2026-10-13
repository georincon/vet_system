import { http } from './client'
import type { LoginResponse, Usuario } from '@/types'

export async function login(username: string, password: string): Promise<LoginResponse> {
  const response = await http.post<LoginResponse>('/auth/login', { username, password })
  return response.data
}

export async function fetchMe(): Promise<Usuario> {
  const response = await http.get<Usuario>('/auth/me')
  return response.data
}