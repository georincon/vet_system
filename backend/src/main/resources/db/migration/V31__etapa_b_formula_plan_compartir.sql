-- ============================================================================
-- V31 - Etapa B de documentacion clinica: formula medica y plan de tratamiento (PDF verificable),
-- controles de seguimiento, y compartir documentos con el tutor (correo / WhatsApp / enlaces seguros).
--   1) receta: numero de formula y codigo de verificacion publico (se asignan al emitir el PDF);
--      receta_item: concentracion y presentacion del medicamento.
--   2) plan_tratamiento (1 por consulta) + plan_tratamiento_item (fases) + control_seguimiento.
--      El plan y sus items son hijos de la consulta: el trigger de V15 los congela al CERRAR (cambios via addendum).
--      Los controles NO se congelan: el seguimiento nace despues del cierre (queda un addendum SEGUIMIENTO).
--   3) compartir_*: bitacora de envios al tutor, instantanea (PDF) de lo enviado, enlaces seguros con token
--      (solo se guarda el hash SHA-256) y bitacora de accesos publicos a los enlaces.
--   4) plantillas de mensaje por tipo de documento (variables {{tutor}} {{paciente}} {{clinica}} {{documento}} {{enlace}}).
-- La tarjeta profesional del veterinario ya existe (veterinario.numero_profesional, V1): se agrega API para editarla.
-- ============================================================================

-- ---------------------------------------------------------------- 1) formula
CREATE SEQUENCE IF NOT EXISTS seq_formula_medica START 1;
ALTER TABLE receta ADD COLUMN IF NOT EXISTS numero_formula      VARCHAR(30);
ALTER TABLE receta ADD COLUMN IF NOT EXISTS codigo_verificacion VARCHAR(16);
ALTER TABLE receta ADD COLUMN IF NOT EXISTS formula_emitida_en  TIMESTAMPTZ;
CREATE UNIQUE INDEX IF NOT EXISTS uq_receta_numero_formula ON receta (numero_formula) WHERE numero_formula IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_receta_codigo_verif    ON receta (codigo_verificacion) WHERE codigo_verificacion IS NOT NULL;
COMMENT ON COLUMN receta.codigo_verificacion IS 'Codigo aleatorio impreso en el PDF; GET /api/verificar/{codigo} (publico) confirma existencia, fecha, clinica y veterinario.';

ALTER TABLE receta_item ADD COLUMN IF NOT EXISTS concentracion VARCHAR(100);
ALTER TABLE receta_item ADD COLUMN IF NOT EXISTS presentacion  VARCHAR(100);

-- ---------------------------------------------------------------- 2) plan de tratamiento y controles
CREATE TABLE IF NOT EXISTS plan_tratamiento (
    id              BIGSERIAL PRIMARY KEY,
    consulta_id     BIGINT NOT NULL REFERENCES consulta (id),
    paciente_id     BIGINT NOT NULL REFERENCES paciente (id),
    objetivo        TEXT,
    recomendaciones TEXT,
    advertencias    TEXT,
    estado          VARCHAR(12) NOT NULL DEFAULT 'ACTIVO',
    creado_por      BIGINT REFERENCES usuario (id),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_plan_consulta UNIQUE (consulta_id),
    CONSTRAINT ck_plan_estado CHECK (estado IN ('ACTIVO','COMPLETADO','CANCELADO'))
);
CREATE INDEX IF NOT EXISTS ix_plan_paciente ON plan_tratamiento (paciente_id);

CREATE TABLE IF NOT EXISTS plan_tratamiento_item (
    id           BIGSERIAL PRIMARY KEY,
    plan_id      BIGINT NOT NULL REFERENCES plan_tratamiento (id) ON DELETE CASCADE,
    consulta_id  BIGINT NOT NULL REFERENCES consulta (id),   -- denormalizado para el trigger de inmutabilidad
    orden        INT NOT NULL DEFAULT 1,
    fase         VARCHAR(100),
    tipo         VARCHAR(15) NOT NULL DEFAULT 'OTRO',
    descripcion  TEXT NOT NULL,
    fecha_inicio DATE,
    fecha_fin    DATE,
    CONSTRAINT ck_plan_item_tipo CHECK (tipo IN ('MEDICACION','PROCEDIMIENTO','DIETA','CUIDADO','EXAMEN','CONTROL','OTRO')),
    CONSTRAINT ck_plan_item_fechas CHECK (fecha_fin IS NULL OR fecha_inicio IS NULL OR fecha_fin >= fecha_inicio)
);
CREATE INDEX IF NOT EXISTS ix_plan_item_plan ON plan_tratamiento_item (plan_id, orden);

