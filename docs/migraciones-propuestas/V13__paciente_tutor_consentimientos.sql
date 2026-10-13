-- ============================================================================
-- V13 (PROPUESTA) - Extension de paciente/tutor: identificacion, pedigri, fallecimiento,
-- preferencias de contacto (Ley 1581/2012), consentimientos y problemas activos.
-- Nota: paciente ya tiene microchip, esterilizado, fallecido, color (V1).
-- ============================================================================
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS microchip_fecha_implante DATE;
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS microchip_ubicacion       VARCHAR(60);
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS tatuaje                   VARCHAR(40);
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS pasaporte_numero          VARCHAR(40);
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS pedigri_registro          VARCHAR(60);
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS pedigri_entidad           VARCHAR(100);
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS criador                   VARCHAR(150);
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS padre_id                  BIGINT REFERENCES paciente (id);
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS madre_id                  BIGINT REFERENCES paciente (id);
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS senas_particulares        TEXT;
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS patron_pelaje             VARCHAR(60);
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS foto_ruta                 VARCHAR(500);
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS fecha_nacimiento_aproximada BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS fecha_esterilizacion      DATE;
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS origen                    VARCHAR(20);
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS fecha_fallecimiento       DATE;
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS causa_fallecimiento       VARCHAR(255);
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS agresivo                  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS fusionado_en_id           BIGINT REFERENCES paciente (id);
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_paciente_origen') THEN
    ALTER TABLE paciente ADD CONSTRAINT ck_paciente_origen
      CHECK (origen IS NULL OR origen IN ('CRIADERO','ADOPCION','RESCATE','REGALO','COMPRA','NACIDO_EN_CASA','OTRO'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_paciente_fallecimiento') THEN
    ALTER TABLE paciente ADD CONSTRAINT ck_paciente_fallecimiento
      CHECK (fecha_fallecimiento IS NULL OR fallecido);
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_paciente_nombre_trgm ON paciente USING gin (nombre gin_trgm_ops);
CREATE INDEX IF NOT EXISTS ix_paciente_padres      ON paciente (padre_id, madre_id);

ALTER TABLE tutor ADD COLUMN IF NOT EXISTS fecha_nacimiento          DATE;
ALTER TABLE tutor ADD COLUMN IF NOT EXISTS tipo_persona              VARCHAR(10) NOT NULL DEFAULT 'NATURAL';
ALTER TABLE tutor ADD COLUMN IF NOT EXISTS digito_verificacion       CHAR(1);
ALTER TABLE tutor ADD COLUMN IF NOT EXISTS codigo_dane_municipio     VARCHAR(10);
ALTER TABLE tutor ADD COLUMN IF NOT EXISTS responsabilidad_fiscal    VARCHAR(20);
ALTER TABLE tutor ADD COLUMN IF NOT EXISTS preferencia_contacto      VARCHAR(10) NOT NULL DEFAULT 'WHATSAPP';
ALTER TABLE tutor ADD COLUMN IF NOT EXISTS acepta_email              BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tutor ADD COLUMN IF NOT EXISTS acepta_whatsapp           BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tutor ADD COLUMN IF NOT EXISTS acepta_sms                BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tutor ADD COLUMN IF NOT EXISTS autoriza_tratamiento_datos BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tutor ADD COLUMN IF NOT EXISTS fecha_autorizacion_datos  TIMESTAMPTZ;
ALTER TABLE tutor ADD COLUMN IF NOT EXISTS fusionado_en_id           BIGINT REFERENCES tutor (id);
ALTER TABLE tutor ADD COLUMN IF NOT EXISTS bloqueado_cartera         BOOLEAN NOT NULL DEFAULT FALSE;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tutor_tipo_persona') THEN
    ALTER TABLE tutor ADD CONSTRAINT ck_tutor_tipo_persona CHECK (tipo_persona IN ('NATURAL','JURIDICA'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_tutor_pref_contacto') THEN
    ALTER TABLE tutor ADD CONSTRAINT ck_tutor_pref_contacto CHECK (preferencia_contacto IN ('WHATSAPP','EMAIL','SMS','LLAMADA'));
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_tutor_nombres_trgm   ON tutor USING gin ((nombres || ' ' || COALESCE(apellidos, '')) gin_trgm_ops);
CREATE INDEX IF NOT EXISTS ix_tutor_telefono       ON tutor (telefono);

ALTER TABLE alergia ADD COLUMN IF NOT EXISTS tipo           VARCHAR(15) NOT NULL DEFAULT 'OTRA';
ALTER TABLE alergia ADD COLUMN IF NOT EXISTS reaccion       VARCHAR(255);
ALTER TABLE alergia ADD COLUMN IF NOT EXISTS registrada_en  DATE DEFAULT CURRENT_DATE;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_alergia_tipo') THEN
    ALTER TABLE alergia ADD CONSTRAINT ck_alergia_tipo CHECK (tipo IN ('MEDICAMENTO','ALIMENTO','AMBIENTAL','VACUNA','OTRA'));
  END IF;
END $$;

-- ---------------------------------------------------------------- CONSENTIMIENTOS
CREATE TABLE IF NOT EXISTS consentimiento_plantilla (
    id          BIGSERIAL PRIMARY KEY,
    codigo      VARCHAR(40)  NOT NULL,
    tipo        VARCHAR(25)  NOT NULL,
    titulo      VARCHAR(200) NOT NULL,
    texto       TEXT         NOT NULL,
    version     INT          NOT NULL DEFAULT 1,
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_consent_plantilla UNIQUE (codigo, version),
    CONSTRAINT ck_consent_plantilla_tipo CHECK (tipo IN
      ('HABEAS_DATA','PROCEDIMIENTO','CIRUGIA_ANESTESIA','HOSPITALIZACION','EUTANASIA','NECROPSIA','CONTACTO_MARKETING','GUARDERIA','TELEMEDICINA','GRABACION'))
);
COMMENT ON TABLE consentimiento_plantilla IS 'Textos versionados de consentimiento. El texto firmado se copia a consentimiento.texto_firmado.';

CREATE TABLE IF NOT EXISTS consentimiento (
    id            BIGSERIAL PRIMARY KEY,
    plantilla_id  BIGINT REFERENCES consentimiento_plantilla (id),
    tipo          VARCHAR(25) NOT NULL,
    tutor_id      BIGINT NOT NULL REFERENCES tutor (id),
    paciente_id   BIGINT REFERENCES paciente (id),
    consulta_id   BIGINT REFERENCES consulta (id),
    sede_id       BIGINT REFERENCES sede (id),
    estado        VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
    texto_firmado TEXT,
    firmante_nombre VARCHAR(255),
    firmante_documento VARCHAR(30),
    firma_ruta    VARCHAR(500),
    firma_hash    VARCHAR(64),
    metodo_firma  VARCHAR(15),
    ip            VARCHAR(45),
    firmado_en    TIMESTAMPTZ,
    revocado_en   TIMESTAMPTZ,
    motivo_revocacion VARCHAR(255),
    registrado_por BIGINT REFERENCES usuario (id),
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_consent_estado CHECK (estado IN ('PENDIENTE','FIRMADO','RECHAZADO','REVOCADO')),
    CONSTRAINT ck_consent_metodo CHECK (metodo_firma IS NULL OR metodo_firma IN ('TABLET','PAPEL_ESCANEADO','OTP','ENLACE')),
    CONSTRAINT ck_consent_firmado CHECK (estado <> 'FIRMADO' OR (firmado_en IS NOT NULL AND texto_firmado IS NOT NULL))
);
CREATE INDEX IF NOT EXISTS ix_consent_tutor    ON consentimiento (tutor_id, tipo);
CREATE INDEX IF NOT EXISTS ix_consent_paciente ON consentimiento (paciente_id);

-- ---------------------------------------------------------------- LISTA DE PROBLEMAS
CREATE TABLE IF NOT EXISTS problema_activo (
    id           BIGSERIAL PRIMARY KEY,
    paciente_id  BIGINT NOT NULL REFERENCES paciente (id),
    descripcion  VARCHAR(255) NOT NULL,
    codigo       VARCHAR(30),
    tipo         VARCHAR(10) NOT NULL DEFAULT 'AGUDO',
    estado       VARCHAR(10) NOT NULL DEFAULT 'ACTIVO',
    desde        DATE NOT NULL DEFAULT CURRENT_DATE,
    resuelto_en  DATE,
    consulta_id  BIGINT REFERENCES consulta (id),
    notas        TEXT,
    creado_por   BIGINT REFERENCES usuario (id),
    creado_en    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_problema_tipo   CHECK (tipo IN ('AGUDO','CRONICO')),
    CONSTRAINT ck_problema_estado CHECK (estado IN ('ACTIVO','RESUELTO','EN_CONTROL'))
);
CREATE INDEX IF NOT EXISTS ix_problema_paciente ON problema_activo (paciente_id, estado);
COMMENT ON TABLE problema_activo IS 'Lista de problemas del paciente (historia orientada a problemas), visible en el encabezado de la historia.';

-- ---------------------------------------------------------------- SEMILLA consentimientos
INSERT INTO consentimiento_plantilla (codigo, tipo, titulo, texto) VALUES
 ('HABEAS_DATA', 'HABEAS_DATA', 'Autorizacion de tratamiento de datos personales',
  'Autorizo de manera previa, expresa e informada el tratamiento de mis datos personales y los de mi mascota por parte de la clinica, para fines de atencion veterinaria, facturacion y comunicaciones del servicio, conforme a la Ley 1581 de 2012. Conozco mi derecho a conocer, actualizar, rectificar y suprimir mis datos. (Texto de ejemplo: validar con asesor juridico.)'),
 ('CIRUGIA_ANESTESIA', 'CIRUGIA_ANESTESIA', 'Consentimiento informado de cirugia y anestesia',
  'Declaro haber sido informado de la naturaleza del procedimiento, sus riesgos (incluido el riesgo anestesico), alternativas y costos estimados, y autorizo su realizacion. (Texto de ejemplo: validar con asesor juridico.)'),
 ('HOSPITALIZACION', 'HOSPITALIZACION', 'Consentimiento de hospitalizacion',
  'Autorizo la hospitalizacion de mi mascota y los procedimientos diagnosticos y terapeuticos necesarios, y acepto mantener actualizados mis datos de contacto. (Texto de ejemplo.)'),
 ('EUTANASIA', 'EUTANASIA', 'Consentimiento de eutanasia',
  'Solicito y autorizo de manera libre e informada la eutanasia de mi mascota. (Texto de ejemplo: validar con asesor juridico.)'),
 ('PROCEDIMIENTO', 'PROCEDIMIENTO', 'Consentimiento de procedimiento diagnostico/terapeutico',
  'Autorizo el procedimiento descrito por el medico veterinario tras haber recibido la informacion de riesgos y alternativas. (Texto de ejemplo.)'),
 ('CONTACTO_MARKETING', 'CONTACTO_MARKETING', 'Autorizacion de comunicaciones promocionales',
  'Autorizo recibir comunicaciones promocionales por los canales marcados. Puedo revocar esta autorizacion en cualquier momento. (Texto de ejemplo.)'),
 ('GUARDERIA', 'GUARDERIA', 'Condiciones de guarderia / hotel',
  'Acepto las condiciones del servicio de guarderia y autorizo atencion veterinaria de urgencia por cuenta mia. (Texto de ejemplo.)')
ON CONFLICT (codigo, version) DO NOTHING;
