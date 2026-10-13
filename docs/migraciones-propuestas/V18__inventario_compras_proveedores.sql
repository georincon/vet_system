-- ============================================================================
-- V18 (PROPUESTA) - Inventario: proveedores, compras/recepcion, stock minimo por bodega,
-- ficha farmacologica en item, trazabilidad en kardex y libro de controlados.
-- ============================================================================
CREATE TABLE IF NOT EXISTS proveedor (
    id             BIGSERIAL PRIMARY KEY,
    tipo_documento VARCHAR(10) NOT NULL DEFAULT 'NIT',
    numero_documento VARCHAR(30) NOT NULL,
    razon_social   VARCHAR(255) NOT NULL,
    contacto       VARCHAR(150),
    telefono       VARCHAR(30),
    email          VARCHAR(255),
    direccion      VARCHAR(255),
    ciudad         VARCHAR(120),
    plazo_pago_dias INT NOT NULL DEFAULT 0,
    notas          TEXT,
    activo         BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_proveedor_doc UNIQUE (tipo_documento, numero_documento),
    CONSTRAINT ck_proveedor_tipo_doc CHECK (tipo_documento IN ('NIT','CC','CE','PASAPORTE','OTRO')),
    CONSTRAINT ck_proveedor_plazo CHECK (plazo_pago_dias >= 0)
);
CREATE INDEX IF NOT EXISTS ix_proveedor_razon ON proveedor USING gin (razon_social gin_trgm_ops);

-- Ficha de producto
ALTER TABLE item ADD COLUMN IF NOT EXISTS unidad_medida        VARCHAR(15);
ALTER TABLE item ADD COLUMN IF NOT EXISTS presentacion         VARCHAR(100);
ALTER TABLE item ADD COLUMN IF NOT EXISTS marca                VARCHAR(100);
ALTER TABLE item ADD COLUMN IF NOT EXISTS principio_activo     VARCHAR(150);
ALTER TABLE item ADD COLUMN IF NOT EXISTS concentracion        VARCHAR(60);
ALTER TABLE item ADD COLUMN IF NOT EXISTS via_administracion   VARCHAR(30);
ALTER TABLE item ADD COLUMN IF NOT EXISTS registro_ica         VARCHAR(40);   -- registro sanitario (ICA)
ALTER TABLE item ADD COLUMN IF NOT EXISTS es_controlado        BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE item ADD COLUMN IF NOT EXISTS requiere_formula     BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE item ADD COLUMN IF NOT EXISTS codigo_barras        VARCHAR(50);
ALTER TABLE item ADD COLUMN IF NOT EXISTS tiene_vencimiento    BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE item ADD COLUMN IF NOT EXISTS proveedor_preferido_id BIGINT REFERENCES proveedor (id);
CREATE INDEX IF NOT EXISTS ix_item_barras ON item (codigo_barras) WHERE codigo_barras IS NOT NULL;
CREATE INDEX IF NOT EXISTS ix_item_nombre_trgm ON item USING gin (nombre gin_trgm_ops);

CREATE TABLE IF NOT EXISTS item_proveedor (
    item_id      BIGINT NOT NULL REFERENCES item (id),
    proveedor_id BIGINT NOT NULL REFERENCES proveedor (id),
    codigo_proveedor VARCHAR(60),
    ultimo_costo NUMERIC(12,2),
    ultima_compra DATE,
    PRIMARY KEY (item_id, proveedor_id)
);

-- Minimos/maximos por bodega (alertas y sugerido de compra)
CREATE TABLE IF NOT EXISTS item_bodega_stock (
    item_id       BIGINT NOT NULL REFERENCES item (id),
    bodega_id     BIGINT NOT NULL REFERENCES bodega (id),
    stock_minimo  NUMERIC(12,3) NOT NULL DEFAULT 0,
    stock_maximo  NUMERIC(12,3),
    punto_reorden NUMERIC(12,3),
    PRIMARY KEY (item_id, bodega_id),
    CONSTRAINT ck_ibs CHECK (stock_minimo >= 0 AND (stock_maximo IS NULL OR stock_maximo >= stock_minimo))
);

-- Orden de compra
CREATE TABLE IF NOT EXISTS orden_compra (
    id            BIGSERIAL PRIMARY KEY,
    codigo        VARCHAR(20) NOT NULL,
    proveedor_id  BIGINT NOT NULL REFERENCES proveedor (id),
    sede_id       BIGINT REFERENCES sede (id),
    bodega_destino_id BIGINT REFERENCES bodega (id),
    fecha         DATE NOT NULL DEFAULT CURRENT_DATE,
    fecha_esperada DATE,
    estado        VARCHAR(12) NOT NULL DEFAULT 'BORRADOR',
    subtotal      NUMERIC(14,2) NOT NULL DEFAULT 0,
    impuestos     NUMERIC(14,2) NOT NULL DEFAULT 0,
    total         NUMERIC(14,2) NOT NULL DEFAULT 0,
    notas         TEXT,
    creado_por    BIGINT REFERENCES usuario (id),
    creado_en     TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_oc_codigo UNIQUE (codigo),
    CONSTRAINT ck_oc_estado CHECK (estado IN ('BORRADOR','ENVIADA','PARCIAL','RECIBIDA','CANCELADA'))
);
CREATE INDEX IF NOT EXISTS ix_oc_proveedor ON orden_compra (proveedor_id, fecha);

