ALTER TABLE cita ADD COLUMN IF NOT EXISTS fecha DATE;
ALTER TABLE cita ADD COLUMN IF NOT EXISTS hora_inicio TIME;
ALTER TABLE cita ADD COLUMN IF NOT EXISTS hora_fin TIME;
ALTER TABLE cita ADD COLUMN IF NOT EXISTS estado_cita_id INT ;
ALTER TABLE cita ADD COLUMN IF NOT EXISTS motivo VARCHAR(255);
ALTER TABLE cita ADD COLUMN IF NOT EXISTS notas VARCHAR(1000);
ALTER TABLE cita ADD COLUMN IF NOT EXISTS activo BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE cita ADD COLUMN IF NOT EXISTS creado_el TIMESTAMP WITHOUT TIME ZONE;
ALTER TABLE cita ADD COLUMN IF NOT EXISTS actualizado_el TIMESTAMP WITHOUT TIME ZONE;

UPDATE cita SET activo = TRUE WHERE activo IS NULL;
UPDATE cita SET creado_el = COALESCE(creado_el, CURRENT_TIMESTAMP) WHERE creado_el IS NULL;
UPDATE cita SET actualizado_el = COALESCE(actualizado_el, CURRENT_TIMESTAMP) WHERE actualizado_el IS NULL;

ALTER TABLE cita ALTER COLUMN creado_el SET NOT NULL;
ALTER TABLE cita ALTER COLUMN actualizado_el SET NOT NULL;

CREATE TABLE IF NOT EXISTS consulta (
    id BIGSERIAL PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL UNIQUE,
    cita_id BIGINT REFERENCES cita(id),
    paciente_id BIGINT NOT NULL REFERENCES paciente(id),
    veterinario_id BIGINT NOT NULL REFERENCES usuario(id),
    fecha DATE NOT NULL,
    motivo VARCHAR(255),
    sintomatologia VARCHAR(2000),
    diagnostico VARCHAR(2000),
    tratamiento VARCHAR(2000),
    observaciones VARCHAR(2000),
    peso_kg DECIMAL(5,2),
    temperatura_c DECIMAL(4,2),
    frecuencia_cardiaca INT,
    frecuencia_respiratoria INT,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    creado_el TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    actualizado_el TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

CREATE TABLE IF NOT EXISTS consulta_medicamento (
    id BIGSERIAL PRIMARY KEY,
    consulta_id BIGINT NOT NULL REFERENCES consulta(id) ON DELETE CASCADE,
    nombre VARCHAR(150) NOT NULL,
    dosis VARCHAR(120),
    frecuencia VARCHAR(120),
    duracion VARCHAR(120),
    via VARCHAR(50),
    observaciones VARCHAR(255),
    orden INT
);

CREATE TABLE IF NOT EXISTS consulta_vacuna (
    id BIGSERIAL PRIMARY KEY,
    consulta_id BIGINT NOT NULL REFERENCES consulta(id) ON DELETE CASCADE,
    nombre VARCHAR(120) NOT NULL,
    lote VARCHAR(60),
    fecha_aplicacion DATE,
    proxima_fecha DATE
);
