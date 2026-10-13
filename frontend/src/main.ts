import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import './styles/vet-theme.css'
import { vuetify } from './plugins/vuetify'
import { router } from './router'
import { useAuthStore } from './stores/auth'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(vuetify)

const auth = useAuthStore(pinia)
auth.hydrate()

app.use(router)
app.mount('#app')