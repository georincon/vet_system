<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { aplicarInmunizacion, listAntiparasitarios, listEsquemas, listVacunas } from '@/api/inmunizacion'
import { listLotes } from '@/api/inventario'
import { listVeterinarios } from '@/api/usuarios'
import { ApiError } from '@/api/client'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaFecha, hoy, parseFecha, sumarDias } from '@/utils/fecha'
import type { VeterinarioOpcion } from '@/types'
import { cant } from '@/utils/dinero'
import type { Lote } from '@/types/fase4'
import type { Antiparasitario, Esquema, EsquemaSugeridoItem, TipoInmunizacion, Vacuna } from '@/types/fase3'

const props = defineProps<{
  modelValue: boolean
  pacienteId: number
  pacienteNombre?: string
  especieId?: number | null
  fechaNacimiento?: string | null
  /** Preselección (por ejemplo desde el esquema sugerido o la pantalla de pendientes). */
  inicial?: { tipo: TipoInmunizacion; productoId?: number | null } | null
  sugeridos?: EsquemaSugeridoItem[]
}>()
const emit = defineEmits<{ 'update:modelValue': [v: boolean]; aplicada: [] }>()

const auth = useAuthStore()
const ui = useUiStore()
const esAuxiliar = computed(() => auth.rol === 'AUXILIAR')

const vacunas = ref<Vacuna[]>([])
const antiparasitarios = ref<Antiparasitario[]>([])
const veterinarios = ref<VeterinarioOpcion[]>([])
const esquema = ref<Esquema | null>(null)
const lotes = ref<Lote[]>([])
const cargandoLotes = ref(false)
const errorLotes = ref('')
const SIN_LOTE = 0
const lotesOpt = computed(() => [
  ...lotes.value.map((l) => ({
    title: `Lote ${l.numeroLote} · vence ${etiquetaFecha(l.fechaVencimiento)}${l.diasParaVencer !== null && l.diasParaVencer !== undefined && l.diasParaVencer <= 30 ? ' (por vencer)' : ''} · stock ${cant(l.cantidadActual)} · ${l.bodega}`,
    value: l.id,
  })),
  { title: 'Continuar sin lote de inventario (no descuenta stock)', value: SIN_LOTE },
])
const sinLote = computed(() => !form.externa && form.loteId === SIN_LOTE)
const loteSel = computed(() => lotes.value.find((l) => l.id === form.loteId) ?? null)

async function cargarLotes() {
  lotes.value = []; errorLotes.value = ''
  if (!form.productoId) return
  cargandoLotes.value = true
  try {
    lotes.value = await listLotes({
      vacunaId: form.tipo === 'VACUNA' ? form.productoId : null,
      antiparasitarioId: form.tipo === 'ANTIPARASITARIO' ? form.productoId : null,
      soloVigentes: true,
    })
    // FEFO: la lista llega ordenada por vencimiento; se sugiere el primero.
    form.loteId = lotes.value[0]?.id ?? null
  } catch (e) { errorLotes.value = errorText(e, 'No se pudieron cargar los lotes') } finally { cargandoLotes.value = false }
}

const form = reactive({
  tipo: 'VACUNA' as TipoInmunizacion,
  productoId: null as number | null,
  fechaAplicacion: hoy(),
  dosisNumero: null as number | null,
  lote: '',
  fechaVencimientoLote: '',
  loteId: null as number | null,
  externa: false,
  veterinarioId: null as number | null,
  sitioAplicacion: '',
  viaAdministracion: '',
  dosis: '',
  pesoKg: null as number | null,
  observaciones: '',
  reaccionAdversa: '',
  proximaFecha: '',
})
const proximaEditada = ref(false)
const guardando = ref(false)
const error = ref('')
const errorStatus = ref<number | undefined>()
const formRef = ref<{ validate: () => Promise<{ valid: boolean }> } | null>(null)

const productos = computed(() =>
  form.tipo === 'VACUNA'
    ? vacunas.value.map((v) => ({ title: `${v.nombre} (${v.codigo})`, value: v.id }))
    : antiparasitarios.value.map((a) => ({ title: `${a.nombre} (${a.tipo.toLowerCase()})`, value: a.id })),
)
const vacunaSel = computed(() => vacunas.value.find((v) => v.id === form.productoId) ?? null)
const antiSel = computed(() => antiparasitarios.value.find((a) => a.id === form.productoId) ?? null)
const sugeridoSel = computed(() =>
  form.tipo === 'VACUNA' ? props.sugeridos?.find((s) => s.vacunaId === form.productoId) ?? null : null,
)