CREATE TABLE IF NOT EXISTS orden_compra_item (
    id               BIGSERIAL PRIMARY KEY,
    orden_compra_id  BIGINT NOT NULL REFERENCES orden_compra (id) ON DELETE CASCADE,
    item_id          BIGINT NOT NULL REFERENCES item (id),
    cantidad         NUMERIC(12,3) NOT NULL,
    cantidad_recibida NUMERIC(12,3) NOT NULL DEFAULT 0,
    costo_unitario   NUMERIC(12,2) NOT NULL DEFAULT 0,
    impuesto_pct     NUMERIC(5,2)  NOT NULL DEFAULT 0,
    CONSTRAINT ck_oci CHECK (cantidad > 0 AND cantidad_recibida >= 0 AND costo_unitario >= 0)
);
CREATE INDEX IF NOT EXISTS ix_oci_oc ON orden_compra_item (orden_compra_id);

-- Recepcion (factura del proveedor) -> genera lotes y movimientos ENTRADA
CREATE TABLE IF NOT EXISTS recepcion_compra (
    id              BIGSERIAL PRIMARY KEY,
    orden_compra_id BIGINT REFERENCES orden_compra (id),
    proveedor_id    BIGINT NOT NULL REFERENCES proveedor (id),
    bodega_id       BIGINT NOT NULL REFERENCES bodega (id),
    numero_factura_proveedor VARCHAR(40),
    fecha           DATE NOT NULL DEFAULT CURRENT_DATE,
    total           NUMERIC(14,2) NOT NULL DEFAULT 0,
    fecha_vencimiento_pago DATE,
    estado_pago     VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE',
    recibido_por    BIGINT REFERENCES usuario (id),
    notas           TEXT,
    creado_en       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_recepcion_factura UNIQUE (proveedor_id, numero_factura_proveedor),
    CONSTRAINT ck_recepcion_pago CHECK (estado_pago IN ('PENDIENTE','PARCIAL','PAGADA'))
);

CREATE TABLE IF NOT EXISTS recepcion_compra_item (
    id            BIGSERIAL PRIMARY KEY,
    recepcion_id  BIGINT NOT NULL REFERENCES recepcion_compra (id) ON DELETE CASCADE,
    orden_compra_item_id BIGINT REFERENCES orden_compra_item (id),
    item_id       BIGINT NOT NULL REFERENCES item (id),
    lote_id       BIGINT REFERENCES lote (id),
    cantidad      NUMERIC(12,3) NOT NULL,
    costo_unitario NUMERIC(12,2) NOT NULL DEFAULT 0,
    CONSTRAINT ck_rci CHECK (cantidad > 0 AND costo_unitario >= 0)
);
CREATE INDEX IF NOT EXISTS ix_rci_recepcion ON recepcion_compra_item (recepcion_id);

-- Kardex: trazabilidad a origen
ALTER TABLE movimiento_stock ADD COLUMN IF NOT EXISTS bodega_destino_id  BIGINT REFERENCES bodega (id);
ALTER TABLE movimiento_stock ADD COLUMN IF NOT EXISTS costo_unitario     NUMERIC(12,2);
ALTER TABLE movimiento_stock ADD COLUMN IF NOT EXISTS consulta_id        BIGINT REFERENCES consulta (id);
ALTER TABLE movimiento_stock ADD COLUMN IF NOT EXISTS factura_item_id    BIGINT REFERENCES factura_item (id);
ALTER TABLE movimiento_stock ADD COLUMN IF NOT EXISTS recepcion_item_id  BIGINT REFERENCES recepcion_compra_item (id);
ALTER TABLE movimiento_stock ADD COLUMN IF NOT EXISTS paciente_id        BIGINT REFERENCES paciente (id);
ALTER TABLE movimiento_stock ADD COLUMN IF NOT EXISTS saldo_resultante   NUMERIC(12,3);
CREATE INDEX IF NOT EXISTS ix_ms_consulta ON movimiento_stock (consulta_id);
CREATE INDEX IF NOT EXISTS ix_ms_factura_item ON movimiento_stock (factura_item_id);

