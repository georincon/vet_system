-- ============================================================================
-- V20 (PROPUESTA) - Hospitalizacion (camas, tareas, monitoreo), cirugia/anestesia y
-- laboratorio con parametros y rangos de referencia por especie/sexo/edad.
-- ============================================================================
CREATE TABLE IF NOT EXISTS cama (
    id        BIGSERIAL PRIMARY KEY,
    sede_id   BIGINT NOT NULL REFERENCES sede (id),
    sala_id   BIGINT REFERENCES sala (id),
    codigo    VARCHAR(20) NOT NULL,
    tipo      VARCHAR(15) NOT NULL DEFAULT 'JAULA',
    tamano    VARCHAR(10) NOT NULL DEFAULT 'MEDIANA',
    aislamiento BOOLEAN NOT NULL DEFAULT FALSE,
    estado    VARCHAR(15) NOT NULL DEFAULT 'LIBRE',
    activo    BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_cama_sede_codigo UNIQUE (sede_id, codigo),
    CONSTRAINT ck_cama_tipo   CHECK (tipo IN ('JAULA','CANIL','UCI','AISLAMIENTO','HOTEL')),
    CONSTRAINT ck_cama_tamano CHECK (tamano IN ('PEQUENA','MEDIANA','GRANDE')),
    CONSTRAINT ck_cama_estado CHECK (estado IN ('LIBRE','OCUPADA','LIMPIEZA','FUERA_SERVICIO'))
);

