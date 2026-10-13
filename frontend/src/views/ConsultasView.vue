<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { onMounted } from 'vue'
import { createConsulta, listConsultas, updateConsulta } from '@/api/consultas'
import { listPacientes } from '@/api/pacientes'
import { listUsuarios } from '@/api/usuarios'
import { useUiStore } from '@/stores/ui'
import { errorText } from '@/utils/errors'
import type { Consulta, ConsultaRequest, Paciente, Usuario } from '@/types'

interface FormHandle {
  validate: () => Promise<{ valid: boolean }>
  reset: () => void
}

const ui = useUiStore()

const items = ref<Consulta[]>([])
const loading = ref(false)
const params = reactive({ fecha: '', pacienteId: null as number | null, page: 0, size: 20, totalElements: 0, totalPages: 0 })

const dialog = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormHandle | null>(null)

const form = reactive<ConsultaRequest>({
  citaId: null, pacienteId: undefined as unknown as number, veterinarioId: undefined as unknown as number,
  fecha: new Date().toISOString().slice(0,10), motivo:'', sintomatologia:'', diagnostico:'', tratamiento:'', observaciones:'',
  pesoKg:null, temperaturaC:null, frecuenciaCardiaca:null, frecuenciaRespiratoria:null, medicamentos:[], vacunas:[]
})

const pacientes = ref<Paciente[]>([]); const veterinarios = ref<Usuario[]>([])
const pacientesLoading = ref(false); const veterinariosLoading = ref(false)
const pacienteOptions = computed(() => pacientes.value.map(p => ({ title: `${p.codigo} - ${p.nombre}`, value: p.id })))
const veterinarioOptions = computed(() => veterinarios.value.filter(v=>v.activo).map(v => ({ title: v.nombre, value: v.id })))

async function load() {
  loading.value = true
  try {
    const page = await listConsultas(params.fecha||undefined, params.pacienteId||undefined, params.page, params.size)
    items.value = page.items; params.totalElements = page.totalElements; params.totalPages = page.totalPages
  } catch (e) { ui.showError(errorText(e,'No se pudieron cargar las consultas')) } finally { loading.value = false }
}
async function loadPacientes() { pacientesLoading.value=true; try{ const p=await listPacientes(undefined,0,200); pacientes.value=p.items } catch(e){ui.showError(errorText(e,'No se pudieron cargar pacientes'))} finally{pacientesLoading.value=false} }
async function loadVeterinarios() { veterinariosLoading.value=true; try{ const p=await listUsuarios(undefined,0,200); veterinarios.value=p.items } catch(e){ui.showError(errorText(e,'No se pudieron cargar usuarios'))} finally{veterinariosLoading.value=false} }

function resetForm() {
  form.citaId=null; form.pacienteId=undefined as unknown as number; form.veterinarioId=undefined as unknown as number
  form.fecha=new Date().toISOString().slice(0,10); form.motivo=''; form.sintomatologia=''; form.diagnostico=''; form.tratamiento=''; form.observaciones=''
  form.pesoKg=null; form.temperaturaC=null; form.frecuenciaCardiaca=null; form.frecuenciaRespiratoria=null; form.medicamentos=[]; form.vacunas=[]
}

function openCreate() { editingId.value=null; resetForm(); formRef.value?.reset(); dialog.value=true }
function openEdit(c:Consulta) {
  editingId.value=c.id; form.citaId=c.citaId||null; form.pacienteId=c.paciente.id; form.veterinarioId=c.veterinario.id; form.fecha=c.fecha
  form.motivo=c.motivo||''; form.sintomatologia=c.sintomatologia||''; form.diagnostico=c.diagnostico||''; form.tratamiento=c.tratamiento||''; form.observaciones=c.observaciones||''
  form.pesoKg=c.pesoKg??null; form.temperaturaC=c.temperaturaC??null; form.frecuenciaCardiaca=c.frecuenciaCardiaca??null; form.frecuenciaRespiratoria=c.frecuenciaRespiratoria??null
  form.medicamentos=c.medicamentos||[]; form.vacunas=c.vacunas||[]; dialog.value=true
}

