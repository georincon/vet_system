<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { registrarMonitoreo } from '@/api/procedimientos'
import { useUiStore } from '@/stores/ui'
import type { MonitoreoAnestesico, Procedimiento } from '@/types/etapa'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'

const props = defineProps<{ procedimiento: Procedimiento; puedeRegistrar: boolean }>()
const emit = defineEmits<{ cambio: [] }>()
const ui = useUiStore()

const bloqueado = computed(() => ['CANCELADA', 'APLAZADA'].includes(props.procedimiento.estado))
const habilitado = computed(() => props.puedeRegistrar && !bloqueado.value)

const num = () => null as number | null
const form = reactive({
  registradoEn: '', frecuenciaCardiaca: num(), frecuenciaRespiratoria: num(), spo2: num(), etco2: num(),
  presionSistolica: num(), temperatura: num(), agenteInhalatorioPct: num(), fluidoMlH: num(), notas: '',
})
const error = ref('')
const guardando = ref(false)

const ordenados = computed(() => [...props.procedimiento.monitoreo].sort((a, b) => a.registradoEn.localeCompare(b.registradoEn)))

const fueraSpo2 = (m: MonitoreoAnestesico) => m.spo2 != null && m.spo2 < 95
const fueraTemp = (m: MonitoreoAnestesico) => m.temperatura != null && (m.temperatura < 36 || m.temperatura > 39.5)

function limpiar() {
  Object.assign(form, {
    registradoEn: '', frecuenciaCardiaca: null, frecuenciaRespiratoria: null, spo2: null, etco2: null,
    presionSistolica: null, temperatura: null, agenteInhalatorioPct: null, fluidoMlH: null, notas: '',
  })
}

async function registrar() {
  error.value = ''
  const hayParametro = [form.frecuenciaCardiaca, form.frecuenciaRespiratoria, form.spo2, form.etco2, form.presionSistolica,
    form.temperatura, form.agenteInhalatorioPct, form.fluidoMlH].some((v) => v !== null && (v as unknown) !== '')
  if (!hayParametro) { error.value = 'Registre al menos un parámetro'; return }
  guardando.value = true
  try {
    const n = (v: number | null) => (v === null || (v as unknown) === '' ? null : Number(v))
    await registrarMonitoreo(props.procedimiento.id, {
      registradoEn: form.registradoEn ? (form.registradoEn.length === 16 ? `${form.registradoEn}:00` : form.registradoEn) : null,
      frecuenciaCardiaca: n(form.frecuenciaCardiaca), frecuenciaRespiratoria: n(form.frecuenciaRespiratoria), spo2: n(form.spo2),
      etco2: n(form.etco2), presionSistolica: n(form.presionSistolica), temperatura: n(form.temperatura),
      agenteInhalatorioPct: n(form.agenteInhalatorioPct), fluidoMlH: n(form.fluidoMlH), notas: form.notas.trim() || null,
    })
    limpiar()
    ui.showSuccess('Registro de monitoreo guardado')
    emit('cambio')
  } catch (e) {
    error.value = errorText(e, 'No se pudo registrar el monitoreo')
  } finally {
    guardando.value = false
  }
}

