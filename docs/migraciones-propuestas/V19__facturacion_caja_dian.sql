-- ============================================================================
-- V19 (PROPUESTA) - Facturacion, caja por turno, cargos pendientes, resoluciones DIAN
-- (via proveedor tecnologico), notas credito y anticipos.
-- ============================================================================
CREATE TABLE IF NOT EXISTS resolucion_facturacion (
    id                 BIGSERIAL PRIMARY KEY,
    sede_id            BIGINT REFERENCES sede (id),
    tipo_documento     VARCHAR(15) NOT NULL DEFAULT 'FACTURA',
    prefijo            VARCHAR(10) NOT NULL,
    numero_resolucion  VARCHAR(30) NOT NULL,
    fecha_resolucion   DATE,
    rango_desde        BIGINT NOT NULL,
    rango_hasta        BIGINT NOT NULL,
    consecutivo_actual BIGINT NOT NULL,
    vigente_desde      DATE,
    vigente_hasta      DATE,
    ambiente           VARCHAR(12) NOT NULL DEFAULT 'HABILITACION',
    activo             BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_resolucion UNIQUE (numero_resolucion, prefijo),
    CONSTRAINT ck_res_tipo CHECK (tipo_documento IN ('FACTURA','NOTA_CREDITO','NOTA_DEBITO','POS')),
    CONSTRAINT ck_res_rango CHECK (rango_hasta >= rango_desde AND consecutivo_actual >= rango_desde - 1 AND consecutivo_actual <= rango_hasta),
    CONSTRAINT ck_res_ambiente CHECK (ambiente IN ('HABILITACION','PRODUCCION'))
);
COMMENT ON TABLE resolucion_facturacion IS 'Rangos de numeracion autorizados por la DIAN. La clave tecnica/credenciales NO se guardan aqui (secretos externos).';

