export type Rule = (value: unknown) => boolean | string

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function isEmpty(value: unknown): boolean {
  return value === undefined || value === null || String(value).trim() === ''
}

export const required =
  (message = 'Campo obligatorio'): Rule =>
  (value) =>
    !isEmpty(value) || message

export const email =
  (message = 'Ingrese un correo electrónico válido'): Rule =>
  (value) =>
    isEmpty(value) || EMAIL.test(String(value)) || message

export const minLength =
  (min: number, message?: string): Rule =>
  (value) =>
    isEmpty(value) || String(value).length >= min || (message ?? `Debe tener al menos ${min} caracteres`)

export const password =
  (message = 'La contraseña debe tener al menos 6 caracteres'): Rule =>
  (value) =>
    isEmpty(value) || String(value).length >= 6 || message

export const requiredSelect =
  (message = 'Seleccione una opción'): Rule =>
  (value) =>
    value !== undefined && value !== null && value !== '' || message

export const formRules = {
  required,
  email,
  minLength,
  password,
  requiredSelect,
}