<script setup lang="ts">
import type { EsquemaSugeridoItem, EsquemaSugerido, EstadoEsquema } from '@/types/fase3'
import { etiquetaFecha } from '@/utils/fecha'

defineProps<{ esquema: EsquemaSugerido | null; loading?: boolean; puedeAplicar?: boolean }>()
const emit = defineEmits<{ aplicar: [item: EsquemaSugeridoItem] }>()

const META: Record<EstadoEsquema, { text: string; color: string; icon: string }> = {
  VENCIDA: { text: 'Vencida', color: 'error', icon: 'mdi-alert-circle-outline' },
  TOCA_AHORA: { text: 'Toca ahora', color: 'warning', icon: 'mdi-clock-alert-outline' },
  PROXIMA: { text: 'Próxima', color: 'info', icon: 'mdi-calendar-clock' },
  AL_DIA: { text: 'Al día', color: 'success', icon: 'mdi-check-circle-outline' },
  COMPLETA: { text: 'Serie completa', color: 'success', icon: 'mdi-check-all' },
  FUTURA: { text: 'Futura', color: 'grey', icon: 'mdi-timer-sand' },
}
const aplicable = (e: EstadoEsquema) => e === 'VENCIDA' || e === 'TOCA_AHORA' || e === 'PROXIMA'
</script>

<template>
  <div>
    <div class="d-flex align-center flex-wrap ga-2 mb-2">
      <h3 class="vet-section-title flex-grow-1 mb-0">Esquema sugerido</h3>
      <span v-if="esquema" class="text-caption text-medium-emphasis">
        {{ esquema.especie }}<template v-if="esquema.edadSemanas != null"> · {{ esquema.edadSemanas }} semanas de edad</template>
      </span>
    </div>
    <v-progress-linear v-if="loading" indeterminate color="primary" />
    <v-table v-if="esquema?.items.length" density="comfortable">
      <thead>
        <tr><th>Vacuna</th><th>Esquema</th><th>Aplicadas</th><th>Próxima dosis</th><th>Fecha sugerida</th><th>Estado</th><th v-if="puedeAplicar" /></tr>
      </thead>
      <tbody>
        <tr v-for="it in esquema.items" :key="it.esquemaId">
          <td>
            <strong>{{ it.vacuna }}</strong>
            <v-chip v-if="it.clasificacion === 'NUCLEO'" size="x-small" class="ml-1" color="primary" variant="tonal">Núcleo</v-chip>
            <v-chip v-if="it.obligatoriaLey" size="x-small" class="ml-1" color="error" variant="tonal">Obligatoria</v-chip>
          </td>
          <td class="text-medium-emphasis">{{ it.esquema }}</td>
          <td>{{ it.dosisAplicadas }}<span v-if="it.ultimaAplicacion" class="text-caption text-medium-emphasis"> (últ. {{ etiquetaFecha(it.ultimaAplicacion) }})</span></td>
          <td>{{ it.proximaDosisNombre ?? (it.proximaDosisNumero ? `Dosis ${it.proximaDosisNumero}` : '—') }}</td>
          <td>{{ etiquetaFecha(it.fechaSugerida) }}</td>
          <td><v-chip size="small" variant="tonal" :color="META[it.estado]?.color" :prepend-icon="META[it.estado]?.icon" :text="META[it.estado]?.text ?? it.estado" /></td>
          <td v-if="puedeAplicar" class="text-end">
            <v-btn v-if="aplicable(it.estado)" size="small" variant="tonal" color="primary" prepend-icon="mdi-needle" @click="emit('aplicar', it)">Aplicar</v-btn>
          </td>
        </tr>
      </tbody>
    </v-table>
    <div v-else-if="!loading" class="text-medium-emphasis py-4 text-center">No hay esquemas activos para la especie del paciente.</div>
  </div>
</template>
