-- ============================================================================
-- V21 (PROPUESTA) - Servicios complementarios: peluqueria/estetica, guarderia/hotel,
-- planes de salud (membresias).
-- ============================================================================
CREATE TABLE IF NOT EXISTS peluqueria_ficha (
    id            BIGSERIAL PRIMARY KEY,
    sede_id       BIGINT REFERENCES sede (id),
    paciente_id   BIGINT NOT NULL REFERENCES paciente (id),
    cita_id       BIGINT REFERENCES cita (id),
    groomer_id    BIGINT REFERENCES usuario (id),
    fecha         DATE NOT NULL DEFAULT CURRENT_DATE,
    servicios     JSONB NOT NULL DEFAULT '[]'::jsonb,
    estilo_corte  VARCHAR(150),
    largo_cuchilla VARCHAR(20),
    estado_piel_pelaje TEXT,
    hallazgos_para_veterinario TEXT,
    temperamento  VARCHAR(20),
    sedacion      BOOLEAN NOT NULL DEFAULT FALSE,
    foto_antes_ruta VARCHAR(500),
    foto_despues_ruta VARCHAR(500),
    estado        VARCHAR(12) NOT NULL DEFAULT 'PROGRAMADO',
    inicio_en     TIMESTAMPTZ,
    fin_en        TIMESTAMPTZ,
    notas         TEXT,
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pf_estado CHECK (estado IN ('PROGRAMADO','EN_PROCESO','TERMINADO','CANCELADO')),
    CONSTRAINT ck_pf_temp CHECK (temperamento IS NULL OR temperamento IN ('TRANQUILO','NERVIOSO','AGRESIVO'))
);
CREATE INDEX IF NOT EXISTS ix_pf_paciente ON peluqueria_ficha (paciente_id, fecha);

CREATE TABLE IF NOT EXISTS guarderia_reserva (
    id              BIGSERIAL PRIMARY KEY,
    codigo          VARCHAR(20) NOT NULL,
    sede_id         BIGINT NOT NULL REFERENCES sede (id),
    paciente_id     BIGINT NOT NULL REFERENCES paciente (id),
    tutor_id        BIGINT NOT NULL REFERENCES tutor (id),
    tipo            VARCHAR(10) NOT NULL DEFAULT 'GUARDERIA',
    cama_id         BIGINT REFERENCES cama (id),
    ingreso_previsto TIMESTAMPTZ NOT NULL,
    salida_prevista  TIMESTAMPTZ NOT NULL,
    ingreso_real    TIMESTAMPTZ,
    salida_real     TIMESTAMPTZ,
    instrucciones_alimentacion TEXT,
    medicacion      TEXT,
    pertenencias    TEXT,
    contacto_emergencia VARCHAR(150),
    vacunas_al_dia_verificado BOOLEAN NOT NULL DEFAULT FALSE,
    consentimiento_id BIGINT REFERENCES consentimiento (id),
    tarifa_dia      NUMERIC(12,2),
    factura_id      BIGINT REFERENCES factura (id),
    estado          VARCHAR(12) NOT NULL DEFAULT 'RESERVADA',
    notas           TEXT,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_guarderia_codigo UNIQUE (codigo),
    CONSTRAINT ck_gr_tipo CHECK (tipo IN ('GUARDERIA','HOTEL')),
    CONSTRAINT ck_gr_estado CHECK (estado IN ('RESERVADA','EN_CURSO','FINALIZADA','CANCELADA','NO_LLEGO')),
    CONSTRAINT ck_gr_rango CHECK (salida_prevista > ingreso_previsto)
);
-- Sin sobrecupo: una cama no puede tener dos reservas activas solapadas
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ex_guarderia_cama_solape') THEN
    ALTER TABLE guarderia_reserva ADD CONSTRAINT ex_guarderia_cama_solape EXCLUDE USING gist
      (cama_id WITH =, tstzrange(ingreso_previsto, salida_prevista) WITH &&)
      WHERE (cama_id IS NOT NULL AND estado IN ('RESERVADA','EN_CURSO'));
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_gr_fechas ON guarderia_reserva (sede_id, ingreso_previsto);

-- Planes de salud / membresias
CREATE TABLE IF NOT EXISTS plan_salud (
    id           BIGSERIAL PRIMARY KEY,
    codigo       VARCHAR(30) NOT NULL,
    nombre       VARCHAR(150) NOT NULL,
    especie_id   BIGINT REFERENCES especie (id),
    descripcion  TEXT,
    precio       NUMERIC(12,2) NOT NULL,
    periodicidad VARCHAR(10) NOT NULL DEFAULT 'ANUAL',
    activo       BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_plan_codigo UNIQUE (codigo),
    CONSTRAINT ck_plan_periodicidad CHECK (periodicidad IN ('MENSUAL','TRIMESTRAL','SEMESTRAL','ANUAL')),
    CONSTRAINT ck_plan_precio CHECK (precio >= 0)
);

