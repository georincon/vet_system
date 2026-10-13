# Análisis del estado (2026-10-13)

## Qué hay
- **Frontend** (Vue 3 + Vuetify 4 + Vite + Pinia): login, dashboard, tutores, pacientes, citas, consultas, usuarios. Compila (`npm run build` OK).
- **Backend** (Spring Boot + Modulith + Flyway + Postgres 18): migraciones V1–V11 completas (esquema de consulta, inmunización, inventario, factura, auditoría), tests compilados en `target/`.
- **Docker**: `docker-compose.yml` con db (5256), backend (9010), frontend/nginx (8090).

## Hallazgos
1. **Backend sin código fuente**: en `backend/src/main/java` solo existe `VeterinariaApplication.java`; faltan los módulos seguridad, tutores, citas y shared (71 clases). El original completo está en `~/Proyectos/Veterinaria/backend`. Sin ellos la API responde 401 (Spring Security por defecto) y no hay login.
2. Frontend con estilos heredados del proyecto UIS (`uis-corporate.css`, `!important` masivo, márgenes "hack" en vistas) y logo roto en el login → reemplazado por `src/styles/vet-theme.css`.
3. La tabla `auditoria` no tiene escritura todavía.

## Cambios de diseño hechos
- Se eliminó el escudo/logo UIS; la patita (`306277.svg`) es ahora `public/patita.svg` (blanca, barra) y `patita-verde.svg` (login, favicon).
- Tema nuevo basado en el emisor EUDI (solo lectura): gradiente verde→azul, cabeceras `vet-header`, títulos de sección, barra de acento, login dividido con ilustración de patitas.
