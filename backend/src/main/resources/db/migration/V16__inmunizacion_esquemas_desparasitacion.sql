-- ============================================================================
-- V16 (PROPUESTA) - Inmunizacion: clasificacion de vacunas, esquemas por especie/edad (WSAVA 2024),
-- aplicacion trazable al lote, desparasitacion y certificado/carnet.
-- Fuente de esquemas: WSAVA Vaccination Guidelines 2024 (nucleo: moquillo, adenovirus, parvovirus;
-- gato: panleucopenia, calicivirus, herpesvirus-1; rabia donde sea endemica). Ajustar a criterio clinico.
-- ============================================================================
ALTER TABLE vacuna ADD COLUMN IF NOT EXISTS clasificacion          VARCHAR(12) NOT NULL DEFAULT 'NO_NUCLEO';
ALTER TABLE vacuna ADD COLUMN IF NOT EXISTS enfermedades_objetivo  VARCHAR(255);
ALTER TABLE vacuna ADD COLUMN IF NOT EXISTS fabricante             VARCHAR(120);
ALTER TABLE vacuna ADD COLUMN IF NOT EXISTS via_administracion     VARCHAR(20);
ALTER TABLE vacuna ADD COLUMN IF NOT EXISTS dosis_ml               NUMERIC(4,2);
ALTER TABLE vacuna ADD COLUMN IF NOT EXISTS item_id                BIGINT REFERENCES item (id);
ALTER TABLE vacuna ADD COLUMN IF NOT EXISTS obligatoria_ley        BOOLEAN NOT NULL DEFAULT FALSE;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_vacuna_clasificacion') THEN
    ALTER TABLE vacuna ADD CONSTRAINT ck_vacuna_clasificacion CHECK (clasificacion IN ('NUCLEO','NO_NUCLEO','NO_RECOMENDADA'));
  END IF;
END $$;

-- Especies base (idempotente: la app puede haberlas sembrado ya)
INSERT INTO especie (nombre) VALUES ('Perro'), ('Gato'), ('Conejo'), ('Ave'), ('Roedor'), ('Reptil'), ('Equino'), ('Otro')
ON CONFLICT (nombre) DO NOTHING;

-- Vacunas (periodo_eficacia_dias = intervalo de revacunacion tras el refuerzo)
INSERT INTO vacuna (codigo, nombre, especie_id, periodo_eficacia_dias, clasificacion, enfermedades_objetivo, via_administracion, obligatoria_ley)
SELECT v.codigo, v.nombre, (SELECT id FROM especie WHERE nombre = v.esp), v.dias, v.clas, v.enf, v.via, v.ley
FROM (VALUES
 ('CAN-DHPP',  'Polivalente canina (Moquillo, Adenovirus, Parvovirus +/- Parainfluenza)', 'Perro', 1095, 'NUCLEO',    'Moquillo, hepatitis infecciosa canina, parvovirosis (CDV, CAV-2, CPV-2)', 'SC', FALSE),
 ('CAN-RAB',   'Antirrabica canina',            'Perro', 365,  'NUCLEO',    'Rabia (endemica: considerada nucleo; validar normativa sanitaria vigente)', 'SC', TRUE),
 ('CAN-LEPTO', 'Leptospira canina (4 serovares)','Perro', 365,  'NO_NUCLEO', 'Leptospirosis', 'SC', FALSE),
 ('CAN-BORD',  'Bordetella / Parainfluenza canina','Perro', 365, 'NO_NUCLEO', 'Tos de las perreras', 'IN', FALSE),
 ('CAN-LYME',  'Borrelia (Lyme) canina',        'Perro', 365,  'NO_NUCLEO', 'Borreliosis', 'SC', FALSE),
 ('CAN-CIV',   'Influenza canina',              'Perro', 365,  'NO_NUCLEO', 'Influenza canina', 'SC', FALSE),
 ('FEL-FVRCP', 'Triple felina (Panleucopenia, Calicivirus, Herpesvirus-1)', 'Gato', 1095, 'NUCLEO', 'Panleucopenia, calicivirus, rinotraqueitis (FPV, FCV, FHV-1)', 'SC', FALSE),
 ('FEL-RAB',   'Antirrabica felina',            'Gato',  365,  'NUCLEO',    'Rabia (endemica: considerada nucleo; validar normativa sanitaria vigente)', 'SC', TRUE),
 ('FEL-FELV',  'Leucemia felina (FeLV)',        'Gato',  365,  'NO_NUCLEO', 'Leucemia felina (nucleo en <1 anio y gatos con acceso exterior)', 'SC', FALSE),
 ('FEL-CLAM',  'Chlamydia felis',               'Gato',  365,  'NO_NUCLEO', 'Clamidiosis felina', 'SC', FALSE)
) AS v(codigo, nombre, esp, dias, clas, enf, via, ley)
ON CONFLICT (codigo) DO NOTHING;