async function save() {
  const r = await formRef.value?.validate(); if(!r?.valid) return
  saving.value=true
  try {
    const payload: ConsultaRequest = { citaId:form.citaId||null, pacienteId:form.pacienteId, veterinarioId:form.veterinarioId, fecha:form.fecha, motivo:form.motivo||null, sintomatologia:form.sintomatologia||null, diagnostico:form.diagnostico||null, tratamiento:form.tratamiento||null, observaciones:form.observaciones||null, pesoKg:form.pesoKg??null, temperaturaC:form.temperaturaC??null, frecuenciaCardiaca:form.frecuenciaCardiaca??null, frecuenciaRespiratoria:form.frecuenciaRespiratoria??null, medicamentos:form.medicamentos||[], vacunas:form.vacunas||[] }
    if(editingId.value==null){ await createConsulta(payload); ui.showSuccess('Consulta creada correctamente') } else { await updateConsulta(editingId.value,payload); ui.showSuccess('Consulta actualizada correctamente') }
    dialog.value=false; await load()
  } catch(e){ ui.showError(errorText(e,'No se pudo guardar la consulta')) } finally { saving.value=false }
}

onMounted(async ()=>{ await Promise.all([load(), loadPacientes(), loadVeterinarios()]) })
watch([()=>params.fecha, ()=>params.pacienteId], ()=>{ params.page=0; void load() })
</script>