CREATE TABLE IF NOT EXISTS control_seguimiento (
    id              BIGSERIAL PRIMARY KEY,
    consulta_id     BIGINT NOT NULL REFERENCES consulta (id),
    plan_id         BIGINT REFERENCES plan_tratamiento (id) ON DELETE SET NULL,
    paciente_id     BIGINT NOT NULL REFERENCES paciente (id),
    fecha_sugerida  DATE NOT NULL,
    motivo          VARCHAR(300) NOT NULL,
    estado          VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE',
    cita_id         BIGINT REFERENCES cita (id),
    recordatorio_id BIGINT,               -- recordatorio generado (modulo comunicaciones), sin FK entre modulos
    creado_por      BIGINT REFERENCES usuario (id),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_control_estado CHECK (estado IN ('PENDIENTE','AGENDADO','REALIZADO','CANCELADO'))
);
CREATE INDEX IF NOT EXISTS ix_control_consulta ON control_seguimiento (consulta_id);
CREATE INDEX IF NOT EXISTS ix_control_paciente_fecha ON control_seguimiento (paciente_id, fecha_sugerida);

CREATE OR REPLACE TRIGGER trg_plan_inmutable      BEFORE INSERT OR UPDATE OR DELETE ON plan_tratamiento      FOR EACH ROW EXECUTE FUNCTION fn_hijo_consulta_inmutable();
CREATE OR REPLACE TRIGGER trg_plan_item_inmutable BEFORE INSERT OR UPDATE OR DELETE ON plan_tratamiento_item FOR EACH ROW EXECUTE FUNCTION fn_hijo_consulta_inmutable();
CREATE OR REPLACE TRIGGER trg_aud_plan_tratamiento      AFTER INSERT OR UPDATE OR DELETE ON plan_tratamiento      FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_plan_tratamiento_item AFTER INSERT OR UPDATE OR DELETE ON plan_tratamiento_item FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_control_seguimiento   AFTER INSERT OR UPDATE OR DELETE ON control_seguimiento   FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();

-- ---------------------------------------------------------------- 3) compartir con el tutor
CREATE TABLE IF NOT EXISTS compartir_envio (
    id                   BIGSERIAL PRIMARY KEY,
    paciente_id          BIGINT NOT NULL REFERENCES paciente (id),
    tutor_id             BIGINT NOT NULL REFERENCES tutor (id),
    canal                VARCHAR(10) NOT NULL,
    destinatario_mascara VARCHAR(120) NOT NULL,          -- nunca el contacto completo
    asunto               VARCHAR(200),
    mensaje              TEXT,                           -- texto enviado, con el enlace enmascarado
    estado               VARCHAR(16) NOT NULL,
    intentos             INT NOT NULL DEFAULT 0,
    ultimo_error         VARCHAR(500),
    proveedor            VARCHAR(40),
    proveedor_mensaje_id VARCHAR(120),
    responsable_id       BIGINT REFERENCES usuario (id), -- veterinario responsable (cuando envia un AUXILIAR)
    creado_por           BIGINT REFERENCES usuario (id),
    creado_en            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enviado_en           TIMESTAMPTZ,
    revocado_en          TIMESTAMPTZ,
    revocado_por         BIGINT REFERENCES usuario (id),
    motivo_revocacion    VARCHAR(255),
    CONSTRAINT ck_cenvio_canal  CHECK (canal IN ('EMAIL','WHATSAPP')),
    CONSTRAINT ck_cenvio_estado CHECK (estado IN ('PENDIENTE','ENVIADO','ENVIADO_SIMULADO','FALLIDO'))
);
CREATE INDEX IF NOT EXISTS ix_cenvio_paciente ON compartir_envio (paciente_id, creado_en DESC);

CREATE TABLE IF NOT EXISTS compartir_envio_documento (
    id             BIGSERIAL PRIMARY KEY,
    envio_id       BIGINT NOT NULL REFERENCES compartir_envio (id),
    tipo           VARCHAR(20) NOT NULL,
    documento_id   BIGINT NOT NULL,
    item_id        BIGINT,
    titulo         VARCHAR(200) NOT NULL,
    nombre_archivo VARCHAR(200) NOT NULL,
    tamano_bytes   INT NOT NULL,
    hash_sha256    VARCHAR(64) NOT NULL,
    contenido      BYTEA NOT NULL,                       -- instantanea exacta de lo compartido
    CONSTRAINT ck_cenvdoc_tipo CHECK (tipo IN ('FORMULA','LABORATORIO','IMAGEN','PATOLOGIA','PROCEDIMIENTO','CARNET','RESUMEN_CONSULTA'))
);
CREATE INDEX IF NOT EXISTS ix_cenvdoc_envio ON compartir_envio_documento (envio_id);