-- ---------------------------------------------------------------- ESQUEMAS
CREATE TABLE IF NOT EXISTS esquema_vacunal (
    id          BIGSERIAL PRIMARY KEY,
    codigo      VARCHAR(40)  NOT NULL,
    nombre      VARCHAR(200) NOT NULL,
    vacuna_id   BIGINT NOT NULL REFERENCES vacuna (id),
    especie_id  BIGINT REFERENCES especie (id),
    fuente      VARCHAR(120),
    notas       TEXT,
    activo      BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_esquema_codigo UNIQUE (codigo)
);

CREATE TABLE IF NOT EXISTS esquema_vacunal_dosis (
    id                    BIGSERIAL PRIMARY KEY,
    esquema_id            BIGINT NOT NULL REFERENCES esquema_vacunal (id) ON DELETE CASCADE,
    orden                 INT    NOT NULL,
    nombre_dosis          VARCHAR(60) NOT NULL,
    edad_minima_semanas   INT,
    intervalo_dias_previa INT,                 -- dias desde la dosis anterior (NULL en la primera)
    es_refuerzo           BOOLEAN NOT NULL DEFAULT FALSE,
    repetir_cada_dias     INT,                 -- si no es NULL, la dosis se repite indefinidamente con este intervalo
    CONSTRAINT uq_esquema_dosis UNIQUE (esquema_id, orden),
    CONSTRAINT ck_esq_dosis_vals CHECK (orden > 0 AND COALESCE(edad_minima_semanas, 0) >= 0
                                        AND COALESCE(intervalo_dias_previa, 0) >= 0 AND COALESCE(repetir_cada_dias, 1) > 0)
);
COMMENT ON TABLE esquema_vacunal_dosis IS 'Calendario: la dosis N+1 se programa intervalo_dias_previa tras la N, respetando edad_minima_semanas.';