const loteVencido = computed(
  () => sinLote.value && !!form.fechaVencimientoLote && !!form.fechaAplicacion && form.fechaVencimientoLote < form.fechaAplicacion,
)

/** Próxima fecha sugerida (misma regla del backend; el servidor tiene la última palabra). */
const proximaSugerida = computed<string | null>(() => {
  const f = form.fechaAplicacion
  if (!f || !form.productoId) return null
  if (form.tipo === 'ANTIPARASITARIO') return antiSel.value ? sumarDias(f, antiSel.value.intervaloDias) : null
  const v = vacunaSel.value
  if (!v) return null
  const e = esquema.value
  if (!e?.dosis?.length) return sumarDias(f, v.periodoEficaciaDias)
  const n = form.dosisNumero ?? sugeridoSel.value?.proximaDosisNumero ?? (sugeridoSel.value ? sugeridoSel.value.dosisAplicadas + 1 : 1)
  const ordenadas = [...e.dosis].sort((a, b) => a.orden - b.orden)
  const idx = ordenadas.findIndex((d) => d.orden === n)
  const siguiente = idx >= 0 ? ordenadas[idx + 1] : undefined
  if (siguiente) {
    let fecha = sumarDias(f, siguiente.intervaloDiasPrevia ?? 0)
    if (props.fechaNacimiento && siguiente.edadMinimaSemanas) {
      const minimo = sumarDias(props.fechaNacimiento, siguiente.edadMinimaSemanas * 7)
      if (parseFecha(minimo) > parseFecha(fecha)) fecha = minimo
    }
    return fecha
  }
  const actual = idx >= 0 ? ordenadas[idx] : ordenadas[ordenadas.length - 1]
  return actual.repetirCadaDias ? sumarDias(f, actual.repetirCadaDias) : null
})

watch(proximaSugerida, (s) => { if (!proximaEditada.value) form.proximaFecha = s ?? '' })

watch(() => [form.tipo, form.productoId] as const, async ([tipo, id]) => {
  esquema.value = null
  form.loteId = null
  void cargarLotes()
  if (tipo === 'VACUNA' && id) {
    const v = vacunas.value.find((x) => x.id === id)
    if (v?.viaAdministracion && !form.viaAdministracion) form.viaAdministracion = v.viaAdministracion
    try {
      const lista = await listEsquemas({ especieId: props.especieId, vacunaId: id, activo: true })
      esquema.value = lista.find((e) => e.especieId === props.especieId) ?? lista[0] ?? null
    } catch { esquema.value = null }
  }
})

function cambiarTipo(t: TipoInmunizacion) {
  form.tipo = t
  form.productoId = null
  proximaEditada.value = false
  form.proximaFecha = ''
}

async function cargarCatalogos() {
  try {
    const [v, a] = await Promise.all([
      listVacunas({ especieId: props.especieId, activo: true }),
      listAntiparasitarios({ especieId: props.especieId, activo: true }),
    ])
    vacunas.value = v
    antiparasitarios.value = a
    if (props.inicial?.productoId) form.productoId = props.inicial.productoId
  } catch (e) { error.value = errorText(e, 'No se pudo cargar el catálogo') }
  if (veterinarios.value.length === 0) {
    try { veterinarios.value = await listVeterinarios() } catch { /* se mostrará vacío */ }
  }
}

watch(() => props.modelValue, async (open) => {
  if (!open) return
  Object.assign(form, {
    tipo: props.inicial?.tipo ?? 'VACUNA', productoId: null, fechaAplicacion: hoy(), dosisNumero: null, lote: '',
    fechaVencimientoLote: '', loteId: null, externa: false, veterinarioId: null, sitioAplicacion: '',
    viaAdministracion: '', dosis: '', pesoKg: null, observaciones: '', reaccionAdversa: '', proximaFecha: '',
  })
  proximaEditada.value = false
  error.value = ''
  errorStatus.value = undefined
  await cargarCatalogos()
}, { immediate: true })