CREATE TABLE IF NOT EXISTS plan_salud_item (
    plan_id        BIGINT NOT NULL REFERENCES plan_salud (id) ON DELETE CASCADE,
    item_id        BIGINT NOT NULL REFERENCES item (id),
    cantidad_incluida INT NOT NULL DEFAULT 1,
    descuento_pct  NUMERIC(5,2) NOT NULL DEFAULT 100,
    PRIMARY KEY (plan_id, item_id),
    CONSTRAINT ck_psi CHECK (cantidad_incluida > 0 AND descuento_pct BETWEEN 0 AND 100)
);

CREATE TABLE IF NOT EXISTS suscripcion_plan (
    id           BIGSERIAL PRIMARY KEY,
    plan_id      BIGINT NOT NULL REFERENCES plan_salud (id),
    paciente_id  BIGINT NOT NULL REFERENCES paciente (id),
    tutor_id     BIGINT NOT NULL REFERENCES tutor (id),
    inicio       DATE NOT NULL DEFAULT CURRENT_DATE,
    fin          DATE NOT NULL,
    estado       VARCHAR(10) NOT NULL DEFAULT 'ACTIVA',
    factura_id   BIGINT REFERENCES factura (id),
    renovacion_automatica BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sp_estado CHECK (estado IN ('ACTIVA','VENCIDA','CANCELADA')),
    CONSTRAINT ck_sp_fechas CHECK (fin > inicio)
);
CREATE INDEX IF NOT EXISTS ix_sp_paciente ON suscripcion_plan (paciente_id, estado);
CREATE INDEX IF NOT EXISTS ix_sp_fin ON suscripcion_plan (fin) WHERE estado = 'ACTIVA';

CREATE TABLE IF NOT EXISTS consumo_plan (
    id             BIGSERIAL PRIMARY KEY,
    suscripcion_id BIGINT NOT NULL REFERENCES suscripcion_plan (id),
    item_id        BIGINT NOT NULL REFERENCES item (id),
    consulta_id    BIGINT REFERENCES consulta (id),
    cantidad       INT NOT NULL DEFAULT 1,
    consumido_en   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cpl CHECK (cantidad > 0)
);
CREATE INDEX IF NOT EXISTS ix_cpl_susc ON consumo_plan (suscripcion_id, item_id);

-- Semilla de planes de ejemplo (precios ilustrativos)
INSERT INTO plan_salud (codigo, nombre, especie_id, descripcion, precio, periodicidad)
SELECT v.codigo, v.nombre, (SELECT id FROM especie WHERE nombre = v.esp), v.descr, v.precio, 'ANUAL'
FROM (VALUES
 ('PLAN-CACHORRO', 'Plan cachorro',  'Perro', 'Serie vacunal nucleo, desparasitaciones y 3 controles', 450000),
 ('PLAN-GATITO',   'Plan gatito',    'Gato',  'Serie vacunal nucleo, desparasitaciones y 3 controles', 400000),
 ('PLAN-ADULTO',   'Plan adulto',    'Perro', 'Chequeo anual, refuerzos y desparasitacion',            320000)
) AS v(codigo, nombre, esp, descr, precio)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO plan_salud_item (plan_id, item_id, cantidad_incluida, descuento_pct)
SELECT p.id, i.id, v.cant, 100
FROM (VALUES
 ('PLAN-CACHORRO','SRV-CONS-GEN',3), ('PLAN-CACHORRO','SRV-PRC-VAC',4), ('PLAN-CACHORRO','SRV-PRC-DES',3),
 ('PLAN-GATITO',  'SRV-CONS-GEN',3), ('PLAN-GATITO',  'SRV-PRC-VAC',4), ('PLAN-GATITO',  'SRV-PRC-DES',3),
 ('PLAN-ADULTO',  'SRV-CONS-GEN',1), ('PLAN-ADULTO',  'SRV-PRC-VAC',2), ('PLAN-ADULTO',  'SRV-PRC-DES',2)
) AS v(plan, item, cant)
JOIN plan_salud p ON p.codigo = v.plan
JOIN item i ON i.codigo = v.item
ON CONFLICT DO NOTHING;