INSERT INTO esquema_vacunal (codigo, nombre, vacuna_id, especie_id, fuente)
SELECT v.cod, v.nom, va.id, va.especie_id, 'WSAVA 2024 (adaptado)'
FROM (VALUES
 ('ESQ-CAN-DHPP',  'Cachorro: polivalente nucleo',   'CAN-DHPP'),
 ('ESQ-CAN-RAB',   'Perro: antirrabica',             'CAN-RAB'),
 ('ESQ-CAN-LEPTO', 'Perro: leptospira',              'CAN-LEPTO'),
 ('ESQ-CAN-BORD',  'Perro: bordetella',              'CAN-BORD'),
 ('ESQ-FEL-FVRCP', 'Gatito: triple felina nucleo',   'FEL-FVRCP'),
 ('ESQ-FEL-RAB',   'Gato: antirrabica',              'FEL-RAB'),
 ('ESQ-FEL-FELV',  'Gato: leucemia felina',          'FEL-FELV')
) AS v(cod, nom, vcod) JOIN vacuna va ON va.codigo = v.vcod
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO esquema_vacunal_dosis (esquema_id, orden, nombre_dosis, edad_minima_semanas, intervalo_dias_previa, es_refuerzo, repetir_cada_dias)
SELECT e.id, d.orden, d.nombre, d.semanas, d.intervalo, d.refuerzo, d.repetir
FROM (VALUES
 -- Serie nucleo cachorro/gatito: dosis cada 2-4 semanas, ultima a las >=16 semanas; refuerzo a los 6-12 meses; luego cada 3 anios
 ('ESQ-CAN-DHPP', 1, 'Dosis 1',            6,  NULL, FALSE, NULL),
 ('ESQ-CAN-DHPP', 2, 'Dosis 2',            9,  21,   FALSE, NULL),
 ('ESQ-CAN-DHPP', 3, 'Dosis 3',           12,  21,   FALSE, NULL),
 ('ESQ-CAN-DHPP', 4, 'Dosis 4 (>=16 sem)',16,  28,   FALSE, NULL),
 ('ESQ-CAN-DHPP', 5, 'Refuerzo 12 meses', NULL,365,  TRUE,  1095),
 ('ESQ-FEL-FVRCP',1, 'Dosis 1',            6,  NULL, FALSE, NULL),
 ('ESQ-FEL-FVRCP',2, 'Dosis 2',            9,  21,   FALSE, NULL),
 ('ESQ-FEL-FVRCP',3, 'Dosis 3',           12,  21,   FALSE, NULL),
 ('ESQ-FEL-FVRCP',4, 'Dosis 4 (>=16 sem)',16,  28,   FALSE, NULL),
 ('ESQ-FEL-FVRCP',5, 'Refuerzo 12 meses', NULL,365,  TRUE,  1095),
 -- Rabia: primovacunacion desde 12 semanas, refuerzo anual (practica local)
 ('ESQ-CAN-RAB',  1, 'Primovacunacion',   12,  NULL, FALSE, NULL),
 ('ESQ-CAN-RAB',  2, 'Refuerzo anual',    NULL,365,  TRUE,  365),
 ('ESQ-FEL-RAB',  1, 'Primovacunacion',   12,  NULL, FALSE, NULL),
 ('ESQ-FEL-RAB',  2, 'Refuerzo anual',    NULL,365,  TRUE,  365),
 -- Leptospira (2 dosis separadas 2-4 sem, desde 8-9 sem) y refuerzo anual
 ('ESQ-CAN-LEPTO',1, 'Dosis 1',            8,  NULL, FALSE, NULL),
 ('ESQ-CAN-LEPTO',2, 'Dosis 2',           12,  28,   FALSE, NULL),
 ('ESQ-CAN-LEPTO',3, 'Refuerzo anual',    NULL,365,  TRUE,  365),
 -- Bordetella
 ('ESQ-CAN-BORD', 1, 'Dosis 1',            8,  NULL, FALSE, NULL),
 ('ESQ-CAN-BORD', 2, 'Refuerzo anual',    NULL,365,  TRUE,  365),
 -- FeLV: 2 dosis desde 8 sem (3-4 sem de separacion), refuerzo a 12 meses, luego anual si hay riesgo
 ('ESQ-FEL-FELV', 1, 'Dosis 1',            8,  NULL, FALSE, NULL),
 ('ESQ-FEL-FELV', 2, 'Dosis 2',           12,  28,   FALSE, NULL),
 ('ESQ-FEL-FELV', 3, 'Refuerzo 12 meses', NULL,365,  TRUE,  365)
) AS d(esq, orden, nombre, semanas, intervalo, refuerzo, repetir)
JOIN esquema_vacunal e ON e.codigo = d.esq
ON CONFLICT (esquema_id, orden) DO NOTHING;

