<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { estadoPresupuesto, facturarPresupuesto, getPresupuesto, savePresupuesto } from '@/api/facturacion'
import PacienteSelect from '@/components/agenda/PacienteSelect.vue'
import EstadoChip from '@/components/EstadoChip.vue'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { ApiError } from '@/api/client'
import { aLineaBody, aLineaUI } from '@/utils/lineas'
import { etiquetaFecha } from '@/utils/fecha'
import type { Presupuesto } from '@/types/fase4'
import FacturarDialog, { type FacturarDatos } from './FacturarDialog.vue'
import LineasEditor, { type LineaUI } from './LineasEditor.vue'
import TutorSelect from './TutorSelect.vue'

const props = defineProps<{ modelValue: boolean; presupuestoId: number | null }>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; cambio: []; facturada: [id: number]; abrirCaja: [] }>()
const auth = useAuthStore()
const ui = useUiStore()

const p = ref<Presupuesto | null>(null)
const lineas = ref<LineaUI[]>([])
const form = reactive({ tutorId: null as number | null, pacienteId: null as number | null, vigenteHasta: '', notas: '' })
const loading = ref(false)
const guardando = ref(false)
const error = ref('')
const facturar = ref(false)
const facturando = ref(false)
const errFactura = ref('')
const faltantes = ref<number[]>([])

const nuevo = computed(() => !props.presupuestoId)
const editable = computed(() => auth.can.facturacionOperar && (nuevo.value || p.value?.estado === 'BORRADOR'))
const puedeFacturar = computed(() => auth.can.facturacionOperar && !!p.value && ['BORRADOR', 'ENVIADO', 'ACEPTADO'].includes(p.value.estado))
const editor = ref<InstanceType<typeof LineasEditor> | null>(null)
const total = computed(() => editor.value?.totales.total ?? p.value?.total ?? 0)
const consumidor = computed(() => /consumidor/i.test(p.value?.tutor.nombre ?? ''))

function pintar(x: Presupuesto) {
  p.value = x
  lineas.value = x.items.map(aLineaUI)
  Object.assign(form, { tutorId: x.tutor.id, pacienteId: x.pacienteId ?? null, vigenteHasta: x.vigenteHasta ?? '', notas: x.notas ?? '' })
}
async function cargar() {
  if (!props.presupuestoId) return
  loading.value = true
  try { pintar(await getPresupuesto(props.presupuestoId)) } catch (e) { error.value = errorText(e, 'No se pudo cargar el presupuesto') } finally { loading.value = false }
}
watch(() => [props.modelValue, props.presupuestoId] as const, ([o]) => {
  if (!o) return
  error.value = ''; errFactura.value = ''; faltantes.value = []; p.value = null; lineas.value = []
  Object.assign(form, { tutorId: null, pacienteId: null, vigenteHasta: '', notas: '' })
  void cargar()
})

