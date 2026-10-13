# Veterinaria - Sistema de Informacion

Sistema de gestion para una clinica veterinaria. Monorepo con:

- `backend/` - Spring Boot 4.1.1 + Spring Modulith 2.1.1 + Postgres 18 (Flyway) + JWT.
- `frontend/` - Vue 3 + Vuetify 4 + Vite 8 + TypeScript + Pinia + vue-router.
- `docker-compose.yml` - Postgres 18 local para desarrollo.

## Puertos

| Servicio     | Puerto | Nota |
|--------------|--------|------|
| Backend API  | 9010   | `server.port` (`VET_PORT`) |
| Postgres dev | 5256   | publicacion de Docker (`5434` era provisional) |
| Vite dev     | 5173   | proxy `/api` -> `http://localhost:9010` |

Credenciales seed: `admin` / `admin` (`seed-admin: true` en dev).

## Como levantar

```bash
# Infraestructura (Postgres 18, datos en /var/lib/postgresql)
docker compose up -d

# Backend (aplica migraciones Flyway al arrancar)
cd backend && ./mvnw spring-boot:run
# o: ./mvnw -DskipTests package && java -jar target/veterinaria-backend-0.1.0-SNAPSHOT.jar

# Frontend (dev con proxy)
cd frontend && npm run dev
```

Health: `GET http://localhost:9010/actuator/health`. Swagger: `/swagger-ui.html` (backend).

## Arquitectura (Spring Modulith)

- Modulo `shared` - `@ApplicationModule(type = Type.OPEN)`: `config`, `error` (handler global),
  `web` (`PageResponse<T>` con `{items,page,size,totalElements,totalPages,first,last}`).
- Modulo `seguridad` (cerrado por defecto): paquetes `api` (controllers/DTOs),
  `application` (services), `domain` (entidades/enums), `internal` (repositorios).
- Reglas: la unica integracion entre modulos debe ser documentada en `application/module-info`
  y validada por `ModularityTests`.

## API Fase 0

- `POST /api/auth/login` `{username,password}` -> `{token, expiraEn, usuario}`.
- `GET /api/auth/me` -> usuario autenticado.
- `GET /api/usuarios?q=&page=&size=` (ADMIN) -> `PageResponse<UsuarioResponse>`.
- `POST /api/usuarios` (ADMIN) `{username,password,nombre,email,rol}`.
- Roles: `ADMIN`, `VETERINARIO`, `RECEPCION`, `AUXILIAR`.

Errores: cuerpo `{timestamp,status,message,path,fieldErrors}` via `shared/error`.
JWT: firma HMAC (secreto via `VET_JWT_SECRET`, min 32 chars), expiracion `expiraEn` (ms).

## Estado

- [x] Fase 0: scaffold + seguridad (login JWT + CRUD usuarios) validado.
  - Backend: `./mvnw verify` -> 5/5 tests (Modularity, Flyway sobre Postgres 18 real
    via Testcontainers, Auth API).
  - Frontend: `npm run build` OK (vue-tsc + vite build).
  - Smoke test E2E real: login (admin), `/me`, listado paginado, 401 sin token.

## Fase 1 - Modulo tutores

Estado al 2026-10-11:

- [x] Migracion V2 (`paciente.sexo` -> CHAR(1)) para validar con Hibernate.
- [x] Modulo Modulith `tutores` cerrado (api/application/domain/internal): entidades
  Tutor, Paciente, Especie, Raza, PacienteTutor (M:N con tutor principal); repos,
  `TutorService`, controllers `/api/tutores`, `/api/pacientes`, `/api/catalogos`;
  seed de catalogos (`vet.catalogos.seed`, 5 especies + razas), codigo de paciente
  auto `PAC-YYYYMMDD-NNNN`, unicidad de documento (409), validacion raza/especie (422).
- [x] `TutoresApiTest` (5 tests): creacion+tutor principal, 409 duplicado, 422 raza
  cruzada, update, catalogos sembrados.
- [x] BUG de suite resuelto (2026-10-11): dos causas.
  1) Contexto: clases con `@Container` estatico + cache de contexto Spring compartia
     la URL del contenedor viejo (reciclado por JUnit) -> 500/Hikari timeout y razas
     vacias. Fix: `@DirtiesContext(AFTER_CLASS)` en `BaseIntegrationTest`.
  2) Bug real en el helper del test `idPorNombre()`: `entre()` tomaba la PRIMERA
     ocurrencia de `"id":` (siempre la primera especie) en vez de la anterior al
     nombre. Fix: `lastIndexOf` sobre el trozo previo.
- [x] `./mvnw verify` -> **10/10** (Modularity, SchemaMigrations, AuthApiTest 3,
  TutoresApiTest 5). Sin codigo de debug temporal.
- [x] Smoke E2E real en :9010 con jar reempaquetado: especies, razas por especie,
  crear tutor, crear paciente vinculado a 2 tutores (codigo `PAC-20261011-0001`,
  tutor principal correcto), busqueda y pacientes de un tutor no principal. (BD dev
  quedo con 2 tutores + 1 paciente + catalogos sembrados.)
- [x] Frontend Fase 1: `types` (Tutor, Paciente, Especie, Raza, enums/documentos),
  `api/catalogos.ts`, `api/tutores.ts`, `api/pacientes.ts`, `TutoresView.vue`
  (tabla + CRUD + dialog "pacientes del tutor"), `PacientesView.vue` (especie->razas
  en cascada, tutores multi + tutor principal, estetica Vuetify acorde a UsuariosView),
  rutas `/tutores` y `/pacientes` + menu "Operacion" en `AppLayout`.
  `npm run build` OK.

Regla: no avanzar al siguiente modulo sin `./mvnw verify` y `npm run build` verdes.

Nota de auditoria: la tabla `auditoria` de V1 queda sin escritura hasta el modulo
de trazabilidad; no bloquea la validacion de JPA (no hay entidad mapeada).

## Seguridad del entorno (notas)

- El historial local de opencode fue anonimizado (sin datos personales) y la config
  quedo con plugins desactivados (ver `~/.config/opencode/`).
- El canal de red al proveedor de modelo se mantiene activo por decision del usuario.