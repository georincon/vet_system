-- ============================================================================
-- V17 (PROPUESTA) - Comunicaciones: plantillas con variables, reglas de recordatorio,
-- cola de recordatorios sin duplicados, bitacora de envios y opt-out.
-- ============================================================================
CREATE TABLE IF NOT EXISTS plantilla_mensaje (
    id          BIGSERIAL PRIMARY KEY,
    codigo      VARCHAR(50)  NOT NULL,
    canal       VARCHAR(10)  NOT NULL,
    idioma      VARCHAR(5)   NOT NULL DEFAULT 'es',
    asunto      VARCHAR(200),
    cuerpo      TEXT         NOT NULL,
    variables   JSONB        NOT NULL DEFAULT '[]'::jsonb,
    plantilla_externa_id VARCHAR(100),   -- id de plantilla aprobada en WhatsApp Business
    activo      BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en   TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_plantilla_msg UNIQUE (codigo, canal, idioma),
    CONSTRAINT ck_plantilla_canal CHECK (canal IN ('EMAIL','WHATSAPP','SMS','PUSH'))
);
COMMENT ON COLUMN plantilla_mensaje.cuerpo IS 'Texto con variables {{tutor}}, {{paciente}}, {{fecha}}, {{hora}}, {{vacuna}}, {{sede}}.';

CREATE TABLE IF NOT EXISTS regla_recordatorio (
    id             BIGSERIAL PRIMARY KEY,
    codigo         VARCHAR(50) NOT NULL,
    evento         VARCHAR(25) NOT NULL,
    dias_offset    INT NOT NULL,           -- negativo = antes del evento, positivo = despues
    canal          VARCHAR(10) NOT NULL,
    plantilla_id   BIGINT REFERENCES plantilla_mensaje (id),
    sede_id        BIGINT REFERENCES sede (id),
    activo         BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_regla_codigo UNIQUE (codigo),
    CONSTRAINT ck_regla_evento CHECK (evento IN ('CITA','VACUNA_PROXIMA','DESPARASITACION_PROXIMA','CONTROL','CUMPLEANOS','FACTURA_VENCIDA','POST_CONSULTA','POST_CIRUGIA','PLAN_SALUD_RENOVACION','INACTIVIDAD')),
    CONSTRAINT ck_regla_canal  CHECK (canal IN ('EMAIL','WHATSAPP','SMS','PUSH'))
);

CREATE TABLE IF NOT EXISTS recordatorio (
    id               BIGSERIAL PRIMARY KEY,
    regla_id         BIGINT REFERENCES regla_recordatorio (id),
    tutor_id         BIGINT NOT NULL REFERENCES tutor (id),
    paciente_id      BIGINT REFERENCES paciente (id),
    tipo             VARCHAR(25) NOT NULL,
    referencia_tabla VARCHAR(40) NOT NULL,
    referencia_id    BIGINT      NOT NULL,
    canal            VARCHAR(10) NOT NULL,
    plantilla_id     BIGINT REFERENCES plantilla_mensaje (id),
    programado_para  TIMESTAMPTZ NOT NULL,
    estado           VARCHAR(12) NOT NULL DEFAULT 'PENDIENTE',
    intentos         INT NOT NULL DEFAULT 0,
    ultimo_error     VARCHAR(500),
    enviado_en       TIMESTAMPTZ,
    sede_id          BIGINT REFERENCES sede (id),
    creado_en        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_rec_estado CHECK (estado IN ('PENDIENTE','ENVIANDO','ENVIADO','FALLIDO','CANCELADO','OMITIDO_OPTOUT')),
    CONSTRAINT ck_rec_canal  CHECK (canal IN ('EMAIL','WHATSAPP','SMS','PUSH')),
    -- idempotencia del job: un recordatorio por (evento de origen, regla, canal)
    CONSTRAINT uq_rec_origen UNIQUE (referencia_tabla, referencia_id, regla_id, canal)
);
CREATE INDEX IF NOT EXISTS ix_rec_cola ON recordatorio (programado_para) WHERE estado = 'PENDIENTE';
CREATE INDEX IF NOT EXISTS ix_rec_tutor ON recordatorio (tutor_id);

CREATE TABLE IF NOT EXISTS envio_log (
    id               BIGSERIAL PRIMARY KEY,
    recordatorio_id  BIGINT REFERENCES recordatorio (id),
    tutor_id         BIGINT REFERENCES tutor (id),
    canal            VARCHAR(10) NOT NULL,
    destinatario     VARCHAR(255) NOT NULL,
    asunto           VARCHAR(200),
    cuerpo           TEXT,
    estado           VARCHAR(12) NOT NULL,
    proveedor        VARCHAR(40),
    proveedor_mensaje_id VARCHAR(120),
    error            VARCHAR(500),
    enviado_en       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    entregado_en     TIMESTAMPTZ,
    leido_en         TIMESTAMPTZ,
    CONSTRAINT ck_envio_estado CHECK (estado IN ('ENVIADO','ENTREGADO','LEIDO','FALLIDO','REBOTADO'))
);
CREATE INDEX IF NOT EXISTS ix_envio_tutor ON envio_log (tutor_id, enviado_en);
CREATE INDEX IF NOT EXISTS ix_envio_recordatorio ON envio_log (recordatorio_id);

