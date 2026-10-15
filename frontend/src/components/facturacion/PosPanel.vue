<script setup lang="ts">
import { computed, ref } from 'vue'
import { ventaPos } from '@/api/facturacion'
import { ApiError } from '@/api/client'
import PacienteSelect from '@/components/agenda/PacienteSelect.vue'
import { useAuthStore } from '@/stores/auth'
import { useCajaStore } from '@/stores/caja'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { cop } from '@/utils/dinero'
import { aLineaBody } from '@/utils/lineas'
import type { PagoInput } from '@/types/fase4'
import LineasEditor, { type LineaUI } from './LineasEditor.vue'
import PagosEditor from './PagosEditor.vue'
import SinTurnoAviso from './SinTurnoAviso.vue'
import TutorSelect from './TutorSelect.vue'

const emit = defineEmits<{ abrirCaja: []; verFactura: [id: number] }>()
const auth = useAuthStore()
const caja = useCajaStore()
const ui = useUiStore()

const lineas = ref<LineaUI[]>([])
const pagos = ref<PagoInput[]>([])
const tutorId = ref<number | null>(null)
const pacienteId = ref<number | null>(null)
const notas = ref('')
const editor = ref<InstanceType<typeof LineasEditor> | null>(null)
const pagosRef = ref<InstanceType<typeof PagosEditor> | null>(null)
const enviando = ref(false)
const error = ref('')
const stockError = ref('')
const faltantes = ref<number[]>([])
const ultima = ref<{ id: number; numero: string; total: number } | null>(null)

const total = computed(() => editor.value?.totales.total ?? 0)
const puedeCobrar = computed(() => caja.abierto && lineas.value.length > 0 && lineas.value.every((l) => l.cantidad > 0) && pagosRef.value?.cuadra && pagos.value.length > 0)

async function cobrar() {
  error.value = ''; stockError.value = ''; faltantes.value = []
  enviando.value = true
  try {
    const f = await ventaPos({
      tutorId: tutorId.value ?? undefined, pacienteId: tutorId.value ? pacienteId.value ?? undefined : undefined, notas: notas.value.trim() || undefined,
      items: lineas.value.map((l) => aLineaBody(l, auth.isAdmin)),
      pagos: pagos.value.filter((p) => p.monto > 0).map((p) => ({ metodo: p.metodo, monto: Number(p.monto), referencia: p.referencia?.trim() || undefined })),
    })
    ultima.value = { id: f.id, numero: f.numero, total: f.total }
    ui.showSuccess(`Venta ${f.numero} registrada`)
    lineas.value = []; pagos.value = []; tutorId.value = null; pacienteId.value = null; notas.value = ''
    void caja.refrescar()
  } catch (e) {
    const msg = errorText(e, 'No se pudo registrar la venta')
    if (e instanceof ApiError && e.status === 409 && /stock/i.test(msg)) {
      // "Stock insuficiente de <ítem>: disponible X, solicitado Y"
      stockError.value = msg
      const nombre = /de (.+?): disponible/i.exec(msg)?.[1]
      const l = lineas.value.find((x) => x.descripcion === nombre)
      if (l?.itemId) faltantes.value = [l.itemId]
    } else if (e instanceof ApiError && e.status === 422 && /turno/i.test(msg)) {
      error.value = `${msg}. Abra su turno de caja.`
      void caja.refrescar()
    } else error.value = msg
  } finally { enviando.value = false }
}
</script>

<template>
  <div>
    <SinTurnoAviso v-if="caja.cargado && !caja.abierto" texto="La venta de mostrador registra pagos y exige un turno de caja abierto." @abrir="emit('abrirCaja')" />
    <v-alert v-if="ultima" type="success" variant="tonal" class="mb-4" closable @click:close="ultima = null">
      Venta <strong>{{ ultima.numero }}</strong> por {{ cop(ultima.total) }} registrada.
      <v-btn size="small" variant="text" @click="emit('verFactura', ultima.id)">Ver comprobante</v-btn>
    </v-alert>
    <v-alert v-if="stockError" type="error" variant="tonal" class="mb-4" icon="mdi-package-variant-remove" title="Stock insuficiente" closable @click:close="stockError = ''">
      {{ stockError }}. No se registró nada: reduzca la cantidad o reponga el stock antes de reintentar.
    </v-alert>
    <v-alert v-if="error" type="error" variant="tonal" class="mb-4" :text="error" closable @click:close="error = ''" />

    <v-row>
      <v-col cols="12" md="6"><TutorSelect v-model="tutorId" label="Tutor (opcional)" placeholder="Consumidor final" density="comfortable" hide-details /></v-col>
      <v-col cols="12" md="6"><PacienteSelect v-model="pacienteId" label="Paciente (opcional)" :disabled="!tutorId" /></v-col>
    </v-row>
    <h3 class="vet-section-title mt-2">Carrito</h3>
    <LineasEditor ref="editor" v-model="lineas" :faltantes="faltantes" />
    <div class="mt-4">
      <PagosEditor ref="pagosRef" v-model="pagos" :total="total" />
    </div>
    <v-text-field v-model="notas" label="Notas (opcional)" density="comfortable" hide-details class="mt-4" />
    <div class="d-flex justify-end mt-4 ga-2">
      <v-btn variant="text" :disabled="!lineas.length" @click="lineas = []; pagos = []">Vaciar</v-btn>
      <v-btn color="primary" variant="flat" size="large" prepend-icon="mdi-cash-check" :disabled="!puedeCobrar" :loading="enviando" @click="cobrar">Cobrar {{ cop(total) }}</v-btn>
    </div>
  </div>
</template>
