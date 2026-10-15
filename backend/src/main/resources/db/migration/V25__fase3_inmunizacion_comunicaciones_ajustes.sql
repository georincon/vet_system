-- ============================================================================
-- V25 - Fase 3 (vacunacion + recordatorios): ajustes minimos sobre V16/V17.
--  1) inmunizacion: trazabilidad al renglon de consulta que la origino (idempotencia del evento de cierre,
--     sin tocar consulta_vacuna, que es inmutable tras el cierre) y datos de anulacion (no se borra).
--  2) antiparasitario: catalogo propio (V16 solo tenia items sueltos) con intervalo de re-aplicacion para
--     calcular proxima_fecha; desparasitacion gana catalogo, lote en texto y anulacion.
--  3) CHECK de estados: se admite ENVIADO_SIMULADO (adaptador de registro cuando no hay SMTP).
--  4) Auditoria por trigger (V12) tambien para las tablas nuevas de Fase 3.
-- ============================================================================

-- ---------------------------------------------------------------- 1) inmunizacion
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS consulta_vacuna_id BIGINT REFERENCES consulta_vacuna (id);
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS motivo_anulacion   VARCHAR(255);
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS anulado_por        BIGINT REFERENCES usuario (id);
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS anulado_en         TIMESTAMPTZ;
ALTER TABLE inmunizacion ADD COLUMN IF NOT EXISTS creado_en          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
CREATE UNIQUE INDEX IF NOT EXISTS uq_inm_consulta_vacuna ON inmunizacion (consulta_vacuna_id) WHERE consulta_vacuna_id IS NOT NULL;

-- ---------------------------------------------------------------- 2) antiparasitarios
CREATE TABLE IF NOT EXISTS antiparasitario (
    id               BIGSERIAL PRIMARY KEY,
    codigo           VARCHAR(50)  NOT NULL,
    nombre           VARCHAR(255) NOT NULL,
    tipo             VARCHAR(12)  NOT NULL,
    principio_activo VARCHAR(150),
    especie_id       BIGINT REFERENCES especie (id),
    intervalo_dias   INT NOT NULL,
    item_id          BIGINT REFERENCES item (id),
    activo           BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_antiparasitario_codigo UNIQUE (codigo),
    CONSTRAINT ck_antiparasitario_tipo CHECK (tipo IN ('INTERNA','EXTERNA','DIROFILARIA','COMBINADA')),
    CONSTRAINT ck_antiparasitario_intervalo CHECK (intervalo_dias > 0)
);

INSERT INTO antiparasitario (codigo, nombre, tipo, principio_activo, intervalo_dias, item_id)
SELECT v.codigo, v.nombre, v.tipo, v.pa, v.dias, (SELECT id FROM item WHERE codigo = v.item)
FROM (VALUES
 ('ANT-PZQ',  'Praziquantel + pirantel + febantel (interno)', 'INTERNA',     'praziquantel/pirantel/febantel', 90, 'MED-ANT-PZQ'),
 ('ANT-ISOX', 'Isoxazolina oral (pulgas y garrapatas)',       'EXTERNA',     'isoxazolina',                    30, 'MED-ANT-ISOX'),
 ('ANT-SELA', 'Selamectina pipeta',                           'COMBINADA',   'selamectina',                    30, 'MED-ANT-SELA'),
 ('ANT-IVER', 'Ivermectina (prevencion de dirofilaria)',      'DIROFILARIA', 'ivermectina',                    30, 'MED-ANT-IVER')
) AS v(codigo, nombre, tipo, pa, dias, item)
ON CONFLICT (codigo) DO NOTHING;

ALTER TABLE desparasitacion ADD COLUMN IF NOT EXISTS antiparasitario_id BIGINT REFERENCES antiparasitario (id);
ALTER TABLE desparasitacion ADD COLUMN IF NOT EXISTS lote               VARCHAR(60);
ALTER TABLE desparasitacion ADD COLUMN IF NOT EXISTS fecha_vencimiento_lote DATE;
ALTER TABLE desparasitacion ADD COLUMN IF NOT EXISTS estado             VARCHAR(12) NOT NULL DEFAULT 'APLICADA';
ALTER TABLE desparasitacion ADD COLUMN IF NOT EXISTS motivo_anulacion   VARCHAR(255);
ALTER TABLE desparasitacion ADD COLUMN IF NOT EXISTS anulado_por        BIGINT REFERENCES usuario (id);
ALTER TABLE desparasitacion ADD COLUMN IF NOT EXISTS anulado_en         TIMESTAMPTZ;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_despar_estado') THEN
    ALTER TABLE desparasitacion ADD CONSTRAINT ck_despar_estado CHECK (estado IN ('APLICADA','ANULADA'));
  END IF;
END $$;

-- ---------------------------------------------------------------- 3) ENVIADO_SIMULADO
-- 'ENVIADO_SIMULADO' mide 16 caracteres: se amplian las columnas VARCHAR(12) de estado.
ALTER TABLE recordatorio ALTER COLUMN estado TYPE VARCHAR(20);
ALTER TABLE envio_log    ALTER COLUMN estado TYPE VARCHAR(20);
ALTER TABLE recordatorio DROP CONSTRAINT IF EXISTS ck_rec_estado;
ALTER TABLE recordatorio ADD CONSTRAINT ck_rec_estado
    CHECK (estado IN ('PENDIENTE','ENVIANDO','ENVIADO','ENVIADO_SIMULADO','FALLIDO','CANCELADO','OMITIDO_OPTOUT'));
ALTER TABLE envio_log DROP CONSTRAINT IF EXISTS ck_envio_estado;
ALTER TABLE envio_log ADD CONSTRAINT ck_envio_estado
    CHECK (estado IN ('ENVIADO','ENVIADO_SIMULADO','ENTREGADO','LEIDO','FALLIDO','REBOTADO'));

-- ---------------------------------------------------------------- 4) auditoria por trigger
CREATE OR REPLACE TRIGGER trg_aud_inmunizacion   AFTER INSERT OR UPDATE OR DELETE ON inmunizacion   FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_desparasitacion AFTER INSERT OR UPDATE OR DELETE ON desparasitacion FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_vacuna         AFTER INSERT OR UPDATE OR DELETE ON vacuna         FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_antiparasitario AFTER INSERT OR UPDATE OR DELETE ON antiparasitario FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_esquema_vacunal AFTER INSERT OR UPDATE OR DELETE ON esquema_vacunal FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_plantilla_mensaje AFTER INSERT OR UPDATE OR DELETE ON plantilla_mensaje FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_regla_recordatorio AFTER INSERT OR UPDATE OR DELETE ON regla_recordatorio FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_comunicacion_optout AFTER INSERT OR UPDATE OR DELETE ON comunicacion_optout FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