CREATE TABLE IF NOT EXISTS comunicacion_optout (
    id        BIGSERIAL PRIMARY KEY,
    tutor_id  BIGINT NOT NULL REFERENCES tutor (id),
    canal     VARCHAR(10) NOT NULL,
    categoria VARCHAR(15) NOT NULL DEFAULT 'TODAS',
    desde     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    motivo    VARCHAR(255),
    CONSTRAINT uq_optout UNIQUE (tutor_id, canal, categoria),
    CONSTRAINT ck_optout_canal CHECK (canal IN ('EMAIL','WHATSAPP','SMS','PUSH','TODOS')),
    CONSTRAINT ck_optout_cat   CHECK (categoria IN ('TODAS','SERVICIO','MARKETING'))
);

-- ---------------------------------------------------------------- SEMILLAS
INSERT INTO plantilla_mensaje (codigo, canal, asunto, cuerpo, variables) VALUES
 ('CITA_RECORDATORIO','EMAIL','Recordatorio de cita para {{paciente}}',
  'Hola {{tutor}}, te recordamos la cita de {{paciente}} el {{fecha}} a las {{hora}} en {{sede}}. Responde para confirmar o reprogramar.', '["tutor","paciente","fecha","hora","sede"]'),
 ('CITA_RECORDATORIO','WHATSAPP',NULL,
  'Hola {{tutor}}, te recordamos la cita de {{paciente}} el {{fecha}} a las {{hora}}. Responde 1 para confirmar o 2 para reprogramar.', '["tutor","paciente","fecha","hora"]'),
 ('VACUNA_PROXIMA','EMAIL','{{paciente}} tiene una vacuna proxima',
  'Hola {{tutor}}, a {{paciente}} le corresponde la vacuna {{vacuna}} el {{fecha}}. Agenda tu cita con nosotros.', '["tutor","paciente","vacuna","fecha"]'),
 ('VACUNA_PROXIMA','WHATSAPP',NULL,
  'Hola {{tutor}}, a {{paciente}} le toca la vacuna {{vacuna}} el {{fecha}}. Escribenos para agendar.', '["tutor","paciente","vacuna","fecha"]'),
 ('DESPARASITACION_PROXIMA','WHATSAPP',NULL,
  'Hola {{tutor}}, es momento de desparasitar a {{paciente}} ({{fecha}}). Escribenos para agendar.', '["tutor","paciente","fecha"]'),
 ('CUMPLEANOS','EMAIL','Feliz cumpleanos, {{paciente}}',
  'Hola {{tutor}}, hoy {{paciente}} esta de cumpleanos. De parte de todo el equipo, felicitaciones.', '["tutor","paciente"]'),
 ('POST_CONSULTA','WHATSAPP',NULL,
  'Hola {{tutor}}, como sigue {{paciente}} tras la consulta? Si notas algo extrano, escribenos.', '["tutor","paciente"]'),
 ('FACTURA_VENCIDA','EMAIL','Saldo pendiente',
  'Hola {{tutor}}, tienes un saldo pendiente con nosotros. Contactanos para coordinar el pago.', '["tutor"]')
ON CONFLICT (codigo, canal, idioma) DO NOTHING;

INSERT INTO regla_recordatorio (codigo, evento, dias_offset, canal, plantilla_id)
SELECT v.codigo, v.evento, v.dias, v.canal,
       (SELECT id FROM plantilla_mensaje WHERE codigo = v.plantilla AND canal = v.canal AND idioma = 'es')
FROM (VALUES
 ('CITA_24H_EMAIL',   'CITA',                   -1, 'EMAIL',    'CITA_RECORDATORIO'),
 ('CITA_24H_WA',      'CITA',                   -1, 'WHATSAPP', 'CITA_RECORDATORIO'),
 ('VAC_7D_EMAIL',     'VACUNA_PROXIMA',         -7, 'EMAIL',    'VACUNA_PROXIMA'),
 ('VAC_7D_WA',        'VACUNA_PROXIMA',         -7, 'WHATSAPP', 'VACUNA_PROXIMA'),
 ('VAC_VENCIDA_7D_WA','VACUNA_PROXIMA',          7, 'WHATSAPP', 'VACUNA_PROXIMA'),
 ('DESP_7D_WA',       'DESPARASITACION_PROXIMA',-7, 'WHATSAPP', 'DESPARASITACION_PROXIMA'),
 ('CUMPLE_EMAIL',     'CUMPLEANOS',              0, 'EMAIL',    'CUMPLEANOS'),
 ('POST_CONS_2D_WA',  'POST_CONSULTA',           2, 'WHATSAPP', 'POST_CONSULTA'),
 ('FACT_VENC_EMAIL',  'FACTURA_VENCIDA',         3, 'EMAIL',    'FACTURA_VENCIDA')
) AS v(codigo, evento, dias, canal, plantilla)
ON CONFLICT (codigo) DO NOTHING;