CREATE TABLE IF NOT EXISTS compartir_enlace (
    id                 BIGSERIAL PRIMARY KEY,
    envio_documento_id BIGINT NOT NULL REFERENCES compartir_envio_documento (id),
    envio_id           BIGINT NOT NULL REFERENCES compartir_envio (id),
    token_hash         VARCHAR(64) NOT NULL,             -- SHA-256 hex del token; el token solo existe en el enlace entregado
    token_prefijo      VARCHAR(8) NOT NULL,              -- identifica el enlace en la bitacora sin exponerlo
    expira_en          TIMESTAMPTZ NOT NULL,
    max_descargas      INT NOT NULL,
    descargas          INT NOT NULL DEFAULT 0,
    ultimo_acceso      TIMESTAMPTZ,
    revocado_en        TIMESTAMPTZ,
    revocado_por       BIGINT REFERENCES usuario (id),
    creado_en          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_cenlace_hash UNIQUE (token_hash),
    CONSTRAINT ck_cenlace_max CHECK (max_descargas > 0)
);
CREATE INDEX IF NOT EXISTS ix_cenlace_envio ON compartir_enlace (envio_id);

CREATE TABLE IF NOT EXISTS compartir_acceso (
    id          BIGSERIAL PRIMARY KEY,
    enlace_id   BIGINT REFERENCES compartir_enlace (id),  -- NULL: token desconocido
    resultado   VARCHAR(15) NOT NULL,
    ip          VARCHAR(45),
    user_agent  VARCHAR(255),
    ocurrido_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cacceso_resultado CHECK (resultado IN ('OK','EXPIRADO','REVOCADO','LIMITE','NO_ENCONTRADO'))
);
CREATE INDEX IF NOT EXISTS ix_cacceso_enlace ON compartir_acceso (enlace_id, ocurrido_en);

-- La bitacora de accesos y los documentos enviados no se editan ni se borran.
CREATE OR REPLACE TRIGGER trg_cacceso_append_only BEFORE UPDATE OR DELETE ON compartir_acceso          FOR EACH ROW EXECUTE FUNCTION fn_append_only();
CREATE OR REPLACE TRIGGER trg_cenvdoc_append_only BEFORE UPDATE OR DELETE ON compartir_envio_documento FOR EACH ROW EXECUTE FUNCTION fn_append_only();
CREATE OR REPLACE TRIGGER trg_cenvio_no_delete    BEFORE DELETE ON compartir_envio  FOR EACH ROW EXECUTE FUNCTION fn_no_delete();
CREATE OR REPLACE TRIGGER trg_cenlace_no_delete   BEFORE DELETE ON compartir_enlace FOR EACH ROW EXECUTE FUNCTION fn_no_delete();
-- Auditoria (quien comparte y revoca); el contenido binario NO se audita (compartir_envio_documento queda fuera).
CREATE OR REPLACE TRIGGER trg_aud_compartir_envio  AFTER INSERT OR UPDATE OR DELETE ON compartir_envio  FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_compartir_enlace AFTER INSERT OR UPDATE OR DELETE ON compartir_enlace FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();

