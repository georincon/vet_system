-- ============================================================================
-- V14 (PROPUESTA) - Agenda: salas, horarios laborales, sala de espera, lista de espera,
-- constraint anti-solape, tipos de cita y estados.
-- ============================================================================
CREATE TABLE IF NOT EXISTS sala (
    id        BIGSERIAL PRIMARY KEY,
    sede_id   BIGINT NOT NULL REFERENCES sede (id),
    nombre    VARCHAR(100) NOT NULL,
    tipo      VARCHAR(20)  NOT NULL DEFAULT 'CONSULTORIO',
    capacidad INT NOT NULL DEFAULT 1,
    activo    BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_sala_sede_nombre UNIQUE (sede_id, nombre),
    CONSTRAINT ck_sala_tipo CHECK (tipo IN ('CONSULTORIO','QUIROFANO','HOSPITALIZACION','PELUQUERIA','IMAGENES','LABORATORIO','GUARDERIA','ESPERA')),
    CONSTRAINT ck_sala_capacidad CHECK (capacidad > 0)
);
COMMENT ON TABLE sala IS 'Espacios fisicos reservables (consultorio, quirofano, etc.).';

INSERT INTO sala (sede_id, nombre, tipo)
SELECT s.id, v.nombre, v.tipo FROM sede s
CROSS JOIN (VALUES ('Consultorio 1','CONSULTORIO'),('Consultorio 2','CONSULTORIO'),('Quirofano','QUIROFANO'),
                   ('Imagenes','IMAGENES'),('Peluqueria','PELUQUERIA')) AS v(nombre, tipo)
WHERE s.es_principal
ON CONFLICT (sede_id, nombre) DO NOTHING;

-- Horario laboral semanal del profesional (0=domingo ... 6=sabado). Varias franjas por dia.
CREATE TABLE IF NOT EXISTS horario_profesional (
    id            BIGSERIAL PRIMARY KEY,
    usuario_id    BIGINT NOT NULL REFERENCES usuario (id),
    sede_id       BIGINT NOT NULL REFERENCES sede (id),
    dia_semana    SMALLINT NOT NULL,
    hora_inicio   TIME NOT NULL,
    hora_fin      TIME NOT NULL,
    vigente_desde DATE NOT NULL DEFAULT CURRENT_DATE,
    vigente_hasta DATE,
    activo        BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_horario_dia   CHECK (dia_semana BETWEEN 0 AND 6),
    CONSTRAINT ck_horario_rango CHECK (hora_fin > hora_inicio),
    CONSTRAINT ck_horario_vig   CHECK (vigente_hasta IS NULL OR vigente_hasta >= vigente_desde)
);
CREATE INDEX IF NOT EXISTS ix_horario_usuario ON horario_profesional (usuario_id, dia_semana);

-- bloqueo_agenda (V1) ahora puede bloquear una sala en lugar de un profesional
ALTER TABLE bloqueo_agenda ADD COLUMN IF NOT EXISTS sala_id BIGINT REFERENCES sala (id);
ALTER TABLE bloqueo_agenda ADD COLUMN IF NOT EXISTS tipo    VARCHAR(20) NOT NULL DEFAULT 'OTRO';
ALTER TABLE bloqueo_agenda ADD COLUMN IF NOT EXISTS creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_bloqueo_tipo') THEN
    ALTER TABLE bloqueo_agenda ADD CONSTRAINT ck_bloqueo_tipo
      CHECK (tipo IN ('VACACIONES','CAPACITACION','ALMUERZO','MANTENIMIENTO','CIRUGIA','PERSONAL','OTRO'));
  END IF;
END $$;

-- tipo_cita: precio/servicio asociado y reglas
ALTER TABLE tipo_cita ADD COLUMN IF NOT EXISTS item_id           BIGINT REFERENCES item (id);
ALTER TABLE tipo_cita ADD COLUMN IF NOT EXISTS tipo_sala         VARCHAR(20);
ALTER TABLE tipo_cita ADD COLUMN IF NOT EXISTS permite_online    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tipo_cita ADD COLUMN IF NOT EXISTS requiere_ayuno    BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tipo_cita ADD COLUMN IF NOT EXISTS instrucciones_previas TEXT;

