import { ApiError } from '@/api/client'

export function errorText(error: unknown, fallback = 'La operación no se pudo completar'): string {
  if (error instanceof ApiError && error.message) return error.message
  if (error instanceof Error && error.message) return error.message
  return fallback
}