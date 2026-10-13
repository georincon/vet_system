import { defineStore } from 'pinia'
import { nextTick, reactive } from 'vue'

export interface SnackbarState {
  visible: boolean
  text: string
  color: string
  timeout: number
}

export const useUiStore = defineStore('ui', () => {
  const snackbar = reactive<SnackbarState>({
    visible: false,
    text: '',
    color: 'success',
    timeout: 4500,
  })

  async function showSnackbar(
    text: string,
    color: 'success' | 'error' | 'warning' | 'info' = 'success',
    timeout = 4500,
  ) {
    snackbar.visible = false
    snackbar.text = text
    snackbar.color = color
    snackbar.timeout = timeout
    await nextTick()
    snackbar.visible = true
  }

  function showError(text: string, timeout = 7000) {
    void showSnackbar(text, 'error', timeout)
  }

  function showSuccess(text: string) {
    void showSnackbar(text, 'success')
  }

  return { snackbar, showSnackbar, showError, showSuccess }
})