CREATE TABLE IF NOT EXISTS hospitalizacion (
    id                   BIGSERIAL PRIMARY KEY,
    codigo               VARCHAR(20) NOT NULL,
    sede_id              BIGINT REFERENCES sede (id),
    paciente_id          BIGINT NOT NULL REFERENCES paciente (id),
    tutor_id             BIGINT REFERENCES tutor (id),
    consulta_origen_id   BIGINT REFERENCES consulta (id),
    veterinario_id       BIGINT NOT NULL REFERENCES usuario (id),
    cama_id              BIGINT REFERENCES cama (id),
    consentimiento_id    BIGINT REFERENCES consentimiento (id),
    ingreso_en           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    alta_en              TIMESTAMPTZ,
    motivo               TEXT NOT NULL,
    diagnostico_ingreso  TEXT,
    plan_tratamiento     TEXT,
    estado               VARCHAR(12) NOT NULL DEFAULT 'INGRESADO',
    tipo_alta            VARCHAR(15),
    resumen_alta         TEXT,
    indicaciones_alta    TEXT,
    creado_en            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_hosp_codigo UNIQUE (codigo),
    CONSTRAINT ck_hosp_estado CHECK (estado IN ('INGRESADO','ESTABLE','CRITICO','ALTA','FALLECIDO','REFERIDO')),
    CONSTRAINT ck_hosp_tipo_alta CHECK (tipo_alta IS NULL OR tipo_alta IN ('MEJORIA','VOLUNTARIA','REFERIDO','FALLECIMIENTO','EUTANASIA')),
    CONSTRAINT ck_hosp_fechas CHECK (alta_en IS NULL OR alta_en >= ingreso_en),
    CONSTRAINT ck_hosp_alta CHECK (estado NOT IN ('ALTA','FALLECIDO','REFERIDO') OR alta_en IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS ix_hosp_paciente ON hospitalizacion (paciente_id, ingreso_en);
CREATE INDEX IF NOT EXISTS ix_hosp_estado ON hospitalizacion (estado) WHERE alta_en IS NULL;
-- Una cama no puede tener dos hospitalizaciones activas
CREATE UNIQUE INDEX IF NOT EXISTS uq_hosp_cama_activa ON hospitalizacion (cama_id) WHERE alta_en IS NULL AND cama_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS hospitalizacion_tarea (
    id                BIGSERIAL PRIMARY KEY,
    hospitalizacion_id BIGINT NOT NULL REFERENCES hospitalizacion (id),
    tipo              VARCHAR(15) NOT NULL,
    descripcion       VARCHAR(255) NOT NULL,
    item_id           BIGINT REFERENCES item (id),
    dosis             VARCHAR(100),
    via               VARCHAR(30),
    programada_para   TIMESTAMPTZ NOT NULL,
    estado            VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
    realizada_en      TIMESTAMPTZ,
    realizada_por     BIGINT REFERENCES usuario (id),
    lote_id           BIGINT REFERENCES lote (id),
    observaciones     VARCHAR(500),
    CONSTRAINT ck_ht_tipo   CHECK (tipo IN ('MEDICACION','FLUIDOS','ALIMENTACION','PASEO','CURACION','MONITOREO','EXAMEN','OTRA')),
    CONSTRAINT ck_ht_estado CHECK (estado IN ('PENDIENTE','REALIZADA','OMITIDA','CANCELADA'))
);
CREATE INDEX IF NOT EXISTS ix_ht_pendientes ON hospitalizacion_tarea (programada_para) WHERE estado = 'PENDIENTE';
CREATE INDEX IF NOT EXISTS ix_ht_hosp ON hospitalizacion_tarea (hospitalizacion_id);

CREATE TABLE IF NOT EXISTS hospitalizacion_monitoreo (
    id                 BIGSERIAL PRIMARY KEY,
    hospitalizacion_id BIGINT NOT NULL REFERENCES hospitalizacion (id),
    registrado_en      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    registrado_por     BIGINT REFERENCES usuario (id),
    temperatura        NUMERIC(4,1),
    frecuencia_cardiaca INT,
    frecuencia_respiratoria INT,
    presion_sistolica  INT,
    escala_dolor       SMALLINT,
    peso_kg            NUMERIC(6,2),
    ingesta_agua       VARCHAR(30),
    ingesta_alimento   VARCHAR(30),
    orina              VARCHAR(30),
    heces              VARCHAR(30),
    vomito             BOOLEAN,
    notas              TEXT,
    CONSTRAINT ck_hm_dolor CHECK (escala_dolor IS NULL OR escala_dolor BETWEEN 0 AND 10)
);
CREATE INDEX IF NOT EXISTS ix_hm_hosp ON hospitalizacion_monitoreo (hospitalizacion_id, registrado_en);

-- ---------------------------------------------------------------- CIRUGIA
CREATE TABLE IF NOT EXISTS cirugia (
    id                 BIGSERIAL PRIMARY KEY,
    codigo             VARCHAR(20) NOT NULL,
    sede_id            BIGINT REFERENCES sede (id),
    paciente_id        BIGINT NOT NULL REFERENCES paciente (id),
    consulta_id        BIGINT REFERENCES consulta (id),
    hospitalizacion_id BIGINT REFERENCES hospitalizacion (id),
    item_id            BIGINT REFERENCES item (id),
    sala_id            BIGINT REFERENCES sala (id),
    procedimiento      VARCHAR(255) NOT NULL,
    cirujano_id        BIGINT NOT NULL REFERENCES usuario (id),
    ayudante_id        BIGINT REFERENCES usuario (id),
    anestesiologo_id   BIGINT REFERENCES usuario (id),
    consentimiento_id  BIGINT REFERENCES consentimiento (id),
    programada_para    TIMESTAMPTZ,
    inicio_en          TIMESTAMPTZ,
    fin_en             TIMESTAMPTZ,
    asa                SMALLINT,
    urgencia           BOOLEAN NOT NULL DEFAULT FALSE,
    ayuno_horas        INT,
    peso_kg            NUMERIC(6,2),
    premedicacion      TEXT,
    induccion          TEXT,
    mantenimiento      TEXT,
    analgesia          TEXT,
    descripcion_tecnica TEXT,
    hallazgos          TEXT,
    complicaciones     TEXT,
    material_sutura    VARCHAR(255),
    muestras_histopatologia BOOLEAN NOT NULL DEFAULT FALSE,
    indicaciones_postoperatorias TEXT,
    estado             VARCHAR(12) NOT NULL DEFAULT 'PROGRAMADA',
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cirugia_codigo UNIQUE (codigo),
    CONSTRAINT ck_cirugia_estado CHECK (estado IN ('PROGRAMADA','EN_CURSO','REALIZADA','CANCELADA','APLAZADA')),
    CONSTRAINT ck_cirugia_asa    CHECK (asa IS NULL OR asa BETWEEN 1 AND 5),
    CONSTRAINT ck_cirugia_fechas CHECK (fin_en IS NULL OR inicio_en IS NULL OR fin_en >= inicio_en)
);
CREATE INDEX IF NOT EXISTS ix_cirugia_paciente ON cirugia (paciente_id, programada_para);
CREATE INDEX IF NOT EXISTS ix_cirugia_fecha ON cirugia (programada_para);

CREATE TABLE IF NOT EXISTS cirugia_monitoreo_anestesico (
    id          BIGSERIAL PRIMARY KEY,
    cirugia_id  BIGINT NOT NULL REFERENCES cirugia (id) ON DELETE CASCADE,
    registrado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    frecuencia_cardiaca INT,
    frecuencia_respiratoria INT,
    spo2        NUMERIC(4,1),
    etco2       NUMERIC(4,1),
    presion_sistolica INT,
    temperatura NUMERIC(4,1),
    agente_inhalatorio_pct NUMERIC(3,1),
    fluido_ml_h NUMERIC(6,1),
    notas       VARCHAR(500)
);
CREATE INDEX IF NOT EXISTS ix_cma_cirugia ON cirugia_monitoreo_anestesico (cirugia_id, registrado_en);

-- ---------------------------------------------------------------- LABORATORIO
CREATE TABLE IF NOT EXISTS examen_catalogo (
    id          BIGSERIAL PRIMARY KEY,
    item_id     BIGINT REFERENCES item (id),
    codigo      VARCHAR(30) NOT NULL,
    nombre      VARCHAR(200) NOT NULL,
    tipo        VARCHAR(10) NOT NULL DEFAULT 'LAB',
    muestra     VARCHAR(60),
    requiere_ayuno BOOLEAN NOT NULL DEFAULT FALSE,
    tiempo_entrega_horas INT,
    es_externo  BOOLEAN NOT NULL DEFAULT FALSE,
    activo      BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_examen_codigo UNIQUE (codigo),
    CONSTRAINT ck_examen_tipo CHECK (tipo IN ('LAB','IMAGEN','PATOLOGIA','FUNCIONAL'))
);

CREATE TABLE IF NOT EXISTS examen_parametro (
    id         BIGSERIAL PRIMARY KEY,
    examen_id  BIGINT NOT NULL REFERENCES examen_catalogo (id) ON DELETE CASCADE,
    codigo     VARCHAR(20) NOT NULL,
    nombre     VARCHAR(120) NOT NULL,
    unidad     VARCHAR(20),
    tipo_valor VARCHAR(10) NOT NULL DEFAULT 'NUMERICO',
    orden      INT NOT NULL DEFAULT 1,
    CONSTRAINT uq_parametro UNIQUE (examen_id, codigo),
    CONSTRAINT ck_parametro_tipo CHECK (tipo_valor IN ('NUMERICO','TEXTO','CUALITATIVO'))
);

-- Rango de referencia: el mas especifico (especie+sexo+edad) gana. Valores orientativos; validar con el laboratorio.
CREATE TABLE IF NOT EXISTS rango_referencia (
    id            BIGSERIAL PRIMARY KEY,
    parametro_id  BIGINT NOT NULL REFERENCES examen_parametro (id) ON DELETE CASCADE,
    especie_id    BIGINT NOT NULL REFERENCES especie (id),
    sexo          CHAR(1),
    edad_min_meses INT,
    edad_max_meses INT,
    valor_min     NUMERIC(12,3),
    valor_max     NUMERIC(12,3),
    critico_min   NUMERIC(12,3),
    critico_max   NUMERIC(12,3),
    fuente        VARCHAR(100),
    CONSTRAINT ck_rango_sexo CHECK (sexo IS NULL OR sexo IN ('M','F')),
    CONSTRAINT ck_rango_vals CHECK (valor_min IS NULL OR valor_max IS NULL OR valor_max >= valor_min),
    CONSTRAINT ck_rango_edad CHECK (edad_min_meses IS NULL OR edad_max_meses IS NULL OR edad_max_meses >= edad_min_meses)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_rango_ref ON rango_referencia (parametro_id, especie_id, COALESCE(sexo, '*'), COALESCE(edad_min_meses, -1));

CREATE TABLE IF NOT EXISTS orden_examen (
    id            BIGSERIAL PRIMARY KEY,
    codigo        VARCHAR(20) NOT NULL,
    sede_id       BIGINT REFERENCES sede (id),
    paciente_id   BIGINT NOT NULL REFERENCES paciente (id),
    consulta_id   BIGINT REFERENCES consulta (id),
    hospitalizacion_id BIGINT REFERENCES hospitalizacion (id),
    solicitado_por BIGINT NOT NULL REFERENCES usuario (id),
    prioridad     VARCHAR(8) NOT NULL DEFAULT 'RUTINA',
    estado        VARCHAR(12) NOT NULL DEFAULT 'SOLICITADA',
    laboratorio_externo VARCHAR(150),
    indicacion_clinica  TEXT,
    solicitada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    muestra_tomada_en TIMESTAMPTZ,
    completada_en TIMESTAMPTZ,
    CONSTRAINT uq_orden_examen_codigo UNIQUE (codigo),
    CONSTRAINT ck_oe_prioridad CHECK (prioridad IN ('RUTINA','URGENTE','STAT')),
    CONSTRAINT ck_oe_estado CHECK (estado IN ('SOLICITADA','MUESTRA_TOMADA','EN_PROCESO','COMPLETADA','CANCELADA'))
);
CREATE INDEX IF NOT EXISTS ix_oe_paciente ON orden_examen (paciente_id, solicitada_en);
CREATE INDEX IF NOT EXISTS ix_oe_estado ON orden_examen (estado) WHERE estado <> 'COMPLETADA';

CREATE TABLE IF NOT EXISTS orden_examen_item (
    id          BIGSERIAL PRIMARY KEY,
    orden_id    BIGINT NOT NULL REFERENCES orden_examen (id) ON DELETE CASCADE,
    examen_id   BIGINT NOT NULL REFERENCES examen_catalogo (id),
    estado      VARCHAR(12) NOT NULL DEFAULT 'SOLICITADO',
    interpretacion TEXT,
    validado_por BIGINT REFERENCES usuario (id),
    validado_en TIMESTAMPTZ,
    CONSTRAINT uq_oei UNIQUE (orden_id, examen_id),
    CONSTRAINT ck_oei_estado CHECK (estado IN ('SOLICITADO','EN_PROCESO','RESULTADO','VALIDADO','CANCELADO'))
);

CREATE TABLE IF NOT EXISTS resultado_examen (
    id              BIGSERIAL PRIMARY KEY,
    orden_item_id   BIGINT NOT NULL REFERENCES orden_examen_item (id) ON DELETE CASCADE,
    parametro_id    BIGINT NOT NULL REFERENCES examen_parametro (id),
    valor_numerico  NUMERIC(14,4),
    valor_texto     VARCHAR(255),
    unidad          VARCHAR(20),
    -- copia (snapshot) del rango aplicado al momento del resultado
    ref_min         NUMERIC(12,3),
    ref_max         NUMERIC(12,3),
    bandera         VARCHAR(2),
    registrado_en   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    registrado_por  BIGINT REFERENCES usuario (id),
    origen          VARCHAR(12) NOT NULL DEFAULT 'MANUAL',
    CONSTRAINT uq_resultado UNIQUE (orden_item_id, parametro_id),
    CONSTRAINT ck_resultado_bandera CHECK (bandera IS NULL OR bandera IN ('N','L','H','LL','HH','A')),
    CONSTRAINT ck_resultado_origen CHECK (origen IN ('MANUAL','ARCHIVO','INTEGRACION')),
    CONSTRAINT ck_resultado_valor CHECK (valor_numerico IS NOT NULL OR valor_texto IS NOT NULL)
);
COMMENT ON COLUMN resultado_examen.bandera IS 'N normal, L bajo, H alto, LL/HH critico, A anormal cualitativo.';

ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS orden_examen_id    BIGINT REFERENCES orden_examen (id);
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS cirugia_id         BIGINT REFERENCES cirugia (id);
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS hospitalizacion_id BIGINT REFERENCES hospitalizacion (id);
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS hash_sha256        VARCHAR(64);
CREATE INDEX IF NOT EXISTS ix_adjunto_paciente ON adjunto (paciente_id, subido_en);

-- ---------------------------------------------------------------- SEMILLAS
INSERT INTO cama (sede_id, codigo, tipo, tamano)
SELECT s.id, v.codigo, v.tipo, v.tam FROM sede s
CROSS JOIN (VALUES ('J-01','JAULA','PEQUENA'),('J-02','JAULA','PEQUENA'),('J-03','JAULA','MEDIANA'),
                   ('C-01','CANIL','GRANDE'),('UCI-01','UCI','MEDIANA'),('AIS-01','AISLAMIENTO','MEDIANA')) AS v(codigo, tipo, tam)
WHERE s.es_principal
ON CONFLICT (sede_id, codigo) DO NOTHING;

INSERT INTO examen_catalogo (item_id, codigo, nombre, tipo, muestra, requiere_ayuno, tiempo_entrega_horas)
SELECT (SELECT id FROM item WHERE codigo = v.item), v.codigo, v.nombre, v.tipo, v.muestra, v.ayuno, v.horas
FROM (VALUES
 ('LAB-HEMO', 'HEMO', 'Hemograma completo',       'LAB', 'Sangre EDTA',       FALSE, 2),
 ('LAB-QUIM', 'QUIM', 'Perfil bioquimico basico', 'LAB', 'Suero / heparina',  TRUE,  4)
) AS v(item, codigo, nombre, tipo, muestra, ayuno, horas)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO examen_parametro (examen_id, codigo, nombre, unidad, orden)
SELECT e.id, v.codigo, v.nombre, v.unidad, v.orden
FROM (VALUES
 ('HEMO','HCT',  'Hematocrito',            '%',        1),
 ('HEMO','HGB',  'Hemoglobina',            'g/dL',     2),
 ('HEMO','RBC',  'Globulos rojos',         'x10^6/uL', 3),
 ('HEMO','WBC',  'Leucocitos',             'x10^3/uL', 4),
 ('HEMO','PLT',  'Plaquetas',              'x10^3/uL', 5),
 ('QUIM','GLU',  'Glucosa',                'mg/dL',    1),
 ('QUIM','BUN',  'Nitrogeno ureico (BUN)', 'mg/dL',    2),
 ('QUIM','CREA', 'Creatinina',             'mg/dL',    3),
 ('QUIM','ALT',  'ALT (TGP)',              'U/L',      4),
 ('QUIM','ALP',  'Fosfatasa alcalina',     'U/L',      5),
 ('QUIM','TP',   'Proteinas totales',      'g/dL',     6),
 ('QUIM','ALB',  'Albumina',               'g/dL',     7)
) AS v(ex, codigo, nombre, unidad, orden)
JOIN examen_catalogo e ON e.codigo = v.ex
ON CONFLICT (examen_id, codigo) DO NOTHING;

-- Rangos orientativos adultos (referencias generales de laboratorios veterinarios; verificar con el equipo propio)
INSERT INTO rango_referencia (parametro_id, especie_id, valor_min, valor_max, fuente)
SELECT p.id, (SELECT id FROM especie WHERE nombre = v.esp), v.vmin, v.vmax, 'Orientativo adultos (validar con laboratorio)'
FROM (VALUES
 ('HCT','Perro',37,55),   ('HCT','Gato',30,45),
 ('HGB','Perro',12,18),   ('HGB','Gato',9.8,15.4),
 ('RBC','Perro',5.5,8.5), ('RBC','Gato',5,10),
 ('WBC','Perro',6,17),    ('WBC','Gato',5.5,19.5),
 ('PLT','Perro',200,500), ('PLT','Gato',300,800),
 ('GLU','Perro',70,143),  ('GLU','Gato',74,159),
 ('BUN','Perro',7,27),    ('BUN','Gato',16,36),
 ('CREA','Perro',0.5,1.8),('CREA','Gato',0.8,2.4),
 ('ALT','Perro',10,100),  ('ALT','Gato',12,130),
 ('ALP','Perro',23,212),  ('ALP','Gato',14,111),
 ('TP','Perro',5.2,8.2),  ('TP','Gato',5.7,8.9),
 ('ALB','Perro',2.3,4.0), ('ALB','Gato',2.2,4.0)
) AS v(par, esp, vmin, vmax)
JOIN examen_parametro p ON p.codigo = v.par
ON CONFLICT DO NOTHING;