<template>
  <div>
    <div class="vet-header">
      <div><h1>Consultas</h1><p>Historia clínica</p></div>
      <v-btn variant="flat" prepend-icon="mdi-medical-bag" @click="openCreate">Nueva consulta</v-btn>
    </div>

    <v-card elevation="0" border rounded="xl" class="mb-1">
      <v-card-text class="d-flex align-center ga-3 py-4 px-6 flex-wrap">
        <v-text-field v-model="params.fecha" label="Filtrar por fecha" type="date" density="comfortable" hide-details clearable variant="outlined" style="max-width:240px" @click:clear="params.fecha=''" />
        <v-select v-model="params.pacienteId" label="Filtrar por paciente" :items="pacienteOptions" item-title="title" item-value="value" clearable density="comfortable" hide-details variant="outlined" :loading="pacientesLoading" style="min-width:280px" />
        <v-btn variant="tonal" prepend-icon="mdi-filter-variant" size="large" @click="load">Filtrar</v-btn>
      </v-card-text>
    </v-card>

    <v-card elevation="0" border rounded="xl">
      <v-data-table :headers="[{title:'Código',key:'codigo'},{title:'Fecha',key:'fecha'},{title:'Paciente',key:'paciente'},{title:'Veterinario',key:'veterinario'},{title:'Motivo',key:'motivo'},{title:'Diagnóstico',key:'diagnostico'},{title:'Acciones',key:'acciones',sortable:false,align:'end'}]" :items="items" :loading="loading" :items-per-page="-1" hide-default-footer density="comfortable">
        <template #item.codigo="{item}"><v-chip size="small" variant="tonal" :text="item.codigo" /></template>
        <template #item.paciente="{item}">{{ item.paciente?.nombre }} ({{ item.paciente?.codigo }})</template>
        <template #item.veterinario="{item}">{{ item.veterinario?.nombre }}</template>
        <template #item.motivo="{item}">{{ item.motivo||'—' }}</template>
        <template #item.diagnostico="{item}">{{ item.diagnostico||'—' }}</template>
        <template #item.acciones="{item}"><v-btn size="small" variant="text" icon="mdi-pencil" @click="openEdit(item)" /></template>
        <template #bottom v-if="params.totalPages>1"><div class="pa-4 d-flex justify-center"><v-pagination v-model="params.page" :length="params.totalPages" :total-visible="7" @update:model-value="load" /></div></template>
      </v-data-table>
    </v-card>

    <v-dialog v-model="dialog" max-width="860" scrollable>
      <v-card rounded="xl" elevation="4">
        <v-card-title class="text-h6 pt-6 px-6">{{ editingId===null?'Nueva consulta':'Editar consulta' }}</v-card-title>
        <v-card-text class="px-6">
          <v-form ref="formRef" @submit.prevent="save">
            <v-row dense class="mb-1"><v-col cols="12" sm="6" class="pr-6"><v-select v-model="form.pacienteId" label="Paciente" :items="pacienteOptions" item-title="title" item-value="value" :rules="[(v:any)=>!!v||'Seleccione paciente']" density="comfortable" variant="outlined" :loading="pacientesLoading" /></v-col><v-col cols="12" sm="6" class="pl-6"><v-select v-model="form.veterinarioId" label="Veterinario" :items="veterinarioOptions" item-title="title" item-value="value" :rules="[(v:any)=>!!v||'Seleccione veterinario']" density="comfortable" variant="outlined" :loading="veterinariosLoading" /></v-col></v-row>
            <v-row dense class="mb-1"><v-col cols="12" sm="4" class="pr-6"><v-text-field v-model="form.fecha" label="Fecha" type="date" density="comfortable" variant="outlined" /></v-col><v-col cols="12" sm="8" class="pl-6"><v-text-field v-model="form.motivo" label="Motivo de consulta" density="comfortable" variant="outlined" /></v-col></v-row>
            <v-row dense class="mb-1"><v-col cols="12" class="pr-2"><v-textarea v-model="form.sintomatologia" label="Sintomatología" rows="2" density="comfortable" variant="outlined" auto-grow /></v-col></v-row>
            <v-row dense class="mb-1"><v-col cols="12" class="pr-2"><v-textarea v-model="form.diagnostico" label="Diagnóstico" rows="2" density="comfortable" variant="outlined" auto-grow /></v-col></v-row>
            <v-row dense class="mb-1"><v-col cols="12" class="pr-2"><v-textarea v-model="form.tratamiento" label="Tratamiento" rows="2" density="comfortable" variant="outlined" auto-grow /></v-col></v-row>
            <v-row dense class="mb-1"><v-col cols="12" sm="3" class="pr-4"><v-text-field v-model.number="form.pesoKg" label="Peso (kg)" type="number" step="0.01" density="comfortable" variant="outlined" /></v-col><v-col cols="12" sm="3" class="px-2"><v-text-field v-model.number="form.temperaturaC" label="Temp. (°C)" type="number" step="0.01" density="comfortable" variant="outlined" /></v-col><v-col cols="12" sm="3" class="px-2"><v-text-field v-model.number="form.frecuenciaCardiaca" label="FC (ppm)" type="number" density="comfortable" variant="outlined" /></v-col><v-col cols="12" sm="3" class="pl-4"><v-text-field v-model.number="form.frecuenciaRespiratoria" label="FR (rpm)" type="number" density="comfortable" variant="outlined" /></v-col></v-row>
            <v-row dense class="mb-1"><v-col cols="12" class="pr-2"><v-textarea v-model="form.observaciones" label="Observaciones" rows="2" density="comfortable" variant="outlined" auto-grow /></v-col></v-row>
          </v-form>
        </v-card-text>
        <v-card-actions class="px-6 pb-6"><v-spacer /><v-btn variant="text" rounded="lg" @click="dialog=false">Cancelar</v-btn><v-btn color="primary" :loading="saving" rounded="lg" variant="flat" @click="save">{{ editingId===null?'Crear consulta':'Guardar cambios' }}</v-btn></v-card-actions>
      </v-card>
    </v-dialog>
  </div>
</template>
