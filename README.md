# vet_system

## Levantar todo el proyecto (Docker)

```bash
cd docker-compose
cp .env.example .env     # opcional: cambiar puertos/secretos
docker compose up -d --build
```

| Servicio   | URL / puerto             |
|------------|--------------------------|
| Web        | http://localhost:8090    |
| API        | http://localhost:9010 (`/swagger-ui.html`, `/actuator/health`) |
| PostgreSQL | localhost:5256           |

Usuario inicial: `admin` / `admin` (solo desarrollo; `VET_SEED_ADMIN=false` en producción).