// Mini gráfico SVG de FC y SpO2
const W = 560
const H = 150
const PAD = 28
function serie(campo: 'frecuenciaCardiaca' | 'spo2', min: number, max: number): string {
  const pts = ordenados.value.filter((m) => m[campo] != null)
  if (pts.length < 2) return ''
  return pts.map((m, i) => {
    const x = PAD + (i * (W - 2 * PAD)) / (pts.length - 1)
    const v = Math.min(max, Math.max(min, m[campo] as number))
    const y = H - PAD - ((v - min) / (max - min)) * (H - 2 * PAD)
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
}
const lineaFc = computed(() => serie('frecuenciaCardiaca', 40, 200))
const lineaSpo2 = computed(() => serie('spo2', 80, 100))
</script>

<template>
  <div>
    <h2 class="vet-section-title">Monitoreo anestésico</h2>
    <v-alert v-if="bloqueado" type="info" variant="tonal" density="compact" class="mb-3"
      text="El procedimiento está cancelado o aplazado: no se admiten nuevos registros." />

    <v-card v-if="habilitado" border elevation="0" class="mb-3">
      <v-card-text>
        <v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />
        <v-row dense>
          <v-col cols="12" md="3"><v-text-field v-model="form.registradoEn" label="Hora (vacío = ahora)" type="datetime-local" /></v-col>
          <v-col cols="4" md="1"><v-text-field v-model.number="form.frecuenciaCardiaca" label="FC" type="number" min="0" max="400" /></v-col>
          <v-col cols="4" md="1"><v-text-field v-model.number="form.frecuenciaRespiratoria" label="FR" type="number" min="0" max="200" /></v-col>
          <v-col cols="4" md="1"><v-text-field v-model.number="form.spo2" label="SpO2 %" type="number" min="0" max="100" /></v-col>
          <v-col cols="4" md="1"><v-text-field v-model.number="form.etco2" label="EtCO2" type="number" min="0" max="150" /></v-col>
          <v-col cols="4" md="1"><v-text-field v-model.number="form.presionSistolica" label="PAS" type="number" min="0" max="400" /></v-col>
          <v-col cols="4" md="1"><v-text-field v-model.number="form.temperatura" label="T °C" type="number" step="0.1" min="20" max="45" /></v-col>
          <v-col cols="6" md="2"><v-text-field v-model.number="form.agenteInhalatorioPct" label="Agente %" type="number" step="0.1" min="0" max="9.9" /></v-col>
          <v-col cols="6" md="2"><v-text-field v-model.number="form.fluidoMlH" label="Fluidos mL/h" type="number" min="0" /></v-col>
          <v-col cols="12" md="8"><v-text-field v-model="form.notas" label="Notas" maxlength="500" /></v-col>
          <v-col cols="12" md="2" class="d-flex align-center"><v-btn color="primary" variant="flat" prepend-icon="mdi-plus" :loading="guardando" @click="registrar">Registrar</v-btn></v-col>
        </v-row>
      </v-card-text>
    </v-card>

    <div v-if="ordenados.length" class="vet-tabla-resultados">
      <v-table density="compact" class="vet-proc-tabla">
        <thead><tr><th>Hora</th><th>FC</th><th>FR</th><th>SpO2 %</th><th>EtCO2</th><th>PAS</th><th>T °C</th><th>Agente %</th><th>mL/h</th><th>Notas</th><th>Registró</th></tr></thead>
        <tbody>
          <tr v-for="m in ordenados" :key="m.id">
            <td>{{ etiquetaInstante(m.registradoEn) }}</td>
            <td>{{ m.frecuenciaCardiaca ?? '—' }}</td>
            <td>{{ m.frecuenciaRespiratoria ?? '—' }}</td>
            <td :class="{ 'vet-proc-fuera': fueraSpo2(m) }">
              <v-icon v-if="fueraSpo2(m)" icon="mdi-alert" size="small" aria-label="SpO2 baja" />{{ m.spo2 ?? '—' }}
            </td>
            <td>{{ m.etco2 ?? '—' }}</td>
            <td>{{ m.presionSistolica ?? '—' }}</td>
            <td :class="{ 'vet-proc-fuera': fueraTemp(m) }">
              <v-icon v-if="fueraTemp(m)" icon="mdi-alert" size="small" aria-label="Temperatura fuera de rango" />{{ m.temperatura ?? '—' }}
            </td>
            <td>{{ m.agenteInhalatorioPct ?? '—' }}</td>
            <td>{{ m.fluidoMlH ?? '—' }}</td>
            <td>{{ m.notas ?? '' }}</td>
            <td>{{ m.registradoPor?.nombre ?? '—' }}</td>
          </tr>
        </tbody>
      </v-table>
      <div class="text-caption text-medium-emphasis mt-1">Resaltado orientativo: SpO2 &lt; 95 % y temperatura &lt; 36 o &gt; 39,5 °C.</div>
    </div>
    <div v-else class="text-medium-emphasis text-body-2">Sin registros de monitoreo.</div>

    <div v-if="lineaFc || lineaSpo2" class="vet-grafico mt-3">
      <svg :viewBox="`0 0 ${W} ${H}`" class="vet-proc-grafico" role="img" aria-label="Gráfico de frecuencia cardiaca y SpO2 en el tiempo">
        <line class="vet-eje" :x1="PAD" :y1="H - PAD" :x2="W - PAD" :y2="H - PAD" />
        <line class="vet-eje" :x1="PAD" :y1="PAD" :x2="PAD" :y2="H - PAD" />
        <polyline v-if="lineaFc" class="vet-linea-tendencia" :points="lineaFc" />
        <polyline v-if="lineaSpo2" class="vet-linea-tendencia vet-proc-spo2" :points="lineaSpo2" />
        <text class="vet-eje-texto" :x="PAD" :y="12">FC (azul, 40–200) · SpO2 (verde, 80–100)</text>
      </svg>
    </div>
  </div>
</template>
