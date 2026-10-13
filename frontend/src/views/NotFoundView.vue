<template>
  <v-app>
    <v-main>
      <v-container class="fill-height" fluid>
        <v-row justify="center">
          <v-col cols="12" sm="8" md="5">
            <v-card class="pa-6 text-center" elevation="0" border>
              <div class="text-h2 font-weight-bold text-medium-emphasis">404</div>
              <div class="text-h6 mt-2 mb-4">Página no encontrada</div>
              <p class="text-body-2 text-medium-emphasis mb-4">
                La ruta solicitada no existe en el Sistema de Gestión Veterinaria.
              </p>
              <v-btn color="primary" prepend-icon="mdi-home" to="/">Volver al inicio</v-btn>
            </v-card>
          </v-col>
        </v-row>
      </v-container>
    </v-main>
  </v-app>
</template>

<script setup lang="ts">
document.title = 'Página no encontrada | Sistema de Gestión Veterinaria'
</script>