-- ============================================================================
-- V29 - Etapa A de documentacion clinica avanzada: estructura.
--   1) adjunto: almacenamiento seguro de archivos (nombre aleatorio, miniatura, baja logica con motivo, sin DELETE).
--   2) laboratorio: amplia columnas cortas de V20 (tipo_valor, orden_examen.estado), LOINC/UCUM por parametro, valores normales cualitativos, resultados con estados
--      BORRADOR/VALIDADO/CORREGIDO (correccion enlazada, append-only), validaciones versionadas,
--      bitacora de la orden (bandeja), importaciones HL7/CSV/JSON con el mensaje original.
--   3) informes estructurados de imagen/ecografia y de patologia/citologia (mismo patron de versionado).
--   4) cirugia: tambien registra procedimientos ambulatorios (columna tipo), insumos y consentimiento enlazado.
--   5) auditoria por trigger (fn_auditar_cambio, V12) en las tablas de la etapa.
-- Los datos de catalogo (examenes, parametros, rangos) van en V30.
-- ============================================================================

-- ---------------------------------------------------------------- funciones genericas
CREATE OR REPLACE FUNCTION fn_no_delete() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  RAISE EXCEPTION 'La tabla % no admite DELETE fisico (use la baja logica)', TG_TABLE_NAME USING ERRCODE = 'P0001';
END
$fn$;

-- Registros validados: solo pueden pasar de VALIDADO a CORREGIDO (estado + reemplazado_por); nada mas cambia.
-- El enlace reemplazado_por se completa una sola vez (NULL -> id) sobre un registro ya CORREGIDO.
CREATE OR REPLACE FUNCTION fn_validado_inmutable() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  IF TG_OP = 'DELETE' THEN
    IF OLD.estado <> 'BORRADOR' THEN
      RAISE EXCEPTION 'El registro % de % esta % y no se puede eliminar (use una correccion enlazada)', OLD.id, TG_TABLE_NAME, OLD.estado USING ERRCODE = 'P0001';
    END IF;
    RETURN OLD;
  END IF;
  IF OLD.estado IN ('VALIDADO', 'CORREGIDO') THEN
    IF OLD.estado = 'VALIDADO' AND NEW.estado = 'CORREGIDO'
       AND (to_jsonb(NEW) - 'estado' - 'reemplazado_por') = (to_jsonb(OLD) - 'estado' - 'reemplazado_por') THEN
      RETURN NEW;
    END IF;
    IF OLD.estado = 'CORREGIDO' AND NEW.estado = 'CORREGIDO' AND OLD.reemplazado_por IS NULL
       AND (to_jsonb(NEW) - 'reemplazado_por') = (to_jsonb(OLD) - 'reemplazado_por') THEN
      RETURN NEW;
    END IF;
    RAISE EXCEPTION 'El registro % de % esta % y es inmutable (use una correccion enlazada)', OLD.id, TG_TABLE_NAME, OLD.estado USING ERRCODE = 'P0001';
  END IF;
  RETURN NEW;
END
$fn$;

-- ---------------------------------------------------------------- 1) ARCHIVOS (adjunto)
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS descripcion           VARCHAR(500);
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS almacenamiento_nombre VARCHAR(80);
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS extension             VARCHAR(10);
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS orden_item_id         BIGINT REFERENCES orden_examen_item (id);
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS miniatura_ruta        VARCHAR(500);
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS ancho_px              INT;
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS alto_px               INT;
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS sede_id               BIGINT REFERENCES sede (id);
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS activo                BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS eliminado_en          TIMESTAMPTZ;
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS eliminado_por         BIGINT REFERENCES usuario (id);
ALTER TABLE adjunto ADD COLUMN IF NOT EXISTS motivo_eliminacion    VARCHAR(255);
CREATE INDEX IF NOT EXISTS ix_adjunto_orden    ON adjunto (orden_examen_id) WHERE orden_examen_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_adjunto_cirugia  ON adjunto (cirugia_id) WHERE cirugia_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_adjunto_consulta ON adjunto (consulta_id) WHERE consulta_id IS NOT NULL;
-- NOT VALID: no revalida filas historicas; toda fila nueva debe cumplirlas.
ALTER TABLE adjunto ADD CONSTRAINT ck_adjunto_tipo CHECK (tipo IS NULL OR tipo IN
  ('IMAGEN','RADIOGRAFIA','ECOGRAFIA','DICOM','PDF_LABORATORIO','INFORME','CONSENTIMIENTO','OTRO')) NOT VALID;
ALTER TABLE adjunto ADD CONSTRAINT ck_adjunto_baja CHECK (activo OR (eliminado_en IS NOT NULL AND motivo_eliminacion IS NOT NULL)) NOT VALID;
COMMENT ON COLUMN adjunto.ruta IS 'Ruta RELATIVA al directorio vet.archivos.ruta (nunca absoluta, sin "..").';
COMMENT ON COLUMN adjunto.almacenamiento_nombre IS 'Nombre aleatorio (UUID.ext) con que se guarda en disco; el nombre original solo se muestra.';
CREATE OR REPLACE TRIGGER trg_adjunto_no_delete BEFORE DELETE ON adjunto FOR EACH ROW EXECUTE FUNCTION fn_no_delete();

-- ---------------------------------------------------------------- 2) LABORATORIO: correcciones a columnas de V20
-- V20 declaro columnas demasiado cortas para sus propios valores permitidos: 'CUALITATIVO' (11) en VARCHAR(10) y
-- 'MUESTRA_TOMADA' (14) en VARCHAR(12). Se amplian (V20 no se edita).
ALTER TABLE examen_parametro ALTER COLUMN tipo_valor TYPE VARCHAR(12);
ALTER TABLE orden_examen     ALTER COLUMN estado     TYPE VARCHAR(16);

-- ---------------------------------------------------------------- 2) LABORATORIO: catalogo
ALTER TABLE examen_catalogo ADD COLUMN IF NOT EXISTS categoria     VARCHAR(30);
ALTER TABLE examen_catalogo ADD COLUMN IF NOT EXISTS descripcion   VARCHAR(500);
ALTER TABLE examen_catalogo ADD COLUMN IF NOT EXISTS codigo_loinc  VARCHAR(20);
ALTER TABLE examen_catalogo ADD COLUMN IF NOT EXISTS loinc_estado  VARCHAR(12) NOT NULL DEFAULT 'POR_VALIDAR';
ALTER TABLE examen_catalogo ADD CONSTRAINT ck_examen_loinc_estado CHECK (loinc_estado IN ('POR_VALIDAR','VALIDADO'));

ALTER TABLE examen_parametro ADD COLUMN IF NOT EXISTS codigo_loinc        VARCHAR(20);
ALTER TABLE examen_parametro ADD COLUMN IF NOT EXISTS unidad_ucum         VARCHAR(30);
ALTER TABLE examen_parametro ADD COLUMN IF NOT EXISTS codificacion_estado VARCHAR(12) NOT NULL DEFAULT 'POR_VALIDAR';
ALTER TABLE examen_parametro ADD COLUMN IF NOT EXISTS valor_normal_texto  VARCHAR(100);
ALTER TABLE examen_parametro ADD COLUMN IF NOT EXISTS opciones            VARCHAR(255);
ALTER TABLE examen_parametro ADD COLUMN IF NOT EXISTS decimales           SMALLINT;
ALTER TABLE examen_parametro ADD COLUMN IF NOT EXISTS activo              BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE examen_parametro ADD CONSTRAINT ck_parametro_codif CHECK (codificacion_estado IN ('POR_VALIDAR','VALIDADO'));
COMMENT ON COLUMN examen_parametro.codigo_loinc IS 'LOINC (medicina humana) si se conoce; NULL = sin codigo. Todo codigo queda POR_VALIDAR hasta revision del laboratorio.';
COMMENT ON COLUMN examen_parametro.unidad_ucum IS 'Unidad en notacion UCUM (p. ej. 10*3/uL).';
COMMENT ON COLUMN examen_parametro.valor_normal_texto IS 'Resultado cualitativo esperado (p. ej. NEGATIVO); cualquier otro valor produce bandera A.';
CREATE INDEX IF NOT EXISTS ix_parametro_loinc ON examen_parametro (codigo_loinc) WHERE codigo_loinc IS NOT NULL;

-- ---------------------------------------------------------------- 2) LABORATORIO: orden / item
ALTER TABLE orden_examen ADD COLUMN IF NOT EXISTS muestra_tomada_por    BIGINT REFERENCES usuario (id);
ALTER TABLE orden_examen ADD COLUMN IF NOT EXISTS muestra_observaciones VARCHAR(255);
ALTER TABLE orden_examen ADD COLUMN IF NOT EXISTS cancelada_en          TIMESTAMPTZ;
ALTER TABLE orden_examen ADD COLUMN IF NOT EXISTS cancelada_por         BIGINT REFERENCES usuario (id);
ALTER TABLE orden_examen ADD COLUMN IF NOT EXISTS motivo_cancelacion    VARCHAR(255);
CREATE INDEX IF NOT EXISTS ix_oe_consulta ON orden_examen (consulta_id) WHERE consulta_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_oe_bandeja  ON orden_examen (estado, prioridad, solicitada_en);

ALTER TABLE orden_examen_item ADD COLUMN IF NOT EXISTS capturado_por       BIGINT REFERENCES usuario (id);
ALTER TABLE orden_examen_item ADD COLUMN IF NOT EXISTS capturado_en        TIMESTAMPTZ;
ALTER TABLE orden_examen_item ADD COLUMN IF NOT EXISTS motivo_cancelacion  VARCHAR(255);
ALTER TABLE orden_examen_item ADD COLUMN IF NOT EXISTS version_actual      INT NOT NULL DEFAULT 0;
CREATE INDEX IF NOT EXISTS ix_oei_orden ON orden_examen_item (orden_id);

-- Validaciones y correcciones: una fila por version, append-only (la interpretacion vigente es la mas reciente).
CREATE TABLE IF NOT EXISTS orden_item_validacion (
    id             BIGSERIAL PRIMARY KEY,
    orden_item_id  BIGINT NOT NULL REFERENCES orden_examen_item (id),
    version        INT NOT NULL,
    tipo           VARCHAR(12) NOT NULL,
    interpretacion TEXT,
    motivo         VARCHAR(500),
    validado_por   BIGINT NOT NULL REFERENCES usuario (id),
    validado_en    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_oiv_version UNIQUE (orden_item_id, version),
    CONSTRAINT ck_oiv_tipo CHECK (tipo IN ('VALIDACION','CORRECCION')),
    CONSTRAINT ck_oiv_motivo CHECK (tipo = 'VALIDACION' OR motivo IS NOT NULL)
);
CREATE OR REPLACE TRIGGER trg_oiv_append_only BEFORE UPDATE OR DELETE ON orden_item_validacion
  FOR EACH ROW EXECUTE FUNCTION fn_append_only();

-- Bitacora de la orden (historial de la bandeja de laboratorio): append-only.
CREATE TABLE IF NOT EXISTS orden_examen_evento (
    id              BIGSERIAL PRIMARY KEY,
    orden_id        BIGINT NOT NULL REFERENCES orden_examen (id),
    item_id         BIGINT REFERENCES orden_examen_item (id),
    tipo            VARCHAR(20) NOT NULL,
    estado_anterior VARCHAR(16),
    estado_nuevo    VARCHAR(16),
    detalle         VARCHAR(500),
    usuario_id      BIGINT,
    origen          VARCHAR(12),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS ix_oee_orden ON orden_examen_evento (orden_id, creado_en);
CREATE OR REPLACE TRIGGER trg_oee_append_only BEFORE UPDATE OR DELETE ON orden_examen_evento
  FOR EACH ROW EXECUTE FUNCTION fn_append_only();

-- Importaciones (HL7/CSV/JSON): conserva el mensaje original (trazabilidad) y evita reprocesar el mismo.
CREATE TABLE IF NOT EXISTS lab_importacion (
    id             BIGSERIAL PRIMARY KEY,
    formato        VARCHAR(5) NOT NULL,
    origen         VARCHAR(12) NOT NULL,
    nombre_archivo VARCHAR(255),
    hash_sha256    VARCHAR(64) NOT NULL,
    contenido      TEXT NOT NULL,
    resumen        JSONB,
    total          INT NOT NULL DEFAULT 0,
    importados     INT NOT NULL DEFAULT 0,
    usuario_id     BIGINT REFERENCES usuario (id),
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_lab_importacion_hash UNIQUE (hash_sha256),
    CONSTRAINT ck_lab_imp_formato CHECK (formato IN ('HL7','CSV','JSON')),
    CONSTRAINT ck_lab_imp_origen  CHECK (origen IN ('ARCHIVO','INTEGRACION'))
);
CREATE OR REPLACE TRIGGER trg_lab_imp_append_only BEFORE UPDATE OR DELETE ON lab_importacion
  FOR EACH ROW EXECUTE FUNCTION fn_append_only();

-- ---------------------------------------------------------------- 2) LABORATORIO: resultados
-- Se elimina la unicidad (orden_item, parametro): ahora puede haber una fila vigente y filas CORREGIDO historicas.
ALTER TABLE resultado_examen DROP CONSTRAINT IF EXISTS uq_resultado;
ALTER TABLE resultado_examen ADD COLUMN IF NOT EXISTS estado            VARCHAR(10) NOT NULL DEFAULT 'BORRADOR';
ALTER TABLE resultado_examen ADD COLUMN IF NOT EXISTS critico           BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE resultado_examen ADD COLUMN IF NOT EXISTS ref_critico_min   NUMERIC(12,3);
ALTER TABLE resultado_examen ADD COLUMN IF NOT EXISTS ref_critico_max   NUMERIC(12,3);
ALTER TABLE resultado_examen ADD COLUMN IF NOT EXISTS rango_id          BIGINT;
ALTER TABLE resultado_examen ADD COLUMN IF NOT EXISTS observacion       VARCHAR(500);
ALTER TABLE resultado_examen ADD COLUMN IF NOT EXISTS validacion_id     BIGINT REFERENCES orden_item_validacion (id);
ALTER TABLE resultado_examen ADD COLUMN IF NOT EXISTS reemplaza_a       BIGINT REFERENCES resultado_examen (id);
ALTER TABLE resultado_examen ADD COLUMN IF NOT EXISTS reemplazado_por   BIGINT REFERENCES resultado_examen (id);
ALTER TABLE resultado_examen ADD COLUMN IF NOT EXISTS motivo_correccion VARCHAR(500);
ALTER TABLE resultado_examen ADD COLUMN IF NOT EXISTS importacion_id    BIGINT REFERENCES lab_importacion (id);
ALTER TABLE resultado_examen ADD CONSTRAINT ck_resultado_estado CHECK (estado IN ('BORRADOR','VALIDADO','CORREGIDO'));
CREATE UNIQUE INDEX IF NOT EXISTS uq_resultado_vigente ON resultado_examen (orden_item_id, parametro_id) WHERE estado <> 'CORREGIDO';
CREATE INDEX IF NOT EXISTS ix_resultado_parametro ON resultado_examen (parametro_id, registrado_en);
COMMENT ON COLUMN resultado_examen.estado IS 'BORRADOR (capturado, editable) -> VALIDADO (inmutable) -> CORREGIDO (reemplazado por una fila enlazada via reemplaza_a/reemplazado_por).';
CREATE OR REPLACE TRIGGER trg_resultado_inmutable BEFORE UPDATE OR DELETE ON resultado_examen
  FOR EACH ROW EXECUTE FUNCTION fn_validado_inmutable();

-- ---------------------------------------------------------------- 3) INFORMES de imagen y patologia
CREATE TABLE IF NOT EXISTS informe_imagen (
    id                    BIGSERIAL PRIMARY KEY,
    orden_item_id         BIGINT NOT NULL REFERENCES orden_examen_item (id),
    version               INT NOT NULL DEFAULT 1,
    estado                VARCHAR(10) NOT NULL DEFAULT 'BORRADOR',
    region                VARCHAR(150) NOT NULL,
    tecnica               TEXT,
    hallazgos             TEXT,
    impresion_diagnostica TEXT,
    recomendaciones       TEXT,
    equipo                VARCHAR(150),
    fecha_estudio         DATE,
    informado_por         BIGINT REFERENCES usuario (id),
    validacion_id         BIGINT REFERENCES orden_item_validacion (id),
    reemplaza_a           BIGINT REFERENCES informe_imagen (id),
    reemplazado_por       BIGINT REFERENCES informe_imagen (id),
    motivo_correccion     VARCHAR(500),
    creado_por            BIGINT REFERENCES usuario (id),
    creado_en             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_informe_imagen_estado CHECK (estado IN ('BORRADOR','VALIDADO','CORREGIDO'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_informe_imagen_vigente ON informe_imagen (orden_item_id) WHERE estado <> 'CORREGIDO';
CREATE OR REPLACE TRIGGER trg_informe_imagen_inmutable BEFORE UPDATE OR DELETE ON informe_imagen
  FOR EACH ROW EXECUTE FUNCTION fn_validado_inmutable();

CREATE TABLE IF NOT EXISTS informe_patologia (
    id                        BIGSERIAL PRIMARY KEY,
    orden_item_id             BIGINT NOT NULL REFERENCES orden_examen_item (id),
    version                   INT NOT NULL DEFAULT 1,
    estado                    VARCHAR(10) NOT NULL DEFAULT 'BORRADOR',
    tipo_muestra              VARCHAR(100) NOT NULL,
    sitio_anatomico           VARCHAR(150),
    fecha_toma                DATE,
    descripcion_macroscopica  TEXT,
    descripcion_microscopica  TEXT,
    diagnostico_morfologico   TEXT,
    margenes                  TEXT,
    comentarios               TEXT,
    laboratorio_externo       VARCHAR(150),
    numero_caso_externo       VARCHAR(60),
    patologo                  VARCHAR(150),
    fecha_informe             DATE,
    informado_por             BIGINT REFERENCES usuario (id),
    validacion_id             BIGINT REFERENCES orden_item_validacion (id),
    reemplaza_a               BIGINT REFERENCES informe_patologia (id),
    reemplazado_por           BIGINT REFERENCES informe_patologia (id),
    motivo_correccion         VARCHAR(500),
    creado_por                BIGINT REFERENCES usuario (id),
    creado_en                 TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en            TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_informe_patologia_estado CHECK (estado IN ('BORRADOR','VALIDADO','CORREGIDO'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_informe_patologia_vigente ON informe_patologia (orden_item_id) WHERE estado <> 'CORREGIDO';
CREATE OR REPLACE TRIGGER trg_informe_patologia_inmutable BEFORE UPDATE OR DELETE ON informe_patologia
  FOR EACH ROW EXECUTE FUNCTION fn_validado_inmutable();

-- ---------------------------------------------------------------- 4) PROCEDIMIENTOS Y CIRUGIA (sobre cirugia)
ALTER TABLE cirugia ADD COLUMN IF NOT EXISTS tipo                       VARCHAR(15) NOT NULL DEFAULT 'CIRUGIA';
ALTER TABLE cirugia ADD COLUMN IF NOT EXISTS anestesia_tipo             VARCHAR(12);
ALTER TABLE cirugia ADD COLUMN IF NOT EXISTS diagnostico_preoperatorio  TEXT;
ALTER TABLE cirugia ADD COLUMN IF NOT EXISTS diagnostico_postoperatorio TEXT;
ALTER TABLE cirugia ADD COLUMN IF NOT EXISTS resultado                  TEXT;
ALTER TABLE cirugia ADD COLUMN IF NOT EXISTS controles                  TEXT;
ALTER TABLE cirugia ADD COLUMN IF NOT EXISTS proximo_control            DATE;
ALTER TABLE cirugia ADD COLUMN IF NOT EXISTS motivo_cancelacion         VARCHAR(255);
ALTER TABLE cirugia ADD COLUMN IF NOT EXISTS creado_por                 BIGINT REFERENCES usuario (id);
ALTER TABLE cirugia ADD COLUMN IF NOT EXISTS actualizado_en             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE cirugia ADD CONSTRAINT ck_cirugia_tipo CHECK (tipo IN ('PROCEDIMIENTO','CIRUGIA'));
ALTER TABLE cirugia ADD CONSTRAINT ck_cirugia_anestesia CHECK (anestesia_tipo IS NULL OR anestesia_tipo IN
  ('NINGUNA','LOCAL','SEDACION','GENERAL','EPIDURAL','OTRA'));
CREATE INDEX IF NOT EXISTS ix_cirugia_consulta ON cirugia (consulta_id) WHERE consulta_id IS NOT NULL;
COMMENT ON COLUMN cirugia.tipo IS 'PROCEDIMIENTO (ambulatorio) o CIRUGIA; ambos comparten estructura, consentimiento y reporte.';

ALTER TABLE cirugia_monitoreo_anestesico ADD COLUMN IF NOT EXISTS registrado_por BIGINT REFERENCES usuario (id);

CREATE TABLE IF NOT EXISTS cirugia_insumo (
    id          BIGSERIAL PRIMARY KEY,
    cirugia_id  BIGINT NOT NULL REFERENCES cirugia (id) ON DELETE CASCADE,
    item_id     BIGINT REFERENCES item (id),
    descripcion VARCHAR(255) NOT NULL,
    cantidad    NUMERIC(10,2) NOT NULL DEFAULT 1,
    lote        VARCHAR(60),
    notas       VARCHAR(255),
    CONSTRAINT ck_cirugia_insumo_cantidad CHECK (cantidad > 0)
);
CREATE INDEX IF NOT EXISTS ix_cirugia_insumo ON cirugia_insumo (cirugia_id);

-- ---------------------------------------------------------------- 5) auditoria por trigger
CREATE OR REPLACE TRIGGER trg_aud_adjunto          AFTER INSERT OR UPDATE OR DELETE ON adjunto          FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_examen_catalogo  AFTER INSERT OR UPDATE OR DELETE ON examen_catalogo  FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_examen_parametro AFTER INSERT OR UPDATE OR DELETE ON examen_parametro FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_rango_referencia AFTER INSERT OR UPDATE OR DELETE ON rango_referencia FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_orden_examen     AFTER INSERT OR UPDATE OR DELETE ON orden_examen     FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_orden_examen_item AFTER INSERT OR UPDATE OR DELETE ON orden_examen_item FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_resultado_examen AFTER INSERT OR UPDATE OR DELETE ON resultado_examen FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_orden_item_validacion AFTER INSERT ON orden_item_validacion FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_informe_imagen   AFTER INSERT OR UPDATE OR DELETE ON informe_imagen   FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_informe_patologia AFTER INSERT OR UPDATE OR DELETE ON informe_patologia FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_cirugia          AFTER INSERT OR UPDATE OR DELETE ON cirugia          FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_cirugia_monitoreo AFTER INSERT OR UPDATE OR DELETE ON cirugia_monitoreo_anestesico FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_cirugia_insumo   AFTER INSERT OR UPDATE OR DELETE ON cirugia_insumo   FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
CREATE OR REPLACE TRIGGER trg_aud_consentimiento   AFTER INSERT OR UPDATE OR DELETE ON consentimiento   FOR EACH ROW EXECUTE FUNCTION fn_auditar_cambio();