async function guardar(silencioso = false): Promise<boolean> {
  error.value = ''
  if (!form.tutorId) { error.value = 'Seleccione el tutor'; return false }
  if (!lineas.value.length) { error.value = 'Agregue al menos un renglón'; return false }
  guardando.value = true
  try {
    const g = await savePresupuesto(p.value?.id ?? null, {
      tutorId: form.tutorId, pacienteId: form.pacienteId, vigenteHasta: form.vigenteHasta || null, notas: form.notas.trim(),
      consultaId: p.value?.consultaId ?? null, items: lineas.value.map((l) => aLineaBody(l, auth.isAdmin)),
    })
    pintar(g)
    if (!silencioso) ui.showSuccess(`Presupuesto ${g.codigo} guardado`)
    emit('cambio')
    return true
  } catch (e) { error.value = errorText(e, 'No se pudo guardar el presupuesto'); return false } finally { guardando.value = false }
}
async function cambiarEstado(estado: 'ENVIADO' | 'ACEPTADO' | 'RECHAZADO') {
  if (!p.value) return
  try { pintar(await estadoPresupuesto(p.value.id, estado)); ui.showSuccess('Estado actualizado'); emit('cambio') } catch (e) { error.value = errorText(e, 'No se pudo cambiar el estado') }
}
async function abrirFacturar() {
  if (editable.value && !nuevo.value && !(await guardar(true))) return
  if (nuevo.value) { if (!(await guardar(true))) return }
  errFactura.value = ''; facturar.value = true
}
async function confirmarFactura(d: FacturarDatos) {
  if (!p.value) return
  facturando.value = true; errFactura.value = ''; faltantes.value = []
  try {
    const f = await facturarPresupuesto(p.value.id, { condicionPago: d.condicionPago, fechaVencimiento: d.fechaVencimiento, notas: d.notas, pagos: d.pagos })
    facturar.value = false
    ui.showSuccess(`Factura ${f.numero} emitida`)
    emit('cambio'); emit('update:modelValue', false); emit('facturada', f.id)
  } catch (e) {
    errFactura.value = errorText(e, 'No se pudo facturar')
    if (e instanceof ApiError && e.status === 409 && /stock/i.test(errFactura.value)) {
      const nombre = /de (.+?): disponible/i.exec(errFactura.value)?.[1]
      const l = lineas.value.find((x) => x.descripcion === nombre)
      if (l?.itemId) faltantes.value = [l.itemId]
      errFactura.value += ' Corrija la cantidad o reponga el stock; no se emitió nada.'
    }
  } finally { facturando.value = false }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="1000" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4 d-flex flex-wrap align-center ga-2">
        <span>{{ p ? p.codigo : 'Nuevo presupuesto' }}</span>
        <EstadoChip v-if="p" :estado="p.estado" />
        <v-chip v-if="p?.origen === 'CONSULTA'" size="small" variant="tonal" color="info" prepend-icon="mdi-stethoscope" text="Generado desde consulta" />
        <v-spacer />
        <v-btn icon="mdi-close" variant="text" size="small" aria-label="Cerrar" @click="emit('update:modelValue', false)" />
      </v-card-title>
      <v-card-text>
        <v-progress-linear v-if="loading" indeterminate class="mb-3" />
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-alert v-if="errFactura && !facturar" type="error" variant="tonal" density="compact" class="mb-3" :text="errFactura" />
        <v-alert v-if="p && !editable" type="info" variant="tonal" density="compact" class="mb-3"
          :text="p.estado === 'FACTURADO' ? 'Presupuesto facturado: ya no puede modificarse.' : auth.can.facturacionOperar ? 'Solo los borradores son editables.' : 'Vista de solo lectura de su consulta.'" />
        <v-row dense>
          <v-col cols="12" md="5">
            <TutorSelect v-if="editable" v-model="form.tutorId" label="Tutor" density="comfortable" :clearable="false" :rules="[(v: unknown) => !!v || 'Obligatorio']" />
            <div v-else class="vet-info-bar"><strong>{{ p?.tutor.nombre }}</strong><div class="text-caption">{{ p?.tutor.documento }}</div></div>
          </v-col>
          <v-col cols="12" md="4">
            <PacienteSelect v-if="editable" v-model="form.pacienteId" label="Paciente (opcional)" />
            <div v-else class="vet-info-bar">Paciente: {{ p?.paciente ?? '—' }}</div>
          </v-col>
          <v-col cols="12" md="3">
            <v-text-field v-if="editable" v-model="form.vigenteHasta" type="date" label="Vigente hasta" />
            <div v-else class="vet-info-bar">Vigente hasta {{ etiquetaFecha(p?.vigenteHasta) }}</div>
          </v-col>
        </v-row>
        <h3 class="vet-section-title mt-2">Renglones</h3>
        <LineasEditor ref="editor" v-model="lineas" :readonly="!editable" :faltantes="faltantes" />
        <v-textarea v-if="editable" v-model="form.notas" label="Notas" rows="2" auto-grow class="mt-3" />
        <p v-else-if="p?.notas" class="mt-3 text-body-2">{{ p.notas }}</p>
        <p v-if="p?.facturaId" class="mt-3"><v-chip size="small" color="success" variant="tonal" prepend-icon="mdi-receipt-text-check-outline">Facturado</v-chip></p>
      </v-card-text>
      <v-card-actions class="px-4 pb-4 flex-wrap ga-1">
        <template v-if="p && auth.can.facturacionOperar && ['BORRADOR', 'ENVIADO'].includes(p.estado)">
          <v-btn v-if="p.estado === 'BORRADOR'" variant="text" prepend-icon="mdi-send-outline" @click="cambiarEstado('ENVIADO')">Marcar enviado</v-btn>
          <v-btn variant="text" prepend-icon="mdi-check" @click="cambiarEstado('ACEPTADO')">Aceptado</v-btn>
          <v-btn variant="text" color="error" prepend-icon="mdi-close" @click="cambiarEstado('RECHAZADO')">Rechazado</v-btn>
        </template>
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cerrar</v-btn>
        <v-btn v-if="editable" variant="tonal" prepend-icon="mdi-content-save-outline" :loading="guardando" @click="guardar()">Guardar</v-btn>
        <v-btn v-if="puedeFacturar || (nuevo && editable)" color="primary" variant="flat" prepend-icon="mdi-receipt-text-outline" @click="abrirFacturar">Facturar</v-btn>
      </v-card-actions>
    </v-card>
    <FacturarDialog v-model="facturar" :total="total" :tutor-es-consumidor-final="consumidor" :enviando="facturando" :error="errFactura"
      @confirmar="confirmarFactura" @abrir-caja="emit('abrirCaja'); emit('update:modelValue', false)" />
  </v-dialog>
</template>