-- cita: sala, canal, llegada (sala de espera), confirmacion y cancelacion
ALTER TABLE cita ADD COLUMN IF NOT EXISTS sala_id             BIGINT REFERENCES sala (id);
ALTER TABLE cita ADD COLUMN IF NOT EXISTS canal_origen        VARCHAR(15) NOT NULL DEFAULT 'RECEPCION';
ALTER TABLE cita ADD COLUMN IF NOT EXISTS hora_llegada        TIMESTAMPTZ;
ALTER TABLE cita ADD COLUMN IF NOT EXISTS atencion_inicio     TIMESTAMPTZ;
ALTER TABLE cita ADD COLUMN IF NOT EXISTS confirmada_en       TIMESTAMPTZ;
ALTER TABLE cita ADD COLUMN IF NOT EXISTS confirmada_via      VARCHAR(15);
ALTER TABLE cita ADD COLUMN IF NOT EXISTS cancelada_en        TIMESTAMPTZ;
ALTER TABLE cita ADD COLUMN IF NOT EXISTS cancelada_por       BIGINT REFERENCES usuario (id);
ALTER TABLE cita ADD COLUMN IF NOT EXISTS motivo_cancelacion  VARCHAR(255);
ALTER TABLE cita ADD COLUMN IF NOT EXISTS cita_origen_id      BIGINT REFERENCES cita (id);
ALTER TABLE cita ADD COLUMN IF NOT EXISTS es_urgencia         BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE cita ADD COLUMN IF NOT EXISTS es_telemedicina     BOOLEAN NOT NULL DEFAULT FALSE;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_cita_canal') THEN
    ALTER TABLE cita ADD CONSTRAINT ck_cita_canal CHECK (canal_origen IN ('RECEPCION','TELEFONO','WHATSAPP','PORTAL','WEB','OTRO'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_cita_rango') THEN
    ALTER TABLE cita ADD CONSTRAINT ck_cita_rango CHECK (fecha_hora_fin > fecha_hora_inicio) NOT VALID;
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_cita_sala_fecha ON cita (sala_id, fecha_hora_inicio);

-- Anti-solape a nivel BD (profesional y sala). Solo se crea si los datos actuales no se solapan;
-- ademas se reemplaza uq_cita_vet_inicio, que bloqueaba reutilizar el horario de una cita cancelada.
DO $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ex_cita_vet_solape') THEN
    IF EXISTS (SELECT 1 FROM cita a JOIN cita b ON a.id < b.id AND a.veterinario_id = b.veterinario_id
                AND tstzrange(a.fecha_hora_inicio, a.fecha_hora_fin) && tstzrange(b.fecha_hora_inicio, b.fecha_hora_fin)
                AND a.estado NOT IN ('CANCELADA','NO_ASISTIO') AND b.estado NOT IN ('CANCELADA','NO_ASISTIO')
                AND a.activo AND b.activo) THEN
      RAISE NOTICE 'ex_cita_vet_solape NO creada: hay citas solapadas existentes; depurar y reintentar';
    ELSE
      ALTER TABLE cita ADD CONSTRAINT ex_cita_vet_solape EXCLUDE USING gist
        (veterinario_id WITH =, tstzrange(fecha_hora_inicio, fecha_hora_fin) WITH &&)
        WHERE (estado NOT IN ('CANCELADA','NO_ASISTIO') AND activo);
      ALTER TABLE cita DROP CONSTRAINT IF EXISTS uq_cita_vet_inicio;
    END IF;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ex_cita_sala_solape') THEN
    IF NOT EXISTS (SELECT 1 FROM cita a JOIN cita b ON a.id < b.id AND a.sala_id = b.sala_id AND a.sala_id IS NOT NULL
                AND tstzrange(a.fecha_hora_inicio, a.fecha_hora_fin) && tstzrange(b.fecha_hora_inicio, b.fecha_hora_fin)
                AND a.estado NOT IN ('CANCELADA','NO_ASISTIO') AND b.estado NOT IN ('CANCELADA','NO_ASISTIO')
                AND a.activo AND b.activo) THEN
      ALTER TABLE cita ADD CONSTRAINT ex_cita_sala_solape EXCLUDE USING gist
        (sala_id WITH =, tstzrange(fecha_hora_inicio, fecha_hora_fin) WITH &&)
        WHERE (sala_id IS NOT NULL AND estado NOT IN ('CANCELADA','NO_ASISTIO') AND activo);
    END IF;
  END IF;
END $$;

-- ---------------------------------------------------------------- LISTA DE ESPERA
CREATE TABLE IF NOT EXISTS lista_espera (
    id                  BIGSERIAL PRIMARY KEY,
    sede_id             BIGINT REFERENCES sede (id),
    paciente_id         BIGINT NOT NULL REFERENCES paciente (id),
    tutor_id            BIGINT NOT NULL REFERENCES tutor (id),
    tipo_cita_id        BIGINT REFERENCES tipo_cita (id),
    veterinario_id      BIGINT REFERENCES usuario (id),
    fecha_deseada_desde DATE,
    fecha_deseada_hasta DATE,
    franja_preferida    VARCHAR(10),
    prioridad           SMALLINT NOT NULL DEFAULT 3,
    estado              VARCHAR(12) NOT NULL DEFAULT 'ACTIVA',
    cita_asignada_id    BIGINT REFERENCES cita (id),
    notas               VARCHAR(500),
    creado_en           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_le_estado    CHECK (estado IN ('ACTIVA','ASIGNADA','CANCELADA','VENCIDA')),
    CONSTRAINT ck_le_prioridad CHECK (prioridad BETWEEN 1 AND 5),
    CONSTRAINT ck_le_franja    CHECK (franja_preferida IS NULL OR franja_preferida IN ('MANANA','TARDE','CUALQUIERA'))
);
CREATE INDEX IF NOT EXISTS ix_le_estado ON lista_espera (estado, prioridad, creado_en);
COMMENT ON TABLE lista_espera IS 'Pacientes que esperan cupo; al cancelarse una cita se ofrece al primero compatible.';

-- ---------------------------------------------------------------- SEMILLAS
INSERT INTO estado_cita (codigo, nombre) VALUES ('EN_SALA', 'En sala de espera')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO tipo_cita (nombre, duracion_minutos, color, requiere_confirmacion, permite_online) VALUES
 ('Consulta general',          30, '#2E7D32', TRUE,  TRUE),
 ('Control / seguimiento',     20, '#00897B', TRUE,  TRUE),
 ('Vacunacion',                15, '#1E88E5', FALSE, TRUE),
 ('Desparasitacion',           15, '#3949AB', FALSE, TRUE),
 ('Urgencia',                  45, '#D32F2F', FALSE, FALSE),
 ('Cirugia',                   90, '#6A1B9A', TRUE,  FALSE),
 ('Ecografia / imagenes',      30, '#00ACC1', TRUE,  FALSE),
 ('Toma de muestras / laboratorio', 15, '#F9A825', FALSE, TRUE),
 ('Peluqueria / bano',         60, '#EC407A', TRUE,  TRUE),
 ('Teleconsulta',              20, '#5E35B1', TRUE,  TRUE),
 ('Eutanasia',                 45, '#455A64', TRUE,  FALSE),
 ('Chequeo prequirurgico',     30, '#8D6E63', TRUE,  FALSE)
ON CONFLICT (nombre) DO NOTHING;

UPDATE tipo_cita SET requiere_ayuno = TRUE, instrucciones_previas = 'Ayuno de 8-12 horas (solido); agua permitida.'
 WHERE nombre IN ('Cirugia', 'Chequeo prequirurgico', 'Ecografia / imagenes') AND instrucciones_previas IS NULL;