-- ---------------------------------------------------------------- inmunizacion: trazabilidad
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS dosis_numero      INT;
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS esquema_dosis_id  BIGINT REFERENCES esquema_vacunal_dosis (id);
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS lote_id           BIGINT REFERENCES lote (id);
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS fecha_vencimiento_lote DATE;
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS sitio_aplicacion  VARCHAR(40);
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS via_administracion VARCHAR(20);
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS peso_kg           NUMERIC(6,2);
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS estado            VARCHAR(12) NOT NULL DEFAULT 'APLICADA';
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS reaccion_adversa  TEXT;
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS externa           BOOLEAN NOT NULL DEFAULT FALSE; -- aplicada en otra clinica
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_inm_estado') THEN
    ALTER TABLE inmunizacion ADD CONSTRAINT ck_inm_estado CHECK (estado IN ('APLICADA','OMITIDA','RECHAZADA','ANULADA'));
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_inm_paciente_vacuna ON inmunizacion (paciente_id, vacuna_id, fecha_aplicacion);

-- ---------------------------------------------------------------- DESPARASITACION
CREATE TABLE IF NOT EXISTS desparasitacion (
    id               BIGSERIAL PRIMARY KEY,
    paciente_id      BIGINT NOT NULL REFERENCES paciente (id),
    tipo             VARCHAR(12) NOT NULL,
    item_id          BIGINT REFERENCES item (id),
    producto         VARCHAR(255) NOT NULL,
    principio_activo VARCHAR(150),
    dosis            VARCHAR(80),
    peso_kg          NUMERIC(6,2),
    fecha_aplicacion DATE NOT NULL,
    proxima_fecha    DATE,
    lote_id          BIGINT REFERENCES lote (id),
    consulta_id      BIGINT REFERENCES consulta (id),
    veterinario_id   BIGINT REFERENCES usuario (id),
    sede_id          BIGINT REFERENCES sede (id),
    observaciones    TEXT,
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_despar_tipo CHECK (tipo IN ('INTERNA','EXTERNA','DIROFILARIA','COMBINADA')),
    CONSTRAINT ck_despar_fechas CHECK (proxima_fecha IS NULL OR proxima_fecha >= fecha_aplicacion)
);
CREATE INDEX IF NOT EXISTS ix_despar_paciente ON desparasitacion (paciente_id, fecha_aplicacion);
CREATE INDEX IF NOT EXISTS ix_despar_proxima  ON desparasitacion (proxima_fecha);

-- ---------------------------------------------------------------- CERTIFICADO / CARNET
CREATE TABLE IF NOT EXISTS certificado_vacunacion (
    id          BIGSERIAL PRIMARY KEY,
    numero      VARCHAR(30) NOT NULL,
    paciente_id BIGINT NOT NULL REFERENCES paciente (id),
    tipo        VARCHAR(15) NOT NULL DEFAULT 'CARNET',
    emitido_por BIGINT REFERENCES usuario (id),
    emitido_en  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valido_hasta DATE,
    pdf_ruta    VARCHAR(500),
    hash_documento VARCHAR(64),
    CONSTRAINT uq_cert_numero UNIQUE (numero),
    CONSTRAINT ck_cert_tipo CHECK (tipo IN ('CARNET','CERTIFICADO_VIAJE','CERTIFICADO_SALUD'))
);
CREATE INDEX IF NOT EXISTS ix_cert_paciente ON certificado_vacunacion (paciente_id);

-- Productos antiparasitarios de ejemplo en el catalogo (sin precio; ajustar)
INSERT INTO item (codigo, nombre, tipo, categoria_id, precio_venta, controla_stock)
SELECT v.codigo, v.nombre, 'MEDICAMENTO', (SELECT id FROM categoria_item WHERE nombre = 'Antiparasitarios'), v.precio, TRUE
FROM (VALUES
 ('MED-ANT-PZQ',  'Praziquantel + pirantel + febantel (tableta, por 10 kg)', 12000),
 ('MED-ANT-ISOX', 'Isoxazolina oral (pulgas/garrapatas, por tableta)',      75000),
 ('MED-ANT-SELA', 'Selamectina pipeta (por unidad)',                         55000),
 ('MED-ANT-IVER', 'Ivermectina inyectable (por ml)',                          8000)
) AS v(codigo, nombre, precio)
ON CONFLICT (codigo) DO NOTHING;
