import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import type { ApiErrorBody } from '@/types'
import { useUiStore } from '@/stores/ui'

export class ApiError extends Error {
  status?: number
  path?: string
  fieldErrors?: string[]

  constructor(message: string, status?: number, path?: string, fieldErrors?: string[]) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.path = path
    this.fieldErrors = fieldErrors
  }
}

const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL || '/api'

export const http = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
})

const FALLBACK_MESSAGES: Record<number, string> = {
  400: 'Solicitud inválida',
  401: 'No autorizado',
  403: 'No tiene permisos',
  404: 'Recurso no encontrado',
  409: 'Conflicto con el estado actual del recurso',
  422: 'La solicitud viola una regla de negocio',
  429: 'Demasiadas solicitudes, intente más tarde',
}

function normalizeFieldErrors(fieldErrors?: unknown[]): string[] | undefined {
  if (!Array.isArray(fieldErrors) || fieldErrors.length === 0) return undefined
  const normalized = fieldErrors.map((entry) => {
    if (typeof entry === 'string') return entry
    if (entry && typeof entry === 'object') {
      const obj = entry as Record<string, unknown>
      const field = obj.field ?? obj.fieldName ?? obj.propertyPath
      const message = obj.message ?? obj.defaultMessage ?? obj.msg
      if (field && message) return `${String(field)}: ${String(message)}`
      if (message) return String(message)
      return JSON.stringify(entry)
    }
    return String(entry)
  })
  return normalized.length > 0 ? normalized : undefined
}

function messageFromBody(body?: ApiErrorBody, status?: number): string | undefined {
  if (body?.message && String(body.message).trim() !== '') return body.message
  if (status !== undefined && FALLBACK_MESSAGES[status]) return FALLBACK_MESSAGES[status]
  return undefined
}

function toApiError(error: AxiosError<ApiErrorBody>): ApiError {
  const status = error.response?.status
  const body = error.response?.data
  const fieldErrors = normalizeFieldErrors(body?.fieldErrors)
  let message = messageFromBody(body, status)
  if (!message) {
    if (error.request) {
      message = 'No se pudo conectar con el servidor'
    } else {
      message = error.message || 'Error inesperado'
    }
  }
  if (fieldErrors && fieldErrors.length > 0) {
    message = `${message}: ${fieldErrors.join(' · ')}`
  }
  return new ApiError(message, status, body?.path ?? error.config?.url, fieldErrors)
}

function isLoginRequest(config?: AxiosRequestConfig): boolean {
  const url = config?.url ?? ''
  return url.includes('/auth/login')
}

async function handleUnauthorized(): Promise<void> {
  const [{ useAuthStore }, { router }] = await Promise.all([
    import('@/stores/auth'),
    import('@/router'),
  ])
  const auth = useAuthStore()
  const current = router.currentRoute.value
  auth.logout()
  useUiStore().showSnackbar('La sesión expiró o no es válida, inicie sesión nuevamente', 'warning')
  if (current.path !== '/login') {
    const redirect = current.fullPath && current.fullPath !== '/' ? current.fullPath : undefined
    await router.push({ path: '/login', query: redirect ? { redirect } : {} })
  }
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('vet_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error)) {
      const ui = useUiStore()
      const unexpected = new ApiError(error instanceof Error ? error.message : 'Error inesperado')
      ui.showError(unexpected.message)
      return Promise.reject(unexpected)
    }

    const status = error.response?.status

    if (status === 401 && !isLoginRequest(error.config)) {
      await handleUnauthorized()
      return Promise.reject(toApiError(error))
    }

    const apiError = toApiError(error)

    if (status === 403) {
      useUiStore().showError('No tiene permisos')
    } else if (!error.response) {
      useUiStore().showError(apiError.message)
    }

    return Promise.reject(apiError)
  },
)