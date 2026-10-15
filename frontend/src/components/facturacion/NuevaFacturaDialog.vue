<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { crearFactura } from '@/api/facturacion'
import { ApiError } from '@/api/client'
import PacienteSelect from '@/components/agenda/PacienteSelect.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { aLineaBody } from '@/utils/lineas'
import LineasEditor, { type LineaUI } from './LineasEditor.vue'
import FacturarDialog, { type FacturarDatos } from './FacturarDialog.vue'
import TutorSelect from './TutorSelect.vue'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; creada: [id: number]; abrirCaja: [] ; cambio: [] }>()
const auth = useAuthStore()
const ui = useUiStore()
const lineas = ref<LineaUI[]>([])
const tutorId = ref<number | null>(null)
const pacienteId = ref<number | null>(null)
const editor = ref<InstanceType<typeof LineasEditor> | null>(null)
const facturar = ref(false)
const enviando = ref(false)
const error = ref('')
const faltantes = ref<number[]>([])
const total = computed(() => editor.value?.totales.total ?? 0)

watch(() => props.modelValue, (o) => { if (o) { lineas.value = []; tutorId.value = null; pacienteId.value = null; error.value = ''; faltantes.value = [] } })
function continuar() {
  error.value = ''
  if (!tutorId.value) { error.value = 'Seleccione el tutor de la factura'; return }
  if (!lineas.value.length) { error.value = 'Agregue al menos un renglón'; return }
  facturar.value = true
}
async function emitir(d: FacturarDatos) {
  enviando.value = true; error.value = ''; faltantes.value = []
  try {
    const f = await crearFactura({
      tutorId: tutorId.value, pacienteId: pacienteId.value ?? undefined, condicionPago: d.condicionPago, fechaVencimiento: d.fechaVencimiento, notas: d.notas,
      items: lineas.value.map((l) => aLineaBody(l, auth.isAdmin)), pagos: d.pagos,
    })
    ui.showSuccess(`Factura ${f.numero} emitida`)
    facturar.value = false
    emit('update:modelValue', false); emit('cambio'); emit('creada', f.id)
  } catch (e) {
    error.value = errorText(e, 'No se pudo emitir la factura')
    if (e instanceof ApiError && e.status === 409 && /stock/i.test(error.value)) {
      const nombre = /de (.+?): disponible/i.exec(error.value)?.[1]
      const l = lineas.value.find((x) => x.descripcion === nombre)
      if (l?.itemId) faltantes.value = [l.itemId]
    }
  } finally { enviando.value = false }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="1000" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">Nueva factura</v-card-title>
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-row dense>
          <v-col cols="12" md="6"><TutorSelect v-model="tutorId" label="Tutor" density="comfortable" :clearable="false" /></v-col>
          <v-col cols="12" md="6"><PacienteSelect v-model="pacienteId" label="Paciente (opcional)" /></v-col>
        </v-row>
        <h3 class="vet-section-title mt-2">Renglones</h3>
        <LineasEditor ref="editor" v-model="lineas" :faltantes="faltantes" />
      </v-card-text>
      <v-card-actions class="px-4 pb-4"><v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn color="primary" variant="flat" prepend-icon="mdi-receipt-text-outline" @click="continuar">Continuar</v-btn>
      </v-card-actions>
    </v-card>
    <FacturarDialog v-model="facturar" :total="total" :enviando="enviando" :error="error" @confirmar="emitir" @abrir-caja="emit('abrirCaja'); emit('update:modelValue', false)" />
  </v-dialog>
</template>