ALTER TABLE lote ADD COLUMN IF NOT EXISTS proveedor_id  BIGINT REFERENCES proveedor (id);
ALTER TABLE lote ADD COLUMN IF NOT EXISTS estado        VARCHAR(12) NOT NULL DEFAULT 'DISPONIBLE';
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_lote_estado') THEN
    ALTER TABLE lote ADD CONSTRAINT ck_lote_estado CHECK (estado IN ('DISPONIBLE','CUARENTENA','VENCIDO','AGOTADO','RETIRADO'));
  END IF;
END $$;
-- FEFO: lotes con stock ordenados por vencimiento
CREATE INDEX IF NOT EXISTS ix_lote_fefo ON lote (item_id, bodega_id, fecha_vencimiento) WHERE cantidad_actual > 0;

-- Libro de medicamentos de control especial (trazabilidad por paciente/receta)
CREATE TABLE IF NOT EXISTS registro_controlado (
    id           BIGSERIAL PRIMARY KEY,
    item_id      BIGINT NOT NULL REFERENCES item (id),
    lote_id      BIGINT REFERENCES lote (id),
    movimiento_id BIGINT REFERENCES movimiento_stock (id),
    paciente_id  BIGINT REFERENCES paciente (id),
    receta_id    BIGINT REFERENCES receta (id),
    veterinario_id BIGINT REFERENCES usuario (id),
    tipo         VARCHAR(10) NOT NULL,
    cantidad     NUMERIC(12,3) NOT NULL,
    saldo        NUMERIC(12,3) NOT NULL,
    observaciones VARCHAR(500),
    ocurrido_en  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_rc_tipo CHECK (tipo IN ('ENTRADA','SALIDA','DESECHO','AJUSTE')),
    CONSTRAINT ck_rc_cantidad CHECK (cantidad > 0)
);
CREATE INDEX IF NOT EXISTS ix_rc_item ON registro_controlado (item_id, ocurrido_en);
COMMENT ON TABLE registro_controlado IS 'Libro de controlados (opioides, anestesicos, eutanasicos). Validar requisitos regulatorios vigentes.';
CREATE OR REPLACE TRIGGER trg_rc_append_only BEFORE UPDATE OR DELETE ON registro_controlado
  FOR EACH ROW EXECUTE FUNCTION fn_append_only();

-- Bodega por sede: el nombre ya no necesita ser unico global
-- (se mantiene uq_bodega_nombre de V1; para multi-sede reales relajar en V posterior)

-- ---------------------------------------------------------------- SEMILLAS
INSERT INTO bodega (nombre, tipo, sede_id)
SELECT v.nombre, v.tipo, (SELECT id FROM sede WHERE es_principal)
FROM (VALUES ('Bodega principal','PRINCIPAL'), ('Farmacia','FARMACIA'), ('Quirofano','QUIROFANO')) AS v(nombre, tipo)
ON CONFLICT (nombre) DO NOTHING;

INSERT INTO item (codigo, nombre, tipo, categoria_id, precio_venta, controla_stock, principio_activo, via_administracion, es_controlado, requiere_formula, unidad_medida)
SELECT v.codigo, v.nombre, 'MEDICAMENTO', (SELECT id FROM categoria_item WHERE nombre = 'Medicamentos'),
       v.precio, TRUE, v.pa, v.via, v.ctrl, v.formula, v.um
FROM (VALUES
 ('MED-AMOX',  'Amoxicilina + acido clavulanico (tableta)', 4500,  'amoxicilina/clavulanato', 'ORAL', FALSE, TRUE,  'tableta'),
 ('MED-MELOX', 'Meloxicam inyectable (por ml)',             9000,  'meloxicam',               'SC',   FALSE, TRUE,  'ml'),
 ('MED-MARO',  'Maropitant inyectable (por ml)',            22000, 'maropitant',              'SC',   FALSE, TRUE,  'ml'),
 ('MED-TRAM',  'Tramadol (tableta)',                        3000,  'tramadol',                'ORAL', TRUE,  TRUE,  'tableta'),
 ('MED-KETA',  'Ketamina (por ml)',                         12000, 'ketamina',                'IV/IM',TRUE,  TRUE,  'ml'),
 ('MED-PROP',  'Propofol (por ml)',                         6000,  'propofol',                'IV',   FALSE, TRUE,  'ml'),
 ('MED-SF',    'Solucion salina 0.9% (bolsa 500 ml)',       12000, 'cloruro de sodio',        'IV',   FALSE, FALSE, 'bolsa'),
 ('MED-LACT',  'Lactato de Ringer (bolsa 500 ml)',          12000, 'lactato de ringer',       'IV',   FALSE, FALSE, 'bolsa')
) AS v(codigo, nombre, precio, pa, via, ctrl, formula, um)
ON CONFLICT (codigo) DO NOTHING;
UPDATE item SET es_controlado = TRUE WHERE codigo IN ('MED-TRAM','MED-KETA');