ALTER TABLE factura ADD COLUMN IF NOT EXISTS tipo_documento   VARCHAR(15) NOT NULL DEFAULT 'FACTURA';
ALTER TABLE factura ADD COLUMN IF NOT EXISTS resolucion_id    BIGINT REFERENCES resolucion_facturacion (id);
ALTER TABLE factura ADD COLUMN IF NOT EXISTS factura_origen_id BIGINT REFERENCES factura (id);  -- nota credito/debito -> factura
ALTER TABLE factura ADD COLUMN IF NOT EXISTS consulta_id      BIGINT REFERENCES consulta (id);
ALTER TABLE factura ADD COLUMN IF NOT EXISTS presupuesto_id   BIGINT REFERENCES presupuesto (id);
ALTER TABLE factura ADD COLUMN IF NOT EXISTS condicion_pago   VARCHAR(10) NOT NULL DEFAULT 'CONTADO';
ALTER TABLE factura ADD COLUMN IF NOT EXISTS fecha_vencimiento DATE;
ALTER TABLE factura ADD COLUMN IF NOT EXISTS estado_dian      VARCHAR(12) NOT NULL DEFAULT 'NO_APLICA';
ALTER TABLE factura ADD COLUMN IF NOT EXISTS cufe             VARCHAR(100);
ALTER TABLE factura ADD COLUMN IF NOT EXISTS xml_ruta         VARCHAR(500);
ALTER TABLE factura ADD COLUMN IF NOT EXISTS pdf_ruta         VARCHAR(500);
ALTER TABLE factura ADD COLUMN IF NOT EXISTS dian_mensaje     TEXT;
ALTER TABLE factura ADD COLUMN IF NOT EXISTS anulada_en       TIMESTAMPTZ;
ALTER TABLE factura ADD COLUMN IF NOT EXISTS anulada_por      BIGINT REFERENCES usuario (id);
ALTER TABLE factura ADD COLUMN IF NOT EXISTS motivo_anulacion VARCHAR(255);
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_factura_tipo_doc') THEN
    ALTER TABLE factura ADD CONSTRAINT ck_factura_tipo_doc CHECK (tipo_documento IN ('FACTURA','NOTA_CREDITO','NOTA_DEBITO','POS','CUENTA_COBRO'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_factura_condicion') THEN
    ALTER TABLE factura ADD CONSTRAINT ck_factura_condicion CHECK (condicion_pago IN ('CONTADO','CREDITO'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_factura_dian') THEN
    ALTER TABLE factura ADD CONSTRAINT ck_factura_dian CHECK (estado_dian IN ('NO_APLICA','PENDIENTE','ENVIADA','ACEPTADA','RECHAZADA','ERROR'));
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_factura_saldo') THEN
    ALTER TABLE factura ADD CONSTRAINT ck_factura_saldo CHECK (saldo >= 0 AND total >= 0) NOT VALID;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_factura_nc_origen') THEN
    ALTER TABLE factura ADD CONSTRAINT ck_factura_nc_origen
      CHECK (tipo_documento NOT IN ('NOTA_CREDITO','NOTA_DEBITO') OR factura_origen_id IS NOT NULL) NOT VALID;
  END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_factura_cufe ON factura (cufe) WHERE cufe IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_factura_estado_saldo ON factura (estado, saldo) WHERE saldo > 0;
CREATE INDEX IF NOT EXISTS ix_factura_origen ON factura (factura_origen_id);

-- Una factura EMITIDA/PAGADA no se edita: la correccion es nota credito (estado -> ANULADA solo via NC).
-- Se protege el contenido monetario una vez emitida (se permiten cambios de saldo/estado/DIAN).
CREATE OR REPLACE FUNCTION fn_factura_emitida_inmutable() RETURNS trigger LANGUAGE plpgsql AS $fn$
BEGIN
  IF OLD.estado <> 'BORRADOR' AND (
       NEW.subtotal IS DISTINCT FROM OLD.subtotal OR NEW.descuento IS DISTINCT FROM OLD.descuento OR
       NEW.impuestos IS DISTINCT FROM OLD.impuestos OR NEW.total IS DISTINCT FROM OLD.total OR
       NEW.tutor_id IS DISTINCT FROM OLD.tutor_id OR NEW.numero IS DISTINCT FROM OLD.numero) THEN
    RAISE EXCEPTION 'La factura % esta emitida: use nota credito para corregir', OLD.numero USING ERRCODE = 'P0001';
  END IF;
  RETURN NEW;
END
$fn$;
CREATE OR REPLACE TRIGGER trg_factura_emitida_inmutable BEFORE UPDATE ON factura
  FOR EACH ROW EXECUTE FUNCTION fn_factura_emitida_inmutable();

ALTER TABLE factura_item ADD COLUMN IF NOT EXISTS lote_id          BIGINT REFERENCES lote (id);
ALTER TABLE factura_item ADD COLUMN IF NOT EXISTS veterinario_id   BIGINT REFERENCES usuario (id);   -- comisiones/produccion
ALTER TABLE factura_item ADD COLUMN IF NOT EXISTS paciente_id      BIGINT REFERENCES paciente (id);
ALTER TABLE presupuesto  ADD COLUMN IF NOT EXISTS vigente_hasta    DATE;
ALTER TABLE presupuesto  ADD COLUMN IF NOT EXISTS consulta_id      BIGINT REFERENCES consulta (id);

-- ---------------------------------------------------------------- CAJA POR TURNO
CREATE TABLE IF NOT EXISTS caja (
    id      BIGSERIAL PRIMARY KEY,
    sede_id BIGINT NOT NULL REFERENCES sede (id),
    nombre  VARCHAR(80) NOT NULL,
    activo  BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_caja_sede_nombre UNIQUE (sede_id, nombre)
);

CREATE TABLE IF NOT EXISTS caja_turno (
    id                 BIGSERIAL PRIMARY KEY,
    caja_id            BIGINT NOT NULL REFERENCES caja (id),
    usuario_id         BIGINT NOT NULL REFERENCES usuario (id),
    abierto_en         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cerrado_en         TIMESTAMPTZ,
    monto_apertura     NUMERIC(14,2) NOT NULL DEFAULT 0,
    efectivo_esperado  NUMERIC(14,2),
    efectivo_contado   NUMERIC(14,2),
    diferencia         NUMERIC(14,2),
    estado             VARCHAR(8) NOT NULL DEFAULT 'ABIERTO',
    observaciones      TEXT,
    CONSTRAINT ck_turno_estado CHECK (estado IN ('ABIERTO','CERRADO')),
    CONSTRAINT ck_turno_cierre CHECK (estado = 'ABIERTO' OR cerrado_en IS NOT NULL),
    CONSTRAINT ck_turno_apertura CHECK (monto_apertura >= 0)
);
-- Una sola sesion abierta por caja
CREATE UNIQUE INDEX IF NOT EXISTS uq_turno_abierto_por_caja ON caja_turno (caja_id) WHERE estado = 'ABIERTO';
CREATE INDEX IF NOT EXISTS ix_turno_usuario ON caja_turno (usuario_id, abierto_en);

CREATE TABLE IF NOT EXISTS caja_movimiento (
    id        BIGSERIAL PRIMARY KEY,
    turno_id  BIGINT NOT NULL REFERENCES caja_turno (id),
    tipo      VARCHAR(10) NOT NULL,
    monto     NUMERIC(14,2) NOT NULL,
    concepto  VARCHAR(255) NOT NULL,
    pago_id   BIGINT REFERENCES pago (id),
    usuario_id BIGINT REFERENCES usuario (id),
    ocurrido_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cm_tipo CHECK (tipo IN ('INGRESO','EGRESO','RETIRO','AJUSTE')),
    CONSTRAINT ck_cm_monto CHECK (monto > 0)
);
CREATE INDEX IF NOT EXISTS ix_cm_turno ON caja_movimiento (turno_id);

ALTER TABLE pago ADD COLUMN IF NOT EXISTS turno_id  BIGINT REFERENCES caja_turno (id);
ALTER TABLE pago ADD COLUMN IF NOT EXISTS sede_id   BIGINT REFERENCES sede (id);
ALTER TABLE pago ADD COLUMN IF NOT EXISTS estado    VARCHAR(10) NOT NULL DEFAULT 'APLICADO';
ALTER TABLE pago ADD COLUMN IF NOT EXISTS anticipo_id BIGINT;
ALTER TABLE pago ADD COLUMN IF NOT EXISTS pasarela_referencia VARCHAR(100);
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_pago_estado') THEN
    ALTER TABLE pago ADD CONSTRAINT ck_pago_estado CHECK (estado IN ('PENDIENTE','APLICADO','REVERSADO'));
  END IF;
END $$;
-- Ampliar medios de pago (superconjunto del CHECK de V1)
ALTER TABLE pago DROP CONSTRAINT IF EXISTS ck_pago_metodo;
ALTER TABLE pago ADD CONSTRAINT ck_pago_metodo CHECK (metodo IN
  ('EFECTIVO','TARJETA','TRANSFERENCIA','NEQUI','DAVIPLATA','PSE','DATAFONO','CHEQUE','ANTICIPO','CREDITO','OTRO'));
CREATE INDEX IF NOT EXISTS ix_pago_turno ON pago (turno_id);

-- Anticipos / abonos a favor del tutor (por ejemplo deposito de cirugia u hospitalizacion)
CREATE TABLE IF NOT EXISTS anticipo (
    id          BIGSERIAL PRIMARY KEY,
    tutor_id    BIGINT NOT NULL REFERENCES tutor (id),
    paciente_id BIGINT REFERENCES paciente (id),
    sede_id     BIGINT REFERENCES sede (id),
    turno_id    BIGINT REFERENCES caja_turno (id),
    monto       NUMERIC(14,2) NOT NULL,
    saldo       NUMERIC(14,2) NOT NULL,
    metodo      VARCHAR(20) NOT NULL DEFAULT 'EFECTIVO',
    concepto    VARCHAR(255),
    estado      VARCHAR(10) NOT NULL DEFAULT 'DISPONIBLE',
    creado_por  BIGINT REFERENCES usuario (id),
    creado_en   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_anticipo_monto CHECK (monto > 0 AND saldo >= 0 AND saldo <= monto),
    CONSTRAINT ck_anticipo_estado CHECK (estado IN ('DISPONIBLE','APLICADO','DEVUELTO'))
);
CREATE INDEX IF NOT EXISTS ix_anticipo_tutor ON anticipo (tutor_id, estado);
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_pago_anticipo') THEN
    ALTER TABLE pago ADD CONSTRAINT fk_pago_anticipo FOREIGN KEY (anticipo_id) REFERENCES anticipo (id);
  END IF;
END $$;

-- Cargos generados por actos clinicos (consulta, vacuna, receta, hospitalizacion) pendientes de facturar
CREATE TABLE IF NOT EXISTS cargo_pendiente (
    id              BIGSERIAL PRIMARY KEY,
    sede_id         BIGINT REFERENCES sede (id),
    tutor_id        BIGINT NOT NULL REFERENCES tutor (id),
    paciente_id     BIGINT REFERENCES paciente (id),
    consulta_id     BIGINT REFERENCES consulta (id),
    item_id         BIGINT REFERENCES item (id),
    descripcion     VARCHAR(255) NOT NULL,
    cantidad        NUMERIC(10,2) NOT NULL DEFAULT 1,
    precio_unitario NUMERIC(12,2) NOT NULL DEFAULT 0,
    impuesto_pct    NUMERIC(5,2) NOT NULL DEFAULT 0,
    descuento       NUMERIC(12,2) NOT NULL DEFAULT 0,
    origen_tabla    VARCHAR(40),
    origen_id       BIGINT,
    estado          VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE',
    factura_item_id BIGINT REFERENCES factura_item (id),
    veterinario_id  BIGINT REFERENCES usuario (id),
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cargo_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_cargo_estado CHECK (estado IN ('PENDIENTE','FACTURADO','ANULADO')),
    CONSTRAINT uq_cargo_origen UNIQUE (origen_tabla, origen_id, item_id)
);
CREATE INDEX IF NOT EXISTS ix_cargo_tutor_estado ON cargo_pendiente (tutor_id, estado);
COMMENT ON TABLE cargo_pendiente IS 'Cuenta abierta del paciente: cada acto clinico genera un cargo; facturar los convierte en factura_item.';

-- ---------------------------------------------------------------- SEMILLAS
INSERT INTO caja (sede_id, nombre) SELECT id, 'Caja 1' FROM sede WHERE es_principal ON CONFLICT (sede_id, nombre) DO NOTHING;

-- Tutor "Consumidor final" para ventas de mostrador (NIT generico DIAN 222222222222)
INSERT INTO tutor (tipo_documento, numero_documento, nombres, apellidos)
VALUES ('OTRO', '222222222222', 'Consumidor', 'Final')
ON CONFLICT (tipo_documento, numero_documento) DO NOTHING;