const tituloError = computed(() => {
  if (errorStatus.value === 409) return 'Stock agotado o conflicto: el lote no tiene existencias suficientes'
  if (errorStatus.value === 422) return 'Regla de negocio: revise lote, fecha o producto'
  return 'No se pudo registrar'
})

async function guardar() {
  const r = await formRef.value?.validate()
  if (!r?.valid) return
  if (esAuxiliar.value && !form.veterinarioId) { error.value = 'Indique el veterinario supervisor'; return }
  guardando.value = true
  error.value = ''
  errorStatus.value = undefined
  try {
    await aplicarInmunizacion(props.pacienteId, {
      tipo: form.tipo,
      vacunaId: form.tipo === 'VACUNA' ? form.productoId : null,
      antiparasitarioId: form.tipo === 'ANTIPARASITARIO' ? form.productoId : null,
      fechaAplicacion: form.fechaAplicacion || undefined,
      dosisNumero: form.tipo === 'VACUNA' ? form.dosisNumero || undefined : undefined,
      loteId: form.externa || sinLote.value ? undefined : form.loteId || undefined,
      lote: form.externa || sinLote.value ? form.lote.trim() || undefined : undefined,
      fechaVencimientoLote: form.externa || sinLote.value ? form.fechaVencimientoLote || undefined : undefined,
      veterinarioId: form.veterinarioId || undefined,
      sitioAplicacion: form.sitioAplicacion.trim() || undefined,
      viaAdministracion: form.viaAdministracion.trim() || undefined,
      dosis: form.tipo === 'ANTIPARASITARIO' ? form.dosis.trim() || undefined : undefined,
      pesoKg: form.pesoKg || undefined,
      observaciones: form.observaciones.trim() || undefined,
      reaccionAdversa: form.reaccionAdversa.trim() || undefined,
      externa: form.externa || undefined,
      proximaFecha: form.proximaFecha || undefined,
    })
    ui.showSuccess(form.tipo === 'VACUNA' ? 'Vacuna registrada' : 'Antiparasitario registrado')
    emit('update:modelValue', false)
    emit('aplicada')
  } catch (e) {
    errorStatus.value = e instanceof ApiError ? e.status : undefined
    error.value = errorText(e, 'No se pudo registrar la aplicación')
    if (errorStatus.value === 409) void cargarLotes()
  } finally {
    guardando.value = false
  }
}
</script>

