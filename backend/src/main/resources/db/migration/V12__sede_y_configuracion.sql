-- ============================================================================
-- V12 (PROPUESTA) - Multi-sede, configuracion, soft-delete y auditoria a nivel BD
-- Idempotente respecto a V1-V11. Todas las tablas nuevas usan TIMESTAMPTZ.
-- ============================================================================
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- ---------------------------------------------------------------- SEDE
CREATE TABLE IF NOT EXISTS sede (
    id                    BIGSERIAL PRIMARY KEY,
    codigo                VARCHAR(20)  NOT NULL,
    nombre                VARCHAR(150) NOT NULL,
    nit                   VARCHAR(20),
    direccion             VARCHAR(255),
    ciudad                VARCHAR(120),
    departamento          VARCHAR(120),
    codigo_dane_municipio VARCHAR(10),
    telefono              VARCHAR(30),
    email                 VARCHAR(255),
    zona_horaria          VARCHAR(60)  NOT NULL DEFAULT 'America/Bogota',
    es_principal          BOOLEAN      NOT NULL DEFAULT FALSE,
    activo                BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en             TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en        TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_sede_codigo UNIQUE (codigo)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_sede_una_principal ON sede (es_principal) WHERE es_principal;
COMMENT ON TABLE sede IS 'Sede/sucursal de la clinica. Entidades transaccionales llevan sede_id (nullable = sede principal).';

INSERT INTO sede (codigo, nombre, ciudad, es_principal)
VALUES ('PRINCIPAL', 'Sede principal', 'Bogota', TRUE)
ON CONFLICT (codigo) DO NOTHING;

CREATE TABLE IF NOT EXISTS usuario_sede (
    usuario_id        BIGINT  NOT NULL REFERENCES usuario (id),
    sede_id           BIGINT  NOT NULL REFERENCES sede (id),
    es_predeterminada BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (usuario_id, sede_id)
);
CREATE INDEX IF NOT EXISTS ix_usuario_sede_sede ON usuario_sede (sede_id);
COMMENT ON TABLE usuario_sede IS 'Sedes a las que un usuario puede acceder (filtro por sede en la capa de servicio o RLS).';

-- Todo usuario existente queda asignado a la sede principal
INSERT INTO usuario_sede (usuario_id, sede_id, es_predeterminada)
SELECT u.id, s.id, TRUE FROM usuario u CROSS JOIN sede s WHERE s.es_principal
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- CONFIGURACION
CREATE TABLE IF NOT EXISTS configuracion_clinica (
    id             BIGSERIAL PRIMARY KEY,
    sede_id        BIGINT REFERENCES sede (id),   -- NULL = valor global
    clave          VARCHAR(100) NOT NULL,
    valor          TEXT,
    tipo           VARCHAR(15)  NOT NULL DEFAULT 'TEXTO',
    descripcion    VARCHAR(255),
    actualizado_en TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_config_tipo CHECK (tipo IN ('TEXTO', 'NUMERO', 'BOOLEANO', 'JSON')),
    CONSTRAINT uq_config_sede_clave UNIQUE NULLS NOT DISTINCT (sede_id, clave)
);
COMMENT ON TABLE configuracion_clinica IS 'Parametros clave/valor globales o por sede.';

INSERT INTO configuracion_clinica (sede_id, clave, valor, tipo, descripcion) VALUES
 (NULL, 'moneda',                         'COP',  'TEXTO',    'Moneda de facturacion'),
 (NULL, 'zona_horaria',                   'America/Bogota', 'TEXTO', 'Zona horaria por defecto'),
 (NULL, 'agenda.slot_minutos',            '15',   'NUMERO',   'Granularidad de la agenda'),
 (NULL, 'agenda.horas_confirmacion_previa','24',  'NUMERO',   'Horas antes de la cita para solicitar confirmacion'),
 (NULL, 'clinica.horas_cierre_automatico','24',   'NUMERO',   'Horas tras las que una consulta abierta se cierra sola (AAHA recomienda <=24h)'),
 (NULL, 'clinica.retencion_historia_anios','10',  'NUMERO',   'Anios de retencion de historia clinica (VALIDAR con asesor juridico)'),
 (NULL, 'inventario.dias_alerta_vencimiento','60','NUMERO',   'Dias antes del vencimiento para alertar lotes'),
 (NULL, 'facturacion.iva_servicios_pct',  '0',    'NUMERO',   'IVA por defecto servicios (VALIDAR con contador)'),
 (NULL, 'comunicaciones.hora_envio_inicio','08:00','TEXTO',   'Ventana de envio de recordatorios (inicio)'),
 (NULL, 'comunicaciones.hora_envio_fin',  '19:00','TEXTO',    'Ventana de envio de recordatorios (fin)')
ON CONFLICT DO NOTHING;

-- ---------------------------------------------------------------- sede_id en transaccionales
ALTER TABLE cita           ADD COLUMN IF NOT EXISTS sede_id BIGINT REFERENCES sede (id);
ALTER TABLE consulta       ADD COLUMN IF NOT EXISTS sede_id BIGINT REFERENCES sede (id);
ALTER TABLE factura        ADD COLUMN IF NOT EXISTS sede_id BIGINT REFERENCES sede (id);
ALTER TABLE presupuesto    ADD COLUMN IF NOT EXISTS sede_id BIGINT REFERENCES sede (id);
ALTER TABLE bodega         ADD COLUMN IF NOT EXISTS sede_id BIGINT REFERENCES sede (id);
ALTER TABLE bloqueo_agenda ADD COLUMN IF NOT EXISTS sede_id BIGINT REFERENCES sede (id);
ALTER TABLE adjunto        ADD COLUMN IF NOT EXISTS sede_id BIGINT REFERENCES sede (id);
ALTER TABLE inmunizacion   ADD COLUMN IF NOT EXISTS sede_id BIGINT REFERENCES sede (id);
ALTER TABLE paciente       ADD COLUMN IF NOT EXISTS sede_origen_id BIGINT REFERENCES sede (id);

UPDATE cita           SET sede_id = (SELECT id FROM sede WHERE es_principal) WHERE sede_id IS NULL;
UPDATE consulta       SET sede_id = (SELECT id FROM sede WHERE es_principal) WHERE sede_id IS NULL;
UPDATE factura        SET sede_id = (SELECT id FROM sede WHERE es_principal) WHERE sede_id IS NULL;
UPDATE presupuesto    SET sede_id = (SELECT id FROM sede WHERE es_principal) WHERE sede_id IS NULL;
UPDATE bodega         SET sede_id = (SELECT id FROM sede WHERE es_principal) WHERE sede_id IS NULL;
UPDATE bloqueo_agenda SET sede_id = (SELECT id FROM sede WHERE es_principal) WHERE sede_id IS NULL;
UPDATE paciente       SET sede_origen_id = (SELECT id FROM sede WHERE es_principal) WHERE sede_origen_id IS NULL;

CREATE INDEX IF NOT EXISTS ix_cita_sede_fecha     ON cita (sede_id, fecha_hora_inicio);
CREATE INDEX IF NOT EXISTS ix_consulta_sede_fecha ON consulta (sede_id, fecha_hora);
CREATE INDEX IF NOT EXISTS ix_factura_sede_fecha  ON factura (sede_id, fecha);
CREATE INDEX IF NOT EXISTS ix_bodega_sede         ON bodega (sede_id);

-- ---------------------------------------------------------------- soft-delete
-- Convencion: 'activo' sigue siendo el flag; eliminado_en/por documentan quien y cuando.
-- Nunca se hace DELETE fisico de tutor/paciente/consulta/factura.
ALTER TABLE tutor    ADD COLUMN IF NOT EXISTS eliminado_en  TIMESTAMPTZ;
ALTER TABLE tutor    ADD COLUMN IF NOT EXISTS eliminado_por BIGINT REFERENCES usuario (id);
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS eliminado_en  TIMESTAMPTZ;
ALTER TABLE paciente ADD COLUMN IF NOT EXISTS eliminado_por BIGINT REFERENCES usuario (id);

-- ---------------------------------------------------------------- auditoria
ALTER TABLE auditoria ADD COLUMN IF NOT EXISTS sede_id        BIGINT REFERENCES sede (id);
ALTER TABLE auditoria ADD COLUMN IF NOT EXISTS tipo_evento    VARCHAR(10) NOT NULL DEFAULT 'ESCRITURA';
ALTER TABLE auditoria ADD COLUMN IF NOT EXISTS correlacion_id UUID;
ALTER TABLE auditoria ADD COLUMN IF NOT EXISTS user_agent     VARCHAR(255);
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_auditoria_tipo_evento') THEN
    ALTER TABLE auditoria ADD CONSTRAINT ck_auditoria_tipo_evento CHECK (tipo_evento IN ('ESCRITURA','LECTURA','LOGIN','EXPORTACION'));
  END IF;
END $$;
CREATE INDEX IF NOT EXISTS ix_auditoria_usuario_fecha ON auditoria (usuario_id, ocurrido_en);

-- Red de seguridad: trigger generico que registra INSERT/UPDATE/DELETE con diff JSONB.
-- La app fija 'SET LOCAL app.usuario_id = <id>' y 'app.ip'. Si el interceptor Java ya audita
-- la operacion, fijar 'SET LOCAL app.auditoria_app = on' para evitar duplicados.
CREATE OR REPLACE FUNCTION fn_auditar_cambio() RETURNS trigger LANGUAGE plpgsql AS $fn$
DECLARE
  v_usuario BIGINT := NULLIF(current_setting('app.usuario_id', true), '')::BIGINT;
  v_ip      TEXT   := NULLIF(current_setting('app.ip', true), '');
  v_old     JSONB;
  v_new     JSONB;
  v_detalle JSONB;
  v_id      BIGINT;
BEGIN
  IF COALESCE(current_setting('app.auditoria_app', true), 'off') = 'on' THEN
    RETURN COALESCE(NEW, OLD);
  END IF;
  IF TG_OP = 'INSERT' THEN
    v_new := to_jsonb(NEW); v_detalle := jsonb_build_object('despues', v_new); v_id := (v_new->>'id')::BIGINT;
  ELSIF TG_OP = 'DELETE' THEN
    v_old := to_jsonb(OLD); v_detalle := jsonb_build_object('antes', v_old); v_id := (v_old->>'id')::BIGINT;
  ELSE
    v_old := to_jsonb(OLD); v_new := to_jsonb(NEW); v_id := (v_new->>'id')::BIGINT;
    SELECT jsonb_build_object(
             'antes',   COALESCE(jsonb_object_agg(o.key, o.value), '{}'::jsonb),
             'despues', COALESCE(jsonb_object_agg(n.key, n.value), '{}'::jsonb))
      INTO v_detalle
      FROM jsonb_each(v_new) n
      JOIN jsonb_each(v_old) o USING (key)
     WHERE n.value IS DISTINCT FROM o.value
       AND n.key NOT IN ('actualizado_en', 'actualizado_el');
    IF v_detalle->'despues' = '{}'::jsonb THEN RETURN NEW; END IF;
  END IF;
  INSERT INTO auditoria (usuario_id, accion, entidad, entidad_id, detalle, ip)
  VALUES (v_usuario, TG_OP, TG_TABLE_NAME, v_id, v_detalle, v_ip);
  RETURN COALESCE(NEW, OLD);
END
$fn$;

CREATE OR REPLACE TRIGGER trg_aud_paciente  AFTER INSERT OR UPDATE OR DELETE ON paciente  FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_tutor     AFTER INSERT OR UPDATE OR DELETE ON tutor     FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_consulta  AFTER INSERT OR UPDATE OR DELETE ON consulta  FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_factura   AFTER INSERT OR UPDATE OR DELETE ON factura   FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_pago      AFTER INSERT OR UPDATE OR DELETE ON pago      FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_receta    AFTER INSERT OR UPDATE OR DELETE ON receta    FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
