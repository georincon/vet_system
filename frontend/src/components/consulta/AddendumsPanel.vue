<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { createAddendum, listAddendums } from '@/api/consultas'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import { etiquetaInstante } from '@/utils/fecha'
import type { Addendum, TipoAddendum } from '@/types'

const props = defineProps<{ consultaId: number; canWrite: boolean }>()
const ui = useUiStore()
const TIPOS: { title: string; value: TipoAddendum }[] = [
  { title: 'Aclaración', value: 'ACLARACION' }, { title: 'Corrección', value: 'CORRECCION' },
  { title: 'Resultado tardío', value: 'RESULTADO_TARDIO' }, { title: 'Seguimiento', value: 'SEGUIMIENTO' },
]
const items = ref<Addendum[]>([])
const tipo = ref<TipoAddendum>('ACLARACION')
const texto = ref('')
const saving = ref(false)

async function load() {
  try { items.value = await listAddendums(props.consultaId) } catch (e) { ui.showError(errorText(e, 'No se pudieron cargar los addendums')) }
}
async function agregar() {
  if (!texto.value.trim()) return
  saving.value = true
  try {
    await createAddendum(props.consultaId, tipo.value, texto.value.trim())
    texto.value = ''
    ui.showSuccess('Addendum registrado')
    await load()
  } catch (e) {
    ui.showError(errorText(e, 'No se pudo registrar el addendum'))
  } finally {
    saving.value = false
  }
}
watch(() => props.consultaId, load)
onMounted(load)
</script>

<template>
  <div>
    <v-timeline v-if="items.length" density="compact" side="end" class="mb-2">
      <v-timeline-item v-for="a in items" :key="a.id" size="small" dot-color="primary">
        <div class="font-weight-bold">{{ TIPOS.find((t) => t.value === a.tipo)?.title ?? a.tipo }}
          <span class="text-caption text-medium-emphasis ml-2">{{ a.autor?.nombre }} · {{ etiquetaInstante(a.creadoEn) }}</span></div>
        <div style="white-space: pre-wrap">{{ a.texto }}</div>
      </v-timeline-item>
    </v-timeline>
    <div v-else class="text-medium-emphasis text-body-2 mb-3">Sin addendums. La consulta cerrada no puede modificarse; use un addendum para aclaraciones.</div>
    <div v-if="canWrite" class="d-flex flex-wrap ga-2 align-start">
      <v-select v-model="tipo" :items="TIPOS" label="Tipo" style="max-width: 200px" />
      <v-textarea v-model="texto" label="Texto del addendum" rows="2" auto-grow style="flex: 1 1 320px" />
      <v-btn color="primary" variant="flat" size="large" prepend-icon="mdi-plus" :loading="saving" :disabled="!texto.trim()" @click="agregar">
        Agregar addendum
      </v-btn>
    </div>
  </div>
</template>