<template>
  <v-dialog :model-value="modelValue" max-width="760" scrollable @update:model-value="emit('update:modelValue', $event)">
    <v-card>
      <v-card-title class="pt-4">Aplicar vacuna / antiparasitario{{ pacienteNombre ? ` · ${pacienteNombre}` : '' }}</v-card-title>
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :title="tituloError" :text="error" />
        <v-alert v-if="esAuxiliar" type="info" variant="tonal" density="compact" class="mb-3">
          Como auxiliar aplica bajo supervisión: seleccione el veterinario responsable.
        </v-alert>
        <v-form ref="formRef" @submit.prevent="guardar">
          <v-btn-toggle :model-value="form.tipo" mandatory density="comfortable" color="primary" variant="outlined" divided class="mb-4"
            @update:model-value="cambiarTipo">
            <v-btn value="VACUNA" prepend-icon="mdi-needle">Vacuna</v-btn>
            <v-btn value="ANTIPARASITARIO" prepend-icon="mdi-bug-outline">Antiparasitario</v-btn>
          </v-btn-toggle>

          <v-row dense>
            <v-col cols="12" md="8">
              <v-select v-model="form.productoId" :items="productos" :label="form.tipo === 'VACUNA' ? 'Vacuna del catálogo' : 'Antiparasitario del catálogo'"
                :rules="[(v: unknown) => !!v || 'Seleccione un producto']" no-data-text="Sin productos activos para esta especie" />
            </v-col>
            <v-col cols="12" md="4">
              <v-text-field v-model="form.fechaAplicacion" type="date" label="Fecha de aplicación" :max="hoy()" :rules="[(v: unknown) => !!v || 'Obligatoria']" />
            </v-col>
          </v-row>

          <v-row dense>
            <v-col v-if="form.tipo === 'VACUNA'" cols="6" md="3">
              <v-text-field v-model.number="form.dosisNumero" type="number" min="1" label="Dosis nº"
                :placeholder="sugeridoSel?.proximaDosisNumero ? String(sugeridoSel.proximaDosisNumero) : 'automática'" persistent-placeholder
                :hint="sugeridoSel?.proximaDosisNombre ?? 'Vacío = siguiente en la serie'" persistent-hint />
            </v-col>
            <v-col v-else cols="12" md="6">
              <v-text-field v-model="form.dosis" label="Dosis (p. ej. 1 tableta)" />
            </v-col>
            <v-col cols="6" md="3"><v-text-field v-model="form.pesoKg" type="number" step="0.01" label="Peso (kg)" hint="Vacío = peso actual" persistent-hint /></v-col>
            <v-col cols="6" md="3"><v-text-field v-model="form.sitioAplicacion" label="Sitio de aplicación" /></v-col>
            <v-col cols="6" md="3"><v-text-field v-model="form.viaAdministracion" label="Vía" placeholder="SC, IM, VO…" /></v-col>
          </v-row>

          <h3 class="vet-section-title mt-3">Lote</h3>
          <v-switch v-model="form.externa" color="primary" density="compact" hide-details class="mb-2"
            label="Aplicada en otra clínica (no descuenta stock)" />
          <v-select v-if="!form.externa" v-model="form.loteId" :items="lotesOpt" label="Lote de inventario (vigentes, primero el que vence antes)" :loading="cargandoLotes"
            :disabled="!form.productoId" :hint="loteSel ? `Se descontará 1 dosis del lote ${loteSel.numeroLote} (quedan ${cant(loteSel.cantidadActual)})` : form.productoId && !lotes.length && !cargandoLotes ? 'No hay lotes vigentes con stock para este producto' : ''" persistent-hint />
          <v-alert v-if="errorLotes" type="error" variant="tonal" density="compact" class="mt-2" :text="errorLotes" />
          <v-alert v-if="form.productoId && !cargandoLotes && !lotes.length && !form.externa && !errorLotes" type="warning" variant="tonal" density="compact" class="mt-2"
            text="Sin lotes vigentes con existencias. Cárguelos en Inventario (o continúe sin lote: no se descontará stock)." />
          <v-row v-if="form.externa || sinLote" dense class="mt-1">
            <v-col cols="12" md="7"><v-text-field v-model="form.lote" label="Número de lote (opcional)" /></v-col>
            <v-col cols="12" md="5"><v-text-field v-model="form.fechaVencimientoLote" type="date" label="Vence el lote" /></v-col>
          </v-row>
          <v-alert v-if="loteVencido" type="warning" variant="tonal" density="compact" class="mt-2"
            :text="`El lote vence el ${etiquetaFecha(form.fechaVencimientoLote)}, antes de la fecha de aplicación. El sistema lo rechazará.`" />

          <v-row dense class="mt-2">
            <v-col cols="12" md="6">
              <v-select v-model="form.veterinarioId" :items="veterinarios" item-title="nombre" item-value="id"
                :label="esAuxiliar ? 'Veterinario supervisor (obligatorio)' : 'Veterinario'"
                :rules="esAuxiliar ? [(v: unknown) => !!v || 'Indique el veterinario supervisor'] : []"
                :clearable="!esAuxiliar" :hint="esAuxiliar ? '' : 'Vacío = usted (si es veterinario)'" persistent-hint />
            </v-col>
            <v-col cols="12" md="6">
              <v-text-field v-model="form.proximaFecha" type="date" label="Próxima fecha" :min="form.fechaAplicacion" clearable
                :hint="proximaSugerida ? `Sugerida: ${etiquetaFecha(proximaSugerida)}${proximaEditada ? ' (editada)' : ''}` : 'Sin próxima dosis (serie completa)'"
                persistent-hint @update:model-value="proximaEditada = true" />
            </v-col>
          </v-row>

          <v-row dense class="mt-1">
            <v-col cols="12" md="6"><v-textarea v-model="form.observaciones" label="Observaciones" rows="2" auto-grow /></v-col>
            <v-col cols="12" md="6"><v-textarea v-model="form.reaccionAdversa" label="Reacción adversa" rows="2" auto-grow /></v-col>
          </v-row>
        </v-form>
      </v-card-text>
      <v-card-actions class="px-4 pb-4">
        <v-spacer />
        <v-btn variant="text" @click="emit('update:modelValue', false)">Cancelar</v-btn>
        <v-btn color="primary" variant="flat" :loading="guardando" @click="guardar">Registrar aplicación</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