-- ---------------------------------------------------------------- 4) plantillas de mensaje (sin datos clinicos)
-- Solo saludo, nombre de la mascota, tipo de documento y adjunto/enlace. Configurables desde /api/comunicaciones/plantillas.
INSERT INTO plantilla_mensaje (codigo, canal, idioma, asunto, cuerpo, variables)
SELECT v.codigo, v.canal, 'es', v.asunto, v.cuerpo, v.vars::jsonb
FROM (VALUES
 ('DOC_FORMULA','EMAIL','Formula medica de {{paciente}} - {{clinica}}',
  'Hola {{tutor}}, adjuntamos la formula medica de {{paciente}}. {{clinica}}','["tutor","paciente","clinica","documento"]'),
 ('DOC_FORMULA','WHATSAPP',NULL,
  'Hola {{tutor}}, te compartimos la formula medica de {{paciente}}: {{enlace}} - {{clinica}}','["tutor","paciente","clinica","documento","enlace"]'),
 ('DOC_LABORATORIO','EMAIL','Resultados de laboratorio de {{paciente}} - {{clinica}}',
  'Hola {{tutor}}, adjuntamos el informe de laboratorio de {{paciente}}. {{clinica}}','["tutor","paciente","clinica","documento"]'),
 ('DOC_LABORATORIO','WHATSAPP',NULL,
  'Hola {{tutor}}, ya esta disponible el informe de laboratorio de {{paciente}}: {{enlace}} - {{clinica}}','["tutor","paciente","clinica","documento","enlace"]'),
 ('DOC_IMAGEN','EMAIL','Informe de imagenologia de {{paciente}} - {{clinica}}',
  'Hola {{tutor}}, adjuntamos el informe de imagenologia de {{paciente}}. {{clinica}}','["tutor","paciente","clinica","documento"]'),
 ('DOC_IMAGEN','WHATSAPP',NULL,
  'Hola {{tutor}}, te compartimos el informe de imagenologia de {{paciente}}: {{enlace}} - {{clinica}}','["tutor","paciente","clinica","documento","enlace"]'),
 ('DOC_PATOLOGIA','EMAIL','Informe de patologia de {{paciente}} - {{clinica}}',
  'Hola {{tutor}}, adjuntamos el informe de patologia de {{paciente}}. {{clinica}}','["tutor","paciente","clinica","documento"]'),
 ('DOC_PATOLOGIA','WHATSAPP',NULL,
  'Hola {{tutor}}, te compartimos el informe de patologia de {{paciente}}: {{enlace}} - {{clinica}}','["tutor","paciente","clinica","documento","enlace"]'),
 ('DOC_PROCEDIMIENTO','EMAIL','Reporte de procedimiento de {{paciente}} - {{clinica}}',
  'Hola {{tutor}}, adjuntamos el reporte del procedimiento de {{paciente}}. {{clinica}}','["tutor","paciente","clinica","documento"]'),
 ('DOC_PROCEDIMIENTO','WHATSAPP',NULL,
  'Hola {{tutor}}, te compartimos el reporte del procedimiento de {{paciente}}: {{enlace}} - {{clinica}}','["tutor","paciente","clinica","documento","enlace"]'),
 ('DOC_CARNET','EMAIL','Carnet de vacunacion de {{paciente}} - {{clinica}}',
  'Hola {{tutor}}, adjuntamos el carnet de vacunacion de {{paciente}}. {{clinica}}','["tutor","paciente","clinica","documento"]'),
 ('DOC_CARNET','WHATSAPP',NULL,
  'Hola {{tutor}}, te compartimos el carnet de vacunacion de {{paciente}}: {{enlace}} - {{clinica}}','["tutor","paciente","clinica","documento","enlace"]'),
 ('DOC_RESUMEN_CONSULTA','EMAIL','Resumen de la consulta de {{paciente}} - {{clinica}}',
  'Hola {{tutor}}, adjuntamos el resumen de la consulta de {{paciente}}. {{clinica}}','["tutor","paciente","clinica","documento"]'),
 ('DOC_RESUMEN_CONSULTA','WHATSAPP',NULL,
  'Hola {{tutor}}, te compartimos el resumen de la consulta de {{paciente}}: {{enlace}} - {{clinica}}','["tutor","paciente","clinica","documento","enlace"]'),
 ('DOC_VARIOS','EMAIL','Documentos de {{paciente}} - {{clinica}}',
  'Hola {{tutor}}, adjuntamos los documentos de {{paciente}}: {{documento}}. {{clinica}}','["tutor","paciente","clinica","documento"]'),
 ('DOC_VARIOS','WHATSAPP',NULL,
  'Hola {{tutor}}, te compartimos los documentos de {{paciente}} ({{documento}}): {{enlace}} - {{clinica}}','["tutor","paciente","clinica","documento","enlace"]'),
 ('CONTROL_SEGUIMIENTO','EMAIL','Control sugerido para {{paciente}} - {{clinica}}',
  'Hola {{tutor}}, {{paciente}} tiene un control sugerido para el {{fecha}}. Agenda tu cita con nosotros. {{clinica}}','["tutor","paciente","fecha","clinica"]')
) AS v(codigo, canal, asunto, cuerpo, vars)
ON CONFLICT (codigo, canal, idioma) DO NOTHING;